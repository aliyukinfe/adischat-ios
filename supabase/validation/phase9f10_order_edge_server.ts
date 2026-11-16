// Test-only loopback multiplexer for unchanged Edge handlers.
import { handleAdishopFunction } from "../functions/_shared/adishop.ts";

const allowed = new Set([
  "adishop-request-order-cancellation",
  "adishop-seller-accept-order-group",
  "adishop-seller-reject-order-group",
  "adishop-reveal-delivery-code",
]);

Deno.serve({ hostname: "127.0.0.1", port: 8000 }, (request) => {
  const name = new URL(request.url).pathname.slice(1);
  if (!allowed.has(name)) return new Response(null, { status: 404 });
  return handleAdishopFunction(name, request);
});
