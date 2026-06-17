import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

// One-time purchases live in the `purchases` table and never expire. Only
// lifetime ad-free is a one-time product — it grants ad-free but NOT web sync.
const ONE_TIME_IDS = ["premium_lifetime"];

// Recurring products live in the `subscriptions` table and are only honored
// while the subscription is active/trialing and not past its period end. This
// is what makes a Stripe cancellation revert the user to the free tier.
const SUBSCRIPTION_IDS = [
  "premium_monthly",
  "premium_yearly",
  "websync_monthly",
  "websync_yearly",
];

const WS_IDS = ["websync_monthly", "websync_yearly"];

serve(async (req) => {
  try {
    const url = new URL(req.url);
    const deviceId = url.searchParams.get("device_id");

    if (!deviceId) {
      return new Response(
        JSON.stringify({ error: "Missing device_id" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    let isPremium = false;
    let isWebSyncPremium = false;

    // One-time purchases (lifetime ad-free). These never expire.
    const { data: purchases } = await supabase
      .from("purchases")
      .select("product_id, status")
      .eq("device_id", deviceId)
      .eq("status", "completed")
      .in("product_id", ONE_TIME_IDS);

    if (purchases) {
      for (const p of purchases) {
        if (ONE_TIME_IDS.includes(p.product_id)) {
          isPremium = true; // lifetime grants ad-free only, not web sync
        }
      }
    }

    // Active subscriptions only — a cancelled/expired sub no longer matches
    // (status not active/trialing, or current_period_end in the past), so the
    // user automatically drops back to the free, ad-supported tier.
    const now = new Date().toISOString();
    const { data: subscriptions } = await supabase
      .from("subscriptions")
      .select("product_id, status, current_period_end")
      .eq("device_id", deviceId)
      .in("status", ["active", "trialing"])
      .gte("current_period_end", now);

    if (subscriptions) {
      for (const sub of subscriptions) {
        if (SUBSCRIPTION_IDS.includes(sub.product_id)) {
          isPremium = true;
        }
        if (WS_IDS.includes(sub.product_id)) {
          isWebSyncPremium = true;
        }
      }
    }

    return new Response(
      JSON.stringify({
        isPremium,
        isWebSyncPremium,
        deviceId,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("get-premium-status error:", err);
    return new Response(
      JSON.stringify({ error: String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
