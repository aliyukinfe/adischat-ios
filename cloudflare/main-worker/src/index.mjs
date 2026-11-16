import { handleChatActionsRequest } from "../../shared/chat-actions.mjs";
import { requireFirebaseUser } from "../../shared/firebase-auth.mjs";
import { handleChannelVerificationRequest } from "../../shared/channel-verification-router.mjs";
import { handleCallsRequest } from "../../shared/calls-router.mjs";
import { handleCoreApiRequest, handleInternalShopPushRequest, handlePublicCoreRequest } from "../../shared/core-api.mjs";
import { authenticateDesktopSession, handleDesktopQrAuthRequest } from "../../shared/desktop-qr-auth.mjs";
import { handleMessageReceiptsRequest } from "../../shared/message-receipts-router.mjs";
import { health, notFound, preflight, withCors } from "../../shared/http.mjs";
import { handleStorageRequest } from "../../shared/storage-router.mjs";
import { handleShopIdentityRequest } from "../../shared/shop-identity-assertion.mjs";

async function route(request, env, ctx) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") return preflight(env);
  if (url.pathname === "/" || url.pathname === "/api/health") return health("adischat-api");

  const internalShopPushResponse = await handleInternalShopPushRequest(request, env);
  if (internalShopPushResponse) return internalShopPushResponse;

  const publicCoreResponse = await handlePublicCoreRequest(request, env);
  if (publicCoreResponse) return publicCoreResponse;

  const qrAuthResponse = await handleDesktopQrAuthRequest(request, env);
  if (qrAuthResponse) return qrAuthResponse;

  // Public read access for R2 media URLs used by avatars and message previews.
  // Uploads and avatar writes remain below the auth gate.
  if (["GET", "HEAD"].includes(request.method) && (url.pathname.startsWith("/api/media/") || url.pathname.startsWith("/media/"))) {
    const publicMediaResponse = await handleStorageRequest(request, env, { uid: "public", role: "public" });
    if (publicMediaResponse) return publicMediaResponse;
  }

  let auth = await requireFirebaseUser(request, env);
  if (auth.error) {
    const desktopUser = await authenticateDesktopSession(request, env);
    const authorization = request.headers.get("authorization") || "";
    const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
    if (!desktopUser && token.startsWith("adis_desktop_")) {
      return Response.json({ error: "Desktop session expired or revoked" }, { status: 401 });
    }
    if (!desktopUser) return auth.error;
    auth = { user: desktopUser, userRecord: desktopUser };
  }

  const routeContext = { ...ctx, uid: auth.user.uid, user: auth.user };

  const coreResponse = await handleCoreApiRequest(request, env, auth.user);
  if (coreResponse) return coreResponse;

  const shopIdentityResponse = await handleShopIdentityRequest(request, env, auth.user);
  if (shopIdentityResponse) return shopIdentityResponse;

  const storageResponse = await handleStorageRequest(request, env, auth.user);
  if (storageResponse) return storageResponse;

  const callsResponse = await handleCallsRequest(request, env, auth.user);
  if (callsResponse) return callsResponse;

  const receiptsResponse = await handleMessageReceiptsRequest(request, env, auth.user);
  if (receiptsResponse) return receiptsResponse;

  const chatResponse = await handleChatActionsRequest(request, env, routeContext);
  if (chatResponse) return chatResponse;

  const verificationResponse = await handleChannelVerificationRequest(request, env, auth.user);
  if (verificationResponse) return verificationResponse;

  return notFound();
}

export default {
  async fetch(request, env, ctx) {
    try {
      return withCors(await route(request, env, ctx), env);
    } catch (error) {
      const message = error?.message === "AUTH_REQUIRED" ? "Authentication required" : "Internal server error";
      const status = error?.message === "AUTH_REQUIRED" ? 401 : 500;
      return withCors(Response.json({ error: message }, { status }), env);
    }
  },
};
