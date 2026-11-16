export function withCors(response, env) {
  const allowedOrigin = env.CORS_ORIGIN || "*";
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", allowedOrigin);
  headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Requested-With");
  headers.set("Access-Control-Max-Age", "86400");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

export function preflight(env) {
  return withCors(new Response(null, { status: 204 }), env);
}

export function notFound() {
  return Response.json({ error: "Not found" }, { status: 404 });
}

export function health(name) {
  return Response.json({
    ok: true,
    service: name,
    version: "2026.06.07",
    timestamp: Date.now(),
  });
}

