import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const ALL_PREMIUM_IDS = [
  "premium_monthly",
  "premium_yearly",
  "premium_lifetime",
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

    // Check for one-time purchases
    const { data: purchases } = await supabase
      .from("purchases")
      .select("product_id, status")
      .eq("device_id", deviceId)
      .eq("status", "completed")
      .in("product_id", ALL_PREMIUM_IDS);

    let isPremium = false;
    let isWebSyncPremium = false;

    if (purchases) {
      for (const p of purchases) {
        if (ALL_PREMIUM_IDS.includes(p.product_id)) {
          isPremium = true;
        }
        if (WS_IDS.includes(p.product_id)) {
          isWebSyncPremium = true;
        }
      }
    }

    // Check for active subscriptions
    const now = new Date().toISOString();
    const { data: subscriptions } = await supabase
      .from("subscriptions")
      .select("product_id, status, current_period_end")
      .eq("device_id", deviceId)
      .in("status", ["active", "trialing"])
      .gte("current_period_end", now);

    if (subscriptions) {
      for (const sub of subscriptions) {
        if (ALL_PREMIUM_IDS.includes(sub.product_id)) {
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
