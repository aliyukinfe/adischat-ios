import {
  approveChannelVerification,
  getChannelVerification,
  listChannelVerifications,
  rejectChannelVerification,
  requestChannelVerification,
} from "./channel-verification.mjs";

function isAdmin(user) {
  return ["admin", "owner", "moderator"].includes((user?.role || "").toString().toLowerCase());
}

export async function handleChannelVerificationRequest(request, env, user) {
  const url = new URL(request.url);
  const path = url.pathname;

  let match = path.match(/^\/api\/channels\/([^/]+)\/verification$/);
  if (match) {
    const channelId = decodeURIComponent(match[1]);
    if (request.method === "GET") return getChannelVerification(request, env, channelId);
    if (request.method === "POST") return requestChannelVerification(request, env, channelId, user);
  }

  match = path.match(/^\/api\/admin\/channel-verifications$/);
  if (match) {
    if (!isAdmin(user)) return Response.json({ error: "Admin access required" }, { status: 403 });
    if (request.method === "GET") return listChannelVerifications(request, env);
  }

  match = path.match(/^\/api\/admin\/channel-verifications\/([^/]+)\/(approve|reject)$/);
  if (match) {
    if (!isAdmin(user)) return Response.json({ error: "Admin access required" }, { status: 403 });
    const channelId = decodeURIComponent(match[1]);
    if (request.method === "POST" && match[2] === "approve") {
      return approveChannelVerification(request, env, channelId, user);
    }
    if (request.method === "POST" && match[2] === "reject") {
      return rejectChannelVerification(request, env, channelId, user);
    }
  }

  return null;
}

