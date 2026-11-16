import { clearDesktopSession, getAuthToken, logout } from "./auth";
import type { AdminChannelVerification, AdminGameApproval, AdminReport, AdminStats, AdminVerificationRequest, CallRealtimeToken, CallSession, CallSignal, ChannelPost, CommunityRoom, Conversation, ConversationUpdate, DesktopQrSession, DesktopQrStatus, LinkedDevice, Message, PresenceInfo, RawUser, User, UserSummary } from "./types";

/**
 * Normalizes a raw user payload into our `User` shape. The backend uses
 * different field names depending on the auth path (Firebase session vs. desktop
 * QR session — e.g. `userId`/`displayName`/`avatarUrl`/`profilePicture`), and may
 * wrap the user under `user`/`profile`/`data`. Without this, a QR-session profile
 * maps every field to undefined and renders as an empty "AdisChat User".
 */
export function normalizeUser(raw: RawUser | null | undefined): User {
  const source = raw?.user || raw?.profile || raw?.data || raw || {};
  const rawRoles = Array.isArray(source.roles) ? source.roles : typeof source.roles === "string" ? [source.roles] : [];
  const claims = { ...(source.claims || {}), ...(source.customClaims || {}) } as Record<string, unknown>;
  const claimRoles = Array.isArray(claims.roles) ? claims.roles.map(String) : typeof claims.role === "string" ? [claims.role] : [];
  const role = source.role || rawRoles[0] || claimRoles[0] || (source.isAdmin || source.admin || source.is_admin || claims.admin ? "admin" : "");
  const permissionSource = Array.isArray(source.permissions) ? source.permissions : [];
  const permissions = [...permissionSource, ...rawRoles, ...claimRoles, ...Object.keys(claims).filter((key) => Boolean(claims[key]))];
  return {
    uid: source.uid || source.userId || source.id || "",
    name: source.name || source.displayName || source.fullName || source.full_name || "",
    username: source.username || source.handle || "",
    photoUrl:
      source.photoUrl || source.photo_url || source.avatarUrl || source.avatar_url || source.profilePicture || source.profile_picture || "",
    email: source.email || "",
    phone: source.phone || source.phoneNumber || "",
    dob: source.dob || "",
    bio: source.bio || source.about || "",
    about: source.about || source.bio || "",
    language: source.language || "",
    themePreference: source.themePreference || "",
    lastSeen: source.lastSeen || source.last_seen || 0,
    createdAt: source.createdAt || source.created_at || 0,
    joinedAt: source.joinedAt || source.joined_at || 0,
    role,
    roles: [...new Set([role, ...rawRoles, ...claimRoles].filter(Boolean))],
    permissions,
    isAdmin: Boolean(source.isAdmin || source.admin || source.is_admin || claims.admin || role === "admin"),
    disabled: Boolean(source.disabled),
    paused: Boolean(source.paused || source.status === "paused" || source.account_status === "paused"),
    deleted: Boolean(source.deleted || source.status === "deleted" || source.account_status === "deleted"),
    status: source.status || source.account_status || "",
    verified: source.verified ?? source.is_verified ?? source.isVerified ?? false,
    verificationLevel: source.verificationLevel ?? source.verification_level ?? 0,
    lastSeenVisibility: source.lastSeenVisibility || source.last_seen_visibility || "public",
    onlineStatusVisibility: source.onlineStatusVisibility || source.online_status_visibility || "same_as_last_seen",
    profilePhotoVisibility: source.profilePhotoVisibility || source.profile_photo_visibility || "everyone",
    phoneNumberVisibility: source.phoneNumberVisibility || source.phone_number_visibility || "nobody",
    readReceiptsEnabled: Boolean(source.readReceiptsEnabled ?? source.read_receipts_enabled ?? true),
    whoCanMessageMe: source.whoCanMessageMe || source.who_can_message_me || "everyone",
    whoCanCallMe: source.whoCanCallMe || source.who_can_call_me || "everyone",
    whoCanAddMeToGroups: source.whoCanAddMeToGroups || source.who_can_add_me_to_groups || "everyone"
  };
}

export function isAdminUser(user: { role?: string; roles?: string[]; permissions?: string[]; isAdmin?: boolean } | null | undefined): boolean {
  if (!user) return false;
  if (user.isAdmin) return true;
  const roles = [user.role, ...(user.roles || [])].filter(Boolean).map((role) => String(role).toLowerCase());
  if (roles.some((role) => ["admin", "superadmin", "owner", "moderator"].includes(role))) return true;
  return (user.permissions || []).some((permission) => /admin|moderat|owner|superuser/i.test(permission));
}

const MAIN_API = (process.env.NEXT_PUBLIC_ADISCHAT_API_URL || "https://adischat-api.adischat.workers.dev").replace(/\/$/, "");
const COMMUNITY_API = (process.env.NEXT_PUBLIC_ADISCHAT_COMMUNITY_API_URL || "https://adischat-wallet-api.adischat.workers.dev").replace(/\/$/, "");

type ApiScope = "main" | "community";
const inFlight = new Map<string, { controller: AbortController; promise: Promise<unknown> }>();
const requestTimes: number[] = [];

function recordRequest(source: string) {
  const now = Date.now();
  while (requestTimes.length && now - requestTimes[0] >= 60_000) requestTimes.shift();
  requestTimes.push(now);
  const rpm = requestTimes.length;
  if (rpm > 100) {
    console.warn("[desktop-api] excessive request rate", { source, requestsPerMinute: rpm, activeRequests: inFlight.size });
  } else {
    console.debug("[desktop-api]", { source, requestsPerMinute: rpm, activeRequests: inFlight.size });
  }
}

function forceDesktopLogout() {
  clearDesktopSession();
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("adischat:session-revoked"));
  }
  void logout().catch(() => undefined);
}

async function request<T>(path: string, init: RequestInit = {}, scope: ApiScope = "main", dedupe = true): Promise<T> {
  const token = await getAuthToken();
  const base = scope === "community" ? COMMUNITY_API : MAIN_API;
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (!(init.body instanceof FormData) && init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const method = (init.method || "GET").toUpperCase();
  const key = `${scope}:${method}:${path}:${init.body ? String(init.body) : ""}`;
  if (dedupe && method === "GET") {
    const existing = inFlight.get(key);
    if (existing) return existing.promise as Promise<T>;
  }
  const controller = new AbortController();
  const signal = init.signal || controller.signal;
  recordRequest(`${method} ${path}`);
  const promise = (async () => {
  const response = await fetch(`${base}${path}`, { ...init, headers, signal });
  if (!response.ok) {
    const raw = await response.text();
    let message = `Request failed (${response.status})`;
    try {
      const json = JSON.parse(raw) as { error?: string; message?: string };
      message = json.error || json.message || message;
    } catch {
      if (raw.trim()) message = raw.trim();
    }
    if (response.status === 401 || response.status === 403) {
      const sessionProblem = /auth|token|session|revoked|expired|unauthor/i.test(message);
      if (sessionProblem) forceDesktopLogout();
    }
    if (response.status === 404) {
      if (/privacy|settings/i.test(path)) message = "Unable to update privacy settings.";
      else if (/leave|unfollow|groups|channels|communities/i.test(path)) message = "Unable to leave or unfollow this community.";
      else if (/account|users\/me/i.test(path)) message = "Account deletion unavailable.";
    }
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
  })();
  if (dedupe && method === "GET") inFlight.set(key, { controller, promise });
  try {
    return await promise;
  } finally {
    if (inFlight.get(key)?.promise === promise) inFlight.delete(key);
  }
}

export const api = {
  createDesktopQrSession: (device: { desktopDeviceId: string; desktopDeviceName: string; desktopOs: string }) =>
    request<DesktopQrSession>("/auth/desktop/qr/create", { method: "POST", body: JSON.stringify(device) }),
  getDesktopQrStatus: (qrSessionId: string) =>
    request<DesktopQrStatus>(`/auth/desktop/qr/status/${encodeURIComponent(qrSessionId)}`),
  getLinkedDevices: async () =>
    (await request<{ devices: LinkedDevice[] }>("/api/linked-devices")).devices || [],
  logoutLinkedDevice: (id: string) =>
    request<{ ok?: boolean }>(`/api/linked-devices/${encodeURIComponent(id)}/logout`, { method: "POST", body: JSON.stringify({}) }),
  logoutAllDesktopDevices: () =>
    request<{ ok?: boolean }>("/api/linked-devices/logout-all", { method: "POST", body: JSON.stringify({}) }),
  getMe: async () => normalizeUser(await request<RawUser>("/api/users/me")),
  getPrivacy: async () => normalizeUser(await request<RawUser>("/api/settings/privacy")),
  updatePrivacy: async (body: {
    lastSeenVisibility?: "public" | "private";
    onlineStatusVisibility?: "same_as_last_seen" | "public" | "private";
    profilePhotoVisibility?: "everyone" | "contacts" | "nobody";
    phoneNumberVisibility?: "everyone" | "contacts" | "nobody";
    readReceiptsEnabled?: boolean;
    whoCanMessageMe?: "everyone" | "contacts" | "nobody";
    whoCanCallMe?: "everyone" | "contacts" | "nobody";
    whoCanAddMeToGroups?: "everyone" | "contacts" | "nobody";
  }) => normalizeUser(await request<RawUser>("/api/settings/privacy", { method: "PUT", body: JSON.stringify(body) })),
  deleteAccount: () => request<{ deleted?: boolean; deletedAt?: number }>("/api/account/delete", { method: "POST", body: JSON.stringify({}) }),
  updateUser: async (uid: string, body: Partial<User>) =>
    normalizeUser(
      await request<RawUser>(`/api/users/${encodeURIComponent(uid)}`, {
        method: "PUT",
        body: JSON.stringify({
          name: body.name || "",
          username: body.username || "",
          phone: body.phone || "",
          email: body.email || "",
          dob: body.dob || "",
          bio: body.bio || body.about || "",
          about: body.about || body.bio || "",
          language: body.language || "",
          themePreference: body.themePreference || ""
        })
      })
    ),
  uploadAvatar: async (uid: string, file: File) => {
    const jpeg = await file.arrayBuffer();
    return request<{ photoUrl: string }>(`/api/users/${encodeURIComponent(uid)}/avatar`, {
      method: "POST",
      headers: { "Content-Type": "image/jpeg" },
      body: jpeg
    });
  },
  removeAvatar: async (uid: string, user: User) =>
    normalizeUser(
      await request<RawUser>(`/api/users/${encodeURIComponent(uid)}`, {
        method: "PUT",
        body: JSON.stringify({
          name: user.name || "",
          username: user.username || "",
          phone: user.phone || "",
          email: user.email || "",
          dob: user.dob || "",
          photoUrl: "",
          bio: user.bio || user.about || "",
          about: user.about || user.bio || ""
        })
      })
    ),
  getConversations: () => request<Conversation[]>("/api/conversations"),
  getConversationUpdates: () => request<ConversationUpdate[]>("/api/conversations/updates"),
  getCommunities: (type: "group" | "channel", q = "") =>
    request<CommunityRoom[]>(`/api/communities?type=${type}&q=${encodeURIComponent(q)}`, {}, "community"),
  getMessages: (id: string, after = 0) => request<Message[]>(`/api/conversations/${encodeURIComponent(id)}/messages${after > 0 ? `?after=${encodeURIComponent(String(after))}` : ""}`),
  getCommunityMessages: (id: string, after = 0) =>
    request<Message[]>(`/api/communities/${encodeURIComponent(id)}/messages${after > 0 ? `?after=${encodeURIComponent(String(after))}` : ""}`, {}, "community"),
  getChannelPosts: (id: string, after = 0) =>
    request<{ posts?: ChannelPost[] } | ChannelPost[]>(`/api/channels/${encodeURIComponent(id)}/posts${after > 0 ? `?after=${encodeURIComponent(String(after))}` : ""}`, {}, "community"),
  sendMessage: (id: string, text: string, media?: Partial<Message>) =>
    request<Message>(`/api/conversations/${encodeURIComponent(id)}/messages`, {
      method: "POST",
      body: JSON.stringify({ text, type: media?.type || "text", ...media })
    }),
  sendCommunityMessage: (id: string, text: string, media?: Partial<Message>) =>
    request<Message>(
      `/api/communities/${encodeURIComponent(id)}/messages`,
      { method: "POST", body: JSON.stringify({ text, type: media?.type || "text", ...media }) },
      "community"
    ),
  leaveGroup: (id: string) =>
    request<{ ok?: boolean; joined?: boolean }>(`/api/groups/${encodeURIComponent(id)}/leave`, { method: "POST", body: JSON.stringify({}) }, "community"),
  unfollowChannel: (id: string) =>
    request<{ ok?: boolean; joined?: boolean }>(`/api/channels/${encodeURIComponent(id)}/unfollow`, { method: "POST", body: JSON.stringify({}) }, "community"),
  markRead: (threadId: string, community = false) =>
    request<void>(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/read`,
      { method: "PUT" },
      community ? "community" : "main"
    ),
  uploadMedia: async (file: File, onProgress?: (progress: number) => void) => {
    onProgress?.(1);
    const bytes = await file.arrayBuffer();
    onProgress?.(35);
    const type = file.type.startsWith("image/") ? "image" : file.type.startsWith("video/") ? "video" : "file";
    const uploaded = await request<{ url: string }>(`/api/upload?type=${type}`, {
      method: "POST",
      headers: { "Content-Type": file.type || "application/octet-stream" },
      body: bytes
    });
    onProgress?.(100);
    return uploaded;
  },
  react: (threadId: string, messageId: string, emoji: string, community = false) =>
    request(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/reaction`,
      { method: "POST", body: JSON.stringify({ emoji }) },
      community ? "community" : "main"
    ),
  edit: (threadId: string, messageId: string, text: string, community = false) =>
    request(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/edit`,
      { method: "PATCH", body: JSON.stringify({ text }) },
      community ? "community" : "main"
    ),
  delete: (threadId: string, messageId: string, mode: "me" | "everyone", community = false) =>
    request(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/delete`,
      { method: "POST", body: JSON.stringify({ mode }) },
      community ? "community" : "main"
    ),
  pin: (threadId: string, messageId: string, pinned: boolean, community = false) =>
    request(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/pin`,
      { method: "POST", body: JSON.stringify({ pinned }) },
      community ? "community" : "main"
    ),
  star: (threadId: string, messageId: string, starred: boolean, community = false) =>
    request(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/star`,
      { method: "POST", body: JSON.stringify({ starred }) },
      community ? "community" : "main"
    ),
  searchMessages: (threadId: string, q: string, community = false) =>
    request<Message[]>(
      `/api/${community ? "communities" : "conversations"}/${encodeURIComponent(threadId)}/search?q=${encodeURIComponent(q)}`,
      {},
      community ? "community" : "main"
    ),
  getUser: async (uid: string): Promise<UserSummary> => normalizeUser(await request<RawUser>(`/api/users/${encodeURIComponent(uid)}`)),
  searchUsers: async (q: string): Promise<UserSummary[]> =>
    (await request<RawUser[]>(`/api/users/search?q=${encodeURIComponent(q)}`)).map(normalizeUser),
  getPresence: (uid: string) => request<PresenceInfo>(`/api/users/${encodeURIComponent(uid)}/presence`),
  heartbeat: () => request<{ ok?: boolean; lastSeen?: number }>("/api/users/heartbeat", { method: "POST", body: JSON.stringify({}) }),
  startConversation: (targetUid: string) =>
    request<{ conversationId: string }>(`/api/conversations/start/${encodeURIComponent(targetUid)}`, { method: "POST", body: JSON.stringify({}) }),
  blockUser: (conversationId: string, blockedUid: string, reason = "") =>
    request<{ ok?: boolean; blocked?: boolean }>(`/api/conversations/${encodeURIComponent(conversationId)}/block`, {
      method: "POST",
      body: JSON.stringify({ blockedUid, reason })
    }),
  reportUser: (conversationId: string, reportedUid: string, reason = "report", details = "") =>
    request<{ ok?: boolean; reported?: boolean }>(`/api/conversations/${encodeURIComponent(conversationId)}/report`, {
      method: "POST",
      body: JSON.stringify({ reportedUid, reason, details })
    }),
  startCall: async (receiverUid: string, type: "voice" | "video") => {
    const response = await request<{ call: CallSession; setupRequired?: boolean }>("/api/calls/start", {
      method: "POST",
      body: JSON.stringify({ receiverUid, type })
    });
    return response.call;
  },
  acceptCall: async (callId: string) => {
    const response = await request<{ call: CallSession }>("/api/calls/accept", { method: "POST", body: JSON.stringify({ callId }) });
    return response.call;
  },
  rejectCall: async (callId: string) => {
    const response = await request<{ call: CallSession }>("/api/calls/reject", { method: "POST", body: JSON.stringify({ callId }) });
    return response.call;
  },
  ringingAck: async (callId: string) => {
    const response = await request<{ call: CallSession }>("/api/calls/ringing_ack", { method: "POST", body: JSON.stringify({ callId }) });
    return response.call;
  },
  cancelCall: async (callId: string) => {
    const response = await request<{ call: CallSession }>("/api/calls/cancel", { method: "POST", body: JSON.stringify({ callId }) });
    return response.call;
  },
  endCall: async (callId: string, endReason = "") => {
    const response = await request<{ call: CallSession }>("/api/calls/end", { method: "POST", body: JSON.stringify({ callId, endReason }) });
    return response.call;
  },
  connectedCall: async (callId: string) => {
    const response = await request<{ call: CallSession }>("/api/calls/connected", { method: "POST", body: JSON.stringify({ callId }) });
    return response.call;
  },
  getCallToken: (callId: string) =>
    request<{ call: CallSession; realtime?: CallRealtimeToken }>("/api/calls/token", { method: "POST", body: JSON.stringify({ callId }) }),
  getCall: async (callId: string) => {
    const response = await request<{ call: CallSession }>(`/api/calls/${encodeURIComponent(callId)}`);
    return response.call;
  },
  getActiveCall: async () => {
    const response = await request<{ call?: CallSession | null }>("/api/calls/active");
    return response.call || null;
  },
  getPendingCall: async () => {
    const response = await request<{ call?: CallSession | null }>("/api/calls/pending");
    return response.call || null;
  },
  getCallHistory: async (limit = 50) => {
    const response = await request<{ calls?: CallSession[] }>(`/api/calls/history?limit=${limit}`);
    return response.calls || [];
  },
  sendCallSignal: (callId: string, kind: string, payload: string) =>
    request<{ ok?: boolean }>(`/api/calls/${encodeURIComponent(callId)}/signal`, {
      method: "POST",
      body: JSON.stringify({ kind, payload })
    }),
  getCallSignals: (callId: string, after = 0) =>
    request<{ status?: string; signals?: CallSignal[] }>(`/api/calls/${encodeURIComponent(callId)}/signals?after=${after}`),
  getAdminStats: () => request<AdminStats>("/api/admin/stats"),
  getAdminUsers: (q = "", page = 0) =>
    request<User[]>(`/api/admin/users?q=${encodeURIComponent(q)}&page=${page}`).then((rows) => rows.map(normalizeUser)),
  setAdminUserRole: (uid: string, role: "user" | "merchant" | "admin") =>
    request<{ ok?: boolean; role?: string }>(`/api/admin/users/${encodeURIComponent(uid)}/role`, {
      method: "PUT",
      body: JSON.stringify({ role })
    }),
  setAdminUserStatus: (uid: string, status: "active" | "paused" | "deleted") =>
    request<{ ok?: boolean; status?: string }>(`/api/admin/users/${encodeURIComponent(uid)}/status`, {
      method: "PUT",
      body: JSON.stringify({ status })
    }),
  deleteAdminUser: (uid: string) =>
    request<{ ok?: boolean; deleted?: boolean }>(`/api/admin/users/${encodeURIComponent(uid)}`, {
      method: "DELETE"
    }),
  grantAdminBlueBadge: (uid: string) =>
    request<{ approved?: boolean }>(`/api/admin/verifications/${encodeURIComponent(uid)}/approve`, { method: "POST", body: JSON.stringify({}) }),
  removeAdminBlueBadge: (uid: string) =>
    request<{ ok?: boolean }>(`/api/admin/users/${encodeURIComponent(uid)}/verification`, {
      method: "PUT",
      body: JSON.stringify({ verificationLevel: 0, verified: false })
    }),
  getAdminVerifications: (status = "pending") =>
    request<AdminVerificationRequest[]>(`/api/admin/verifications?status=${encodeURIComponent(status)}`),
  approveAdminVerification: (uid: string) =>
    request<{ approved?: boolean }>(`/api/admin/verifications/${encodeURIComponent(uid)}/approve`, { method: "POST", body: JSON.stringify({}) }),
  rejectAdminVerification: (uid: string, reason: string) =>
    request<{ rejected?: boolean }>(`/api/admin/verifications/${encodeURIComponent(uid)}/reject`, { method: "POST", body: JSON.stringify({ reason }) }),
  getAdminChannelVerifications: (status = "pending") =>
    request<AdminChannelVerification[]>(`/api/admin/channel-verifications?status=${encodeURIComponent(status)}`, {}, "community"),
  approveAdminChannelVerification: (channelId: string) =>
    request<AdminChannelVerification>(`/api/admin/channel-verifications/${encodeURIComponent(channelId)}/approve`, { method: "POST", body: JSON.stringify({}) }, "community"),
  rejectAdminChannelVerification: (channelId: string, reason: string) =>
    request<AdminChannelVerification>(
      `/api/admin/channel-verifications/${encodeURIComponent(channelId)}/reject`,
      { method: "POST", body: JSON.stringify({ reason }) },
      "community"
    ),
  getAdminGameApprovals: (status = "pending") =>
    request<AdminGameApproval[]>(`/api/games/admin/approvals?status=${encodeURIComponent(status)}`, {}, "community"),
  approveAdminGameApproval: (channelId: string, note = "") =>
    request<AdminGameApproval>(
      `/api/games/admin/approvals/${encodeURIComponent(channelId)}/approve`,
      { method: "POST", body: JSON.stringify({ note }) },
      "community"
    ),
  rejectAdminGameApproval: (channelId: string, note = "") =>
    request<AdminGameApproval>(
      `/api/games/admin/approvals/${encodeURIComponent(channelId)}/reject`,
      { method: "POST", body: JSON.stringify({ note }) },
      "community"
    ),
  getAdminReports: async () => {
    try {
      return await request<AdminReport[]>("/api/admin/reports");
    } catch {
      return [];
    }
  },
  getAdminAuditLogs: async () => {
    try {
      return await request<AdminReport[]>("/api/admin/audit-logs");
    } catch {
      return [];
    }
  }
};
