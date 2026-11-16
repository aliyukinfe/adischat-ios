import { adminClient, currentSession } from "../_shared/adishop.ts";
import { createModerationHandler, type AuthenticatedActor } from "./moderation.ts";

const STAGING_URL = "https://opkqyvttbyezjbqwkvho.supabase.co";
const client = adminClient();

async function authenticate(request: Request): Promise<AuthenticatedActor> {
  const { profileId } = await currentSession(request);
  const token = request.headers.get("authorization")?.match(/^Bearer ([A-Za-z0-9_.-]+)$/)?.[1];
  if (!token) throw new Error("invalid-session");
  const payload = JSON.parse(new TextDecoder().decode(
    Uint8Array.from(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
      (character) => character.charCodeAt(0))));
  if (payload.sub !== profileId || typeof payload.jti !== "string" || payload.jti.length > 160)
    throw new Error("invalid-session");
  const { data, error } = await client.rpc("adishop_qa_active_read_scope", { p_session_jti: payload.jti });
  if (error || data?.profileId !== profileId) throw new Error("invalid-session");
  return { profileId, jti: payload.jti };
}

async function isAdmin(actor: AuthenticatedActor): Promise<boolean> {
  const { data, error } = await client.from("shop_user_roles")
    .select("shop_roles(code)").eq("profile_id", actor.profileId).is("revoked_at", null);
  if (error) throw new Error("role-lookup-failed");
  return (data || []).some((row) => {
    const role: unknown = row.shop_roles;
    const profileRole = Array.isArray(role) ? role[0] : role;
    const code = profileRole && typeof profileRole === "object" && "code" in profileRole
      ? profileRole.code : null;
    return code === "SHOP_ADMIN" || code === "SHOP_SUPER_ADMIN";
  });
}

const handler = createModerationHandler({
  authenticate, isAdmin,
  rpc: async (name, args) => await client.rpc(name, args),
  now: () => Date.now(),
});

Deno.serve(async (request) => {
  if (Deno.env.get("SUPABASE_URL") !== STAGING_URL ||
      Deno.env.get("ADISHOP_QA_MODERATION_ENABLED") !== "true" ||
      !Deno.env.get("ADISHOP_SHOP_SESSION_SECRET") ||
      !Deno.env.get("ADISHOP_SUPABASE_SECRET_KEY")) {
    return new Response(JSON.stringify({ ok: false, error: "unavailable" }), {
      status: 503, headers: { "content-type": "application/json", "cache-control": "no-store" },
    });
  }
  return handler(request);
});
