const paused = () => Response.json(
  { error: "Wallet features are not available" },
  { status: 403, headers: { "Cache-Control": "no-store" } },
);

export function walletContainmentResponse(request) {
  const [, api, family, action] = new URL(request.url).pathname.split("/");
  if (api !== "api") return null;
  if (family === "wallet") {
    return request.method === "GET" && action === "transactions" ? null : paused();
  }
  if (family === "merchant" && action === "pay-code") return paused();
  if (family === "admin" && action === "wallets") return paused();
  if (family === "games" && request.method !== "GET") return paused();
  return null;
}
