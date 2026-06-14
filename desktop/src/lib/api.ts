import { clearDesktopSession, getAuthToken, logout } from "./auth";
import type { AdminChannelVerification, AdminGameApproval, AdminReport, AdminStats, AdminVerificationRequest, CallRealtimeToken, CallSession, CallSignal, ChannelPost, CommunityRoom, Conversation, DesktopQrSession, DesktopQrStatus, LinkedDevice, Message, PresenceInfo, RawUser, User, UserSummary } from "./types";

/**
 * Normalizes a raw user payload into our `User` shape. The backend uses
 * different field names depending on the auth path (Firebase session vs. desktop
 * QR session — e.g. `userId`/`displayName`/`avatarUrl`/`profilePicture`), and may
 * wrap the user under `user`/`profile`/`data`. Without this, a QR-session profile
 * maps every field to undefined and renders as an empty "AdisChat User".
 */
export function normalizeUser(raw: RawUser | null | undefined): User {
  const source = raw?.user || raw?.profile || raw?.data || raw || {};
  const role = source.role || source.roles?.[0] || "";
  const permissions = source.permissions || (source.roles ? source.roles.slice() : []);
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
    permissions,
    verified: source.verified ?? source.is_verified ?? source.isVerified ?? false,
    verificationLevel: source.verificationLevel ?? source.verification_level ?? 0
  };
}

export function isAdminUser(user: { role?: string; permissions?: string[] } | null | undefined): boolean {
  if (!user) return false;
  const role = (user.role || "").toLowerCase();
  if (role === "admin" || role === "superadmin" || role === "owner" || role === "moderator") return true;
  return (user.permissions || []).some((permission) => /admin|moderat/i.test(permission));
}

const MAIN_API = (process.env.NEXT_PUBLIC_ADISCHAT_API_URL || "https://adischat-api.adischat.workers.dev").replace(/\/$/, "");
const COMMUNITY_API = (process.env.NEXT_PUBLIC_ADISCHAT_COMMUNITY_API_URL || "https://adischat-wallet-api.adischat.workers.dev").replace(/\/$/, "");

type ApiScope = "main" | "community";

function forceDesktopLogout() {
  clearDesktopSession();
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("adischat:session-revoked"));
  }
  void logout().catch(() => undefined);
}

async function request<T>(path: string, init: RequestInit = {}, scope: ApiScope = "main"): Promise<T> {
  const token = await getAuthToken();
  const base = scope === "community" ? COMMUNITY_API : MAIN_API;
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (!(init.body instanceof FormData) && init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${base}${path}`, { ...init, headers });
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
    throw new Error(message);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
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
  getCommunities: (type: "group" | "channel", q = "") =>
    request<CommunityRoom[]>(`/api/communities?type=${type}&q=${encodeURIComponent(q)}`, {}, "community"),
  getMessages: (id: string) => request<Message[]>(`/api/conversations/${encodeURIComponent(id)}/messages`),
  getCommunityMessages: (id: string) =>
    request<Message[]>(`/api/communities/${encodeURIComponent(id)}/messages`, {}, "community"),
  getChannelPosts: (id: string) =>
    request<{ posts?: ChannelPost[] } | ChannelPost[]>(`/api/channels/${encodeURIComponent(id)}/posts`, {}, "community"),
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
