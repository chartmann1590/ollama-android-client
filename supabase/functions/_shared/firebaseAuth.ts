import { importX509, jwtVerify } from "https://esm.sh/jose@5.2.0";

// Firebase project that issues the ID tokens (matches google-services.json).
const PROJECT_ID = "ollama-android-7525f";
const CERT_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

let cache: { certs: Record<string, string>; expiresAt: number } | null = null;

async function fetchCerts(): Promise<Record<string, string>> {
  const now = Date.now();
  if (cache && cache.expiresAt > now) return cache.certs;
  const resp = await fetch(CERT_URL);
  const certs = (await resp.json()) as Record<string, string>;
  const cc = resp.headers.get("cache-control") || "";
  const m = cc.match(/max-age=(\d+)/);
  const ttlMs = m ? parseInt(m[1], 10) * 1000 : 3_600_000;
  cache = { certs, expiresAt: now + ttlMs };
  return certs;
}

function decodeHeader(token: string): { kid?: string; alg?: string } {
  const part = token.split(".")[0] ?? "";
  const b64 = part.replace(/-/g, "+").replace(/_/g, "/");
  const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
  return JSON.parse(atob(padded));
}

/**
 * Verify a Firebase ID token and return its `uid` (the `sub` claim). Throws if
 * the token is missing, malformed, expired, or not issued for this project.
 *
 * This is what authenticates the *caller* of the billing functions: the public
 * Supabase anon key only gets a request through the gateway — it does not prove
 * who the user is. Deriving the account id from the verified token (rather than
 * trusting a client-supplied id) prevents acting on another user's account.
 */
export async function verifyFirebaseUid(idToken: string | null | undefined): Promise<string> {
  if (!idToken) throw new Error("Missing Firebase ID token");
  const { kid } = decodeHeader(idToken);
  if (!kid) throw new Error("Token missing key id");
  const certs = await fetchCerts();
  const cert = certs[kid];
  if (!cert) throw new Error("Unknown signing key");
  const key = await importX509(cert, "RS256");
  const { payload } = await jwtVerify(idToken, key, {
    audience: PROJECT_ID,
    issuer: `https://securetoken.google.com/${PROJECT_ID}`,
  });
  if (typeof payload.sub !== "string" || !payload.sub) {
    throw new Error("Token missing uid");
  }
  return payload.sub;
}

export function unauthorized(message: string): Response {
  return new Response(JSON.stringify({ error: "Unauthorized: " + message }), {
    status: 401,
    headers: { "Content-Type": "application/json" },
  });
}
