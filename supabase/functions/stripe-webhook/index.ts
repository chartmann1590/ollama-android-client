import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const STRIPE_WEBHOOK_SECRET = Deno.env.get("STRIPE_WEBHOOK_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

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
          // Recurring purchase — tracked ONLY in `subscriptions` so that a later
          // cancellation/expiry revokes access. Do NOT write a `purchases` row,
          // which would grant permanent premium and never revert on cancel.
          const subResp = await fetch(
            "https://api.stripe.com/v1/subscriptions/" + session.subscription,
            {
              headers: {
                "Authorization": "Bearer " + (Deno.env.get("STRIPE_SECRET_KEY") ?? ""),
              },
            }
          );
          const subscription = await subResp.json();

          await supabase.from("subscriptions").upsert(
            {
              device_id: deviceId,
              product_id: productId,
              stripe_subscription_id: session.subscription,
              status: subscription.status,
              current_period_start: new Date(
                (subscription.current_period_start || 0) * 1000
              ).toISOString(),
              current_period_end: new Date(
                (subscription.current_period_end || 0) * 1000
              ).toISOString(),
            },
            { onConflict: "stripe_subscription_id" }
          );
        } else {
          // One-time payment (lifetime ad-free) — permanent entitlement.
          await supabase.from("purchases").insert({
            device_id: deviceId,
            product_id: productId,
            status: "completed",
            stripe_session_id: stripeSessionId,
          });
        }
        break;
      }

      case "customer.subscription.updated":
      case "customer.subscription.deleted": {
        const subscription = event.data.object;
        const subId = subscription.id;

        const { data: existingSubs } = await supabase
          .from("subscriptions")
          .select("device_id, product_id")
          .eq("stripe_subscription_id", subId)
          .limit(1);

        if (existingSubs && existingSubs.length > 0) {
          await supabase.from("subscriptions").upsert(
            {
              device_id: existingSubs[0].device_id,
              product_id: existingSubs[0].product_id,
              stripe_subscription_id: subId,
              status: subscription.status,
              current_period_start: new Date(
                (subscription.current_period_start || 0) * 1000
              ).toISOString(),
              current_period_end: new Date(
                (subscription.current_period_end || 0) * 1000
              ).toISOString(),
            },
            { onConflict: "stripe_subscription_id" }
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

async function verifyStripeSignature(
  body: string,
  signature: string
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
      ["verify"]
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
