import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const STRIPE_SECRET_KEY = Deno.env.get("STRIPE_SECRET_KEY") ?? "";

const PRODUCT_PRICES: Record<string, { amount: number; currency: string; name: string; type: "subscription" | "one_time"; interval?: "month" | "year" }> = {
  websync_yearly: { amount: 1499, currency: "usd", name: "Web Sync Yearly", type: "subscription", interval: "year" },
  websync_monthly: { amount: 199, currency: "usd", name: "Web Sync Monthly", type: "subscription", interval: "month" },
  premium_yearly: { amount: 999, currency: "usd", name: "Ad-Free Yearly", type: "subscription", interval: "year" },
  premium_monthly: { amount: 99, currency: "usd", name: "Ad-Free Monthly", type: "subscription", interval: "month" },
  premium_lifetime: { amount: 1999, currency: "usd", name: "Lifetime Ad-Free", type: "one_time" },
};

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const { productId, deviceId, successUrl, cancelUrl } = await req.json();

    if (!productId || !deviceId) {
      return new Response(
        JSON.stringify({ error: "Missing productId or deviceId" }),
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
