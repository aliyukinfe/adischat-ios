const encoder = new TextEncoder();

export async function allowlistHas(env, uid, now = Date.now()) {
  if (typeof uid !== "string" || !/^[A-Za-z0-9:_-]{1,128}$/.test(uid)) return false;
  const timestamp = String(Math.floor(now / 1000));
  const key = await crypto.subtle.importKey("raw", encoder.encode(env.QA_ALLOWLIST_READ_SECRET),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(`${timestamp}\n${uid}`));
  const response = await env.QA_ALLOWLIST_READER.fetch("https://qa-allowlist-reader.internal/contains", {
    method: "POST",
    headers: { "content-type": "application/json", "x-qa-time": timestamp,
      "x-qa-signature": Array.from(new Uint8Array(signature), (byte) => byte.toString(16).padStart(2, "0")).join("") },
    body: JSON.stringify({ uid }),
  });
  if (!response.ok) throw new Error("qa-allowlist-unavailable");
  const result = await response.json();
  if (typeof result?.allowed !== "boolean" || Object.keys(result).length !== 1) {
    throw new Error("qa-allowlist-invalid-response");
  }
  return result.allowed;
}
