import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const WS_PRODUCTS = [
  "websync_monthly",
  "websync_yearly",
];

// Map product IDs to the premium flag they grant.
function isWebSyncProduct(productId: string): boolean {
  return WS_PRODUCTS.includes(productId);
}

serve(async (req) => {
  try {
    const url = new URL(req.url);
    const sessionId = url.searchParams.get("session_id");
    const deviceId = url.searchParams.get("device_id");

    if (!sessionId || !deviceId) {
      return new Response(
        JSON.stringify({ error: "Missing session_id or device_id" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    const { data: purchases, error } = await supabase
      .from("purchases")
      .select("*")
      .eq("stripe_session_id", sessionId)
      .limit(1);

    if (error || !purchases || purchases.length === 0) {
      return new Response(
        JSON.stringify({ verified: false, error: "Purchase not found" }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const purchase = purchases[0];

    if (purchase.device_id !== deviceId) {
      return new Response(
        JSON.stringify({ verified: false, error: "Device mismatch" }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const isWebSync = isWebSyncProduct(purchase.product_id);

    return new Response(
      JSON.stringify({
        verified: purchase.status === "completed",
        productId: purchase.product_id,
        isWebSyncPremium: isWebSync && purchase.status === "completed",
        status: purchase.status,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("verify-purchase error:", err);
    return new Response(
      JSON.stringify({ error: String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
