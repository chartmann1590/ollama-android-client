import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { verifyFirebaseUid, unauthorized } from "../_shared/firebaseAuth.ts";

const STRIPE_SECRET_KEY = Deno.env.get("STRIPE_SECRET_KEY") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const PRODUCT_PRICES: Record<string, { amount: number; currency: string; name: string; type: "subscription" | "one_time"; interval?: "month" | "year" }> = {
  websync_yearly: { amount: 1499, currency: "usd", name: "Web Sync Yearly", type: "subscription", interval: "year" },
  websync_monthly: { amount: 199, currency: "usd", name: "Web Sync Monthly", type: "subscription", interval: "month" },
  premium_yearly: { amount: 999, currency: "usd", name: "Ad-Free Yearly", type: "subscription", interval: "year" },
  premium_monthly: { amount: 99, currency: "usd", name: "Ad-Free Monthly", type: "subscription", interval: "month" },
  premium_lifetime: { amount: 1999, currency: "usd", name: "Lifetime Ad-Free", type: "one_time" },
};

const LIFETIME_PRODUCT_ID = "premium_lifetime";
const AD_FREE_ONLY_PRODUCT_IDS = ["premium_lifetime", "premium_monthly", "premium_yearly"];
const ACTIVE_SUBSCRIPTION_STATUSES = ["active", "trialing"];

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    // The account is taken from the verified Firebase ID token so a checkout
    // session (and the resulting entitlement) is always bound to the caller.
    let deviceId: string;
    try {
      deviceId = await verifyFirebaseUid(req.headers.get("x-firebase-token"));
    } catch (e) {
      return unauthorized(String((e as Error).message ?? e));
    }

    const { productId, successUrl, cancelUrl } = await req.json();

    if (!productId) {
      return new Response(
        JSON.stringify({ error: "Missing productId" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const config = PRODUCT_PRICES[productId];
    if (!config) {
      return new Response(
        JSON.stringify({ error: "Unknown product: " + productId }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
    const blockReason = await purchaseBlockReason(supabase, deviceId, productId, config.type);
    if (blockReason) {
      return new Response(
        JSON.stringify({ error: blockReason }),
        { status: 409, headers: { "Content-Type": "application/json" } }
      );
    }

    const stripeResp = await fetch("https://api.stripe.com/v1/checkout/sessions", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + STRIPE_SECRET_KEY,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        "mode": config.type === "subscription" ? "subscription" : "payment",
        "success_url": (successUrl || "https://example.com/success") + "?session_id={CHECKOUT_SESSION_ID}",
        "cancel_url": cancelUrl || "https://example.com/cancelled",
        "line_items[0][price_data][currency]": config.currency,
        "line_items[0][price_data][product_data][name]": config.name,
        "line_items[0][price_data][unit_amount]": String(config.amount),
        ...(config.type === "subscription" && config.interval
          ? {
              "line_items[0][price_data][recurring][interval]": config.interval,
            }
          : {}),
        "line_items[0][quantity]": "1",
        "metadata[device_id]": deviceId,
        "metadata[product_id]": productId,
        // Also stamp the metadata onto the subscription itself (not just the
        // checkout session). customer.subscription.created/updated events can
        // arrive before this handler finishes inserting a row for the
        // subscription — carrying the metadata here lets the webhook create
        // the row directly from that event instead of depending on ordering.
        ...(config.type === "subscription"
          ? {
              "subscription_data[metadata][device_id]": deviceId,
              "subscription_data[metadata][product_id]": productId,
            }
          : {}),
      }),
    });

    const session = await stripeResp.json();

    if (!stripeResp.ok) {
      console.error("Stripe error:", session);
      return new Response(
        JSON.stringify({ error: session.error?.message || "Failed to create checkout session" }),
        { status: 500, headers: { "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ url: session.url, sessionId: session.id }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("create-checkout-session error:", err);
    return new Response(
      JSON.stringify({ error: String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});

async function purchaseBlockReason(
  supabase: ReturnType<typeof createClient>,
  deviceId: string,
  productId: string,
  productType: "subscription" | "one_time",
): Promise<string | null> {
  const now = new Date().toISOString();

  if (AD_FREE_ONLY_PRODUCT_IDS.includes(productId)) {
    const { data: lifetimePurchases, error: lifetimeError } = await supabase
      .from("purchases")
      .select("id")
      .eq("device_id", deviceId)
      .eq("product_id", LIFETIME_PRODUCT_ID)
      .eq("status", "completed")
      .limit(1);

    if (lifetimeError) throw lifetimeError;
    if (lifetimePurchases && lifetimePurchases.length > 0) {
      return "Lifetime ad-free is already active for this account.";
    }
  }

  const { data: activeSubscriptions, error: subscriptionError } = await supabase
    .from("subscriptions")
    .select("id")
    .eq("device_id", deviceId)
    .in("status", ACTIVE_SUBSCRIPTION_STATUSES)
    .gte("current_period_end", now)
    .limit(1);

  if (subscriptionError) throw subscriptionError;
  if (activeSubscriptions && activeSubscriptions.length > 0) {
    if (productType === "subscription") {
      return "You already have an active subscription. Cancel it before buying another subscription.";
    }
    if (productId === LIFETIME_PRODUCT_ID) {
      return "Cancel your active subscription before buying lifetime ad-free.";
    }
  }

  return null;
}
