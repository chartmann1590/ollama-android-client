import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { verifyFirebaseUid, unauthorized } from "../_shared/firebaseAuth.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const STRIPE_SECRET_KEY = Deno.env.get("STRIPE_SECRET_KEY") ?? "";

/**
 * Deletes all billing data tied to an account identifier (the Firebase UID).
 * Called during in-app/web account deletion so a removed account retains no
 * entitlements. Cancels any live Stripe subscriptions, then removes the
 * `subscriptions` and `purchases` rows.
 */
serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    // The account to delete is taken from the verified Firebase ID token, NOT
    // from the request body — a caller can only ever delete their own account.
    let accountId: string;
    try {
      accountId = await verifyFirebaseUid(req.headers.get("x-firebase-token"));
    } catch (e) {
      return unauthorized(String((e as Error).message ?? e));
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    // Cancel any live Stripe subscriptions for this account.
    const { data: subs } = await supabase
      .from("subscriptions")
      .select("stripe_subscription_id, status")
      .eq("device_id", accountId);

    if (subs && STRIPE_SECRET_KEY) {
      for (const sub of subs) {
        if (!sub.stripe_subscription_id) continue;
        if (sub.status === "canceled") continue;
        try {
          await fetch(
            "https://api.stripe.com/v1/subscriptions/" + sub.stripe_subscription_id,
            {
              method: "DELETE",
              headers: { "Authorization": "Bearer " + STRIPE_SECRET_KEY },
            }
          );
        } catch (err) {
          console.error("Failed to cancel subscription", sub.stripe_subscription_id, err);
        }
      }
    }

    await supabase.from("subscriptions").delete().eq("device_id", accountId);
    await supabase.from("purchases").delete().eq("device_id", accountId);

    return new Response(
      JSON.stringify({ deleted: true, accountId }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (err) {
    console.error("delete-account error:", err);
    return new Response(
      JSON.stringify({ error: String(err) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
