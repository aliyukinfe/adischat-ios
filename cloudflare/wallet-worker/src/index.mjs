import { handleChatActionsRequest } from "../../shared/chat-actions.mjs";
import { handleCommunitiesRequest } from "../../shared/communities-router.mjs";
import { handleGamePotRequest } from "../../shared/gamepot.mjs";
import { requireFirebaseUser } from "../../shared/firebase-auth.mjs";
import { authenticateDesktopSession } from "../../shared/desktop-qr-auth.mjs";
import { handleChannelVerificationRequest } from "../../shared/channel-verification-router.mjs";
import { handleCallsRequest } from "../../shared/calls-router.mjs";
import { handleChannelPostsRequest } from "../../shared/channel-posts-router.mjs";
import { handleMessageReceiptsRequest } from "../../shared/message-receipts-router.mjs";
import { health, notFound, preflight, withCors } from "../../shared/http.mjs";
import { handleStorageRequest } from "../../shared/storage-router.mjs";
import { handleWalletRequest } from "../../shared/wallet-router.mjs";
import { walletContainmentResponse } from "../../shared/wallet-containment.mjs";

async function route(request, env, ctx) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") return preflight(env);
  if (url.pathname === "/" || url.pathname === "/api/health") return health("adischat-wallet-api");

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

  const walletPause = walletContainmentResponse(request);
  if (walletPause) return walletPause;

  const walletResponse = await handleWalletRequest(request, env, auth.user);
  if (walletResponse) return walletResponse;

  const storageResponse = await handleStorageRequest(request, env, auth.user);
  if (storageResponse) return storageResponse;

  const callsResponse = await handleCallsRequest(request, env, auth.user);
  if (callsResponse) return callsResponse;

  const receiptsResponse = await handleMessageReceiptsRequest(request, env, auth.user);
  if (receiptsResponse) return receiptsResponse;

  const communitiesResponse = await handleCommunitiesRequest(request, env, auth.user);
  if (communitiesResponse) return communitiesResponse;

  const channelPostsResponse = await handleChannelPostsRequest(request, env, auth.user);
  if (channelPostsResponse) return channelPostsResponse;

  const verificationResponse = await handleChannelVerificationRequest(request, env, auth.user);
  if (verificationResponse) return verificationResponse;

  const chatResponse = await handleChatActionsRequest(request, env, routeContext);
  if (chatResponse) return chatResponse;

  if (url.pathname.startsWith("/api/games")) {
    return handleGamePotRequest(request, env, ctx, auth.user.uid, auth.userRecord);
  }

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
