import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const STRIPE_WEBHOOK_SECRET = Deno.env.get("STRIPE_WEBHOOK_SECRET") ?? "";
const STRIPE_SECRET_KEY = Deno.env.get("STRIPE_SECRET_KEY") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const LIFETIME_PRODUCT_ID = "premium_lifetime";
const AD_FREE_ONLY_PRODUCT_IDS = ["premium_lifetime", "premium_monthly", "premium_yearly"];
const ACTIVE_SUBSCRIPTION_STATUSES = ["active", "trialing"];

serve(async (req) => {
  try {
    const signature = req.headers.get("stripe-signature");
    if (!signature) {
      return new Response("Missing stripe-signature header", { status: 400 });
    }

    const body = await req.text();

    const verified = await verifyStripeSignature(body, signature);
    if (!verified) {
      return new Response("Invalid signature", { status: 400 });
    }

    const event = JSON.parse(body);
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    switch (event.type) {
      case "checkout.session.completed": {
        const session = event.data.object;
        const deviceId = session.metadata?.device_id;
        const productId = session.metadata?.product_id;
        const stripeSessionId = session.id;

        if (!deviceId || !productId) {
          console.error("Missing metadata in session", session.id);
          break;
        }

        if (session.mode === "subscription" && session.subscription) {
          if (
            AD_FREE_ONLY_PRODUCT_IDS.includes(productId) &&
            await hasCompletedLifetimePurchase(supabase, deviceId, stripeSessionId)
          ) {
            console.error("Blocking ad-free subscription because lifetime is active", {
              deviceId,
              productId,
              stripeSubscriptionId: session.subscription,
            });
            await cancelStripeSubscription(session.subscription);
            break;
          }

          if (await hasActiveSubscription(supabase, deviceId, session.subscription)) {
            console.error("Blocking duplicate active subscription", {
              deviceId,
              productId,
              stripeSubscriptionId: session.subscription,
            });
            await cancelStripeSubscription(session.subscription);
            break;
          }

          // Recurring purchases live only in `subscriptions`; cancellation or
          // expiry then correctly revokes access.
          const subscription = await fetchSubscriptionWithPeriod(session.subscription);

          await supabase.from("subscriptions").upsert(
            {
              device_id: deviceId,
              product_id: productId,
              stripe_subscription_id: session.subscription,
              status: subscription.status,
              current_period_start: new Date(
                (subscription.current_period_start || 0) * 1000,
              ).toISOString(),
              current_period_end: new Date(
                (subscription.current_period_end || 0) * 1000,
              ).toISOString(),
            },
            { onConflict: "stripe_subscription_id" },
          );
        } else {
          if (productId === LIFETIME_PRODUCT_ID) {
            if (await hasCompletedLifetimePurchase(supabase, deviceId, stripeSessionId)) {
              console.error("Blocking duplicate lifetime entitlement", {
                deviceId,
                stripeSessionId,
              });
              break;
            }

            if (await hasActiveSubscription(supabase, deviceId, null)) {
              console.error("Blocking lifetime entitlement while subscription is active", {
                deviceId,
                stripeSessionId,
              });
              break;
            }
          }

          await supabase.from("purchases").insert({
            device_id: deviceId,
            product_id: productId,
            status: "completed",
            stripe_session_id: stripeSessionId,
          });
        }
        break;
      }

      case "customer.subscription.created":
      case "customer.subscription.updated":
      case "customer.subscription.deleted": {
        // checkout.session.completed fetches the subscription immediately on
        // checkout completion, but Stripe doesn't always have current_period_*
        // populated on the subscription yet at that exact instant, which would
        // otherwise persist as epoch-zero (permanently "expired"). Handling
        // customer.subscription.created here — using the fields embedded in
        // the event itself rather than a fresh fetch — self-heals that row
        // moments later once Stripe has finished computing the period.
        const subscription = event.data.object;
        const subId = subscription.id;

        const { data: existingSubs } = await supabase
          .from("subscriptions")
          .select("device_id, product_id")
          .eq("stripe_subscription_id", subId)
          .limit(1);

        // Fall back to the metadata stamped on the subscription itself
        // (subscription_data[metadata] set at checkout) when no row exists
        // yet — covers customer.subscription.created arriving before
        // checkout.session.completed has finished inserting its row.
        const deviceId = existingSubs?.[0]?.device_id ?? subscription.metadata?.device_id;
        const productId = existingSubs?.[0]?.product_id ?? subscription.metadata?.product_id;

        if (deviceId && productId) {
          await supabase.from("subscriptions").upsert(
            {
              device_id: deviceId,
              product_id: productId,
              stripe_subscription_id: subId,
              status: subscription.status,
              current_period_start: new Date(
                (subscription.current_period_start || 0) * 1000,
              ).toISOString(),
              current_period_end: new Date(
                (subscription.current_period_end || 0) * 1000,
              ).toISOString(),
            },
            { onConflict: "stripe_subscription_id" },
          );
        }
        break;
      }
    }

    return new Response(JSON.stringify({ received: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("stripe-webhook error:", err);
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});

async function hasCompletedLifetimePurchase(
  supabase: ReturnType<typeof createClient>,
  deviceId: string,
  stripeSessionId: string,
): Promise<boolean> {
  const { data, error } = await supabase
    .from("purchases")
    .select("id")
    .eq("device_id", deviceId)
    .eq("product_id", LIFETIME_PRODUCT_ID)
    .eq("status", "completed")
    .neq("stripe_session_id", stripeSessionId)
    .limit(1);

  if (error) throw error;
  return Boolean(data && data.length > 0);
}

async function hasActiveSubscription(
  supabase: ReturnType<typeof createClient>,
  deviceId: string,
  stripeSubscriptionId: string | null,
): Promise<boolean> {
  let query = supabase
    .from("subscriptions")
    .select("id")
    .eq("device_id", deviceId)
    .in("status", ACTIVE_SUBSCRIPTION_STATUSES)
    .gte("current_period_end", new Date().toISOString())
    .limit(1);

  if (stripeSubscriptionId) {
    query = query.neq("stripe_subscription_id", stripeSubscriptionId);
  }

  const { data, error } = await query;
  if (error) throw error;
  return Boolean(data && data.length > 0);
}

/**
 * Fetch a subscription's details right after checkout completes. Stripe
 * doesn't always have current_period_start/end populated on the subscription
 * the instant checkout.session.completed fires — an immediate GET can return
 * them as null, which would otherwise be written as epoch-zero and read as
 * "already expired" forever. Retry briefly until they're populated.
 */
async function fetchSubscriptionWithPeriod(
  stripeSubscriptionId: string,
  attempts = 6,
  delayMs = 3000,
  // deno-lint-ignore no-explicit-any
): Promise<any> {
  let subscription: Record<string, unknown> = {};
  for (let attempt = 0; attempt < attempts; attempt++) {
    const resp = await fetch(
      "https://api.stripe.com/v1/subscriptions/" + stripeSubscriptionId,
      { headers: { "Authorization": "Bearer " + STRIPE_SECRET_KEY } },
    );
    subscription = await resp.json();
    if (subscription.current_period_end) return subscription;
    if (attempt < attempts - 1) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  console.error("current_period_end still missing after retries", {
    stripeSubscriptionId,
  });
  return subscription;
}

async function cancelStripeSubscription(stripeSubscriptionId: string): Promise<void> {
  if (!STRIPE_SECRET_KEY) return;
  const response = await fetch(
    "https://api.stripe.com/v1/subscriptions/" + stripeSubscriptionId,
    {
      method: "DELETE",
      headers: {
        "Authorization": "Bearer " + STRIPE_SECRET_KEY,
      },
    },
  );
  if (!response.ok) {
    console.error("Failed to cancel duplicate Stripe subscription", await response.text());
  }
}

async function verifyStripeSignature(
  body: string,
  signature: string,
): Promise<boolean> {
  if (!STRIPE_WEBHOOK_SECRET) {
    console.warn("STRIPE_WEBHOOK_SECRET not set; skipping verification");
    return true;
  }
  try {
    const algo = { name: "HMAC", hash: "SHA-256" };
    const enc = new TextEncoder();
    const key = await crypto.subtle.importKey(
      "raw",
      enc.encode(STRIPE_WEBHOOK_SECRET),
      algo,
      false,
      ["verify"],
    );
    const parts = signature.split(",");
    const timestampPart = parts.find((p) => p.startsWith("t="));
    const sigPart = parts.find((p) => p.startsWith("v1="));
    if (!timestampPart || !sigPart) return false;
    const timestamp = timestampPart.slice(2);
    const sig = sigPart.slice(3);
    const payload = timestamp + "." + body;
    const sigBytes = hexToBytes(sig);
    const valid = await crypto.subtle.verify(algo, key, sigBytes, enc.encode(payload));
    return valid;
  } catch {
    return false;
  }
}

function hexToBytes(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.slice(i, i + 2), 16);
  }
  return bytes;
}
