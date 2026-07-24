const encoder = new TextEncoder();

function base64Url(bytes) {
  const raw = btoa(String.fromCharCode(...new Uint8Array(bytes)));
  return raw.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function json(body, status = 200) {
  return Response.json(body, { status });
}

function nowSeconds() {
  return Math.floor(Date.now() / 1000);
}

function randomId(prefix = "shop") {
  const bytes = new Uint8Array(18);
  crypto.getRandomValues(bytes);
  return `${prefix}_${base64Url(bytes)}`;
}

async function signJwt(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64Url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = base64Url(encoder.encode(JSON.stringify(payload)));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(signingInput));
  return `${signingInput}.${base64Url(signature)}`;
}

export async function handleShopIdentityRequest(request, env, user) {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/api/shop/session-assertion") return null;

  if (!env.ADISHOP_ASSERTION_SECRET) {
    return json({ error: "Shop identity is temporarily unavailable." }, 503);
  }

  const issuedAt = nowSeconds();
  const expiresAt = issuedAt + 10 * 60;
  const payload = {
    iss: env.ADISHOP_ASSERTION_ISSUER || "adischat-api",
    aud: env.ADISHOP_ASSERTION_AUDIENCE || "adishop-edge",
    sub: user.uid,
    iat: issuedAt,
    nbf: issuedAt - 15,
    exp: expiresAt,
    jti: randomId("assertion"),
    nonce: randomId("nonce"),
    provider: user.desktopSessionId ? "desktop_qr" : "firebase",
    session_id: user.desktopSessionId || "",
    adischat_user_id: user.uid,
    display_name: user.name || "",
    avatar_url: user.photoUrl || "",
    email: user.email || "",
    role: user.role || "user",
  };

  const assertion = await signJwt(payload, env.ADISHOP_ASSERTION_SECRET);
  return json({
    ok: true,
    assertion,
    expiresAt: expiresAt * 1000,
  });
}
