"use client";

import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type DragEvent } from "react";
import type { User as FirebaseUser } from "firebase/auth";
import QRCode from "qrcode";
import { api, isAdminUser } from "@/lib/api";
import { clearDesktopSession, describeToken, getDesktopSessionToken, loginWithEmail, logout, observeAuth, saveDesktopSession, trySignInWithCustomToken } from "@/lib/auth";
import { readCache, writeCache } from "@/lib/cache";
import { DesktopCallEngine, type CallState } from "@/lib/callEngine";
import { notifyNewMessage, requestNotificationPermission, setAppBadge } from "@/lib/desktopNotifications";
import { DesktopRealtime } from "@/lib/realtime";
import type { AdminChannelVerification, AdminGameApproval, AdminReport, AdminStats, AdminVerificationRequest, ChannelPost, CommunityRoom, Conversation, DesktopPreferences, DesktopQrSession, DesktopQrStatus, DesktopThread, Message, PresenceInfo, User, UserSummary } from "@/lib/types";
import { CallDeviceSettings, CallLayer } from "./CallLayer";
import { ProfilePanel, type ProfileTarget } from "./ProfilePanel";

type Filter = "all" | "unread" | "chat" | "group" | "channel";
type ThemeMode = "dark" | "light" | "system";
type DesktopView = "messaging" | "profile" | "settings" | "admin";
type ConnectionStatus = "connected" | "reconnecting" | "disconnected";
type IconName = "chat" | "group" | "channel" | "unread" | "settings" | "profile" | "search" | "check" | "mute" | "smile" | "plus" | "phone" | "video" | "more" | "refresh" | "chevron-down" | "shield";
type SearchHit = { kind: "user"; user: UserSummary } | { kind: "group" | "channel"; room: CommunityRoom };

const navItems: Array<{ id: Filter | "settings" | "profile"; label: string; icon: IconName }> = [
  { id: "all", label: "Chats", icon: "chat" },
  { id: "group", label: "Groups", icon: "group" },
  { id: "channel", label: "Channels", icon: "channel" },
  { id: "unread", label: "Unread", icon: "unread" },
  { id: "settings", label: "Settings", icon: "settings" },
  { id: "profile", label: "Profile", icon: "profile" }
];

// Common emoji set for the composer picker.
const EMOJIS = [
  "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😉", "😎", "🤩",
  "🙂", "🙃", "😇", "🥰", "😋", "😜", "🤔", "🤗", "🙄", "😴",
  "😢", "😭", "😡", "😱", "😅", "😬", "🤝", "🙏", "👍", "👎",
  "👏", "🙌", "💪", "🔥", "✨", "🎉", "❤️", "🧡", "💛", "💚",
  "💙", "💜", "💯", "✅", "❌", "⭐", "🌟", "💔", "👀", "🤞"
];

const defaultPreferences: DesktopPreferences = {
  notificationsEnabled: true,
  messageNotifications: true,
  groupNotifications: true,
  channelNotifications: true,
  sound: true,
  notificationPreview: true,
  theme: "dark",
  fontSize: "normal",
  chatBackground: "clean",
  bubbleStyle: "modern",
  enterToSend: true,
  mediaAutoDownload: false,
  downloadFolder: "Downloads/AdisChat",
  linkPreviews: true,
  emojiSuggestions: true,
  autoDeleteTemp: true,
  appLock: false,
  language: "en",
  lastSeenVisibility: "everyone",
  profilePhotoVisibility: "everyone",
  whoCanMessageMe: "everyone",
  readReceipts: true,
  connectionIndicatorStyle: "orbit"
};

function toThreads(conversations: Conversation[], groups: CommunityRoom[], channels: CommunityRoom[]): DesktopThread[] {
  const chatThreads = conversations.map<DesktopThread>((item) => ({
    id: item.id,
    kind: "chat",
    title: item.otherUserName || "Private chat",
    subtitle: item.otherUserId,
    avatar: item.otherUserPhotoUrl,
    lastPreview: item.lastMessage || "No messages yet",
    lastAt: item.lastMessageAt,
    unreadCount: item.unreadCount || 0,
    muted: item.muted,
    pinned: item.pinned,
    archived: item.archived,
    online: false,
    verified: item.otherUserVerified || (item.otherUserVerificationLevel || 0) >= 3,
    raw: item
  }));
  const communityThreads = [...groups, ...channels].map<DesktopThread>((item) => ({
    id: item.id,
    kind: item.type,
    title: item.name || (item.type === "group" ? "Group" : "Channel"),
    subtitle: item.description,
    avatar: item.photoUrl,
    lastPreview: item.lastPostPreview || item.lastMessage || "No updates yet",
    lastAt: item.lastPostAt || item.lastMessageAt,
    unreadCount: item.unreadCount || 0,
    pinned: false,
    online: false,
    verified: item.verified,
    raw: item
  }));
  return [...chatThreads, ...communityThreads].sort(sortThreads);
}

function sortThreads(a: DesktopThread, b: DesktopThread) {
  return Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)) || (b.lastAt || 0) - (a.lastAt || 0);
}

function messagePreviewText(message: Message | undefined) {
  if (!message) return "";
  if (message.text) return message.text;
  if (message.type === "image") return "Image";
  if (message.type === "video") return "Video";
  if (message.type === "voice") return "Voice message";
  if (message.type === "file" || message.type === "document") return "File";
  return "New message";
}

function postPreviewText(post: ChannelPost | undefined) {
  if (!post) return "";
  return post.body || post.title || (post.mediaUrl ? "New post" : "");
}

/**
 * Rebuilds the thread list per section. When a section's fetch failed (its arg is
 * null), the previously-known threads of that kind are preserved instead of being
 * dropped — so a community-API failure never makes groups/channels disappear.
 */
function mergeThreads(
  existing: DesktopThread[],
  conversations: Conversation[] | null,
  groups: CommunityRoom[] | null,
  channels: CommunityRoom[] | null
): DesktopThread[] {
  const chats = conversations !== null ? toThreads(conversations, [], []) : existing.filter((t) => t.kind === "chat");
  const groupThreads = groups !== null ? toThreads([], groups, []) : existing.filter((t) => t.kind === "group");
  const channelThreads = channels !== null ? toThreads([], [], channels) : existing.filter((t) => t.kind === "channel");
  return [...chats, ...groupThreads, ...channelThreads].sort(sortThreads);
}

function formatTime(value?: number) {
  if (!value) return "";
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatDate(value?: number | string) {
  if (!value) return "Not available";
  const date = typeof value === "number" ? new Date(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(date);
}

function avatarLabel(title: string) {
  return title.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "A";
}

function profileCacheKey(uid: string) {
  return `desktop:user:${uid}:profile`;
}

function AppIcon({ name }: { name: IconName }) {
  const common = { width: 22, height: 22, viewBox: "0 0 24 24", fill: "none", xmlns: "http://www.w3.org/2000/svg", "aria-hidden": true };
  switch (name) {
    case "chat":
      return <svg className="app-icon" {...common}><path d="M5 6.75A4.25 4.25 0 0 1 9.25 2.5h5.5A4.25 4.25 0 0 1 19 6.75v3.5a4.25 4.25 0 0 1-4.25 4.25H11l-4.4 3.2a.7.7 0 0 1-1.1-.57V14.1A4.24 4.24 0 0 1 5 10.25v-3.5Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><path d="M9 8h6M9 11h3.7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "group":
      return <svg className="app-icon" {...common}><path d="M8.5 11.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4ZM15.7 10.4a2.75 2.75 0 1 0 0-5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M3.5 19.2c.55-3.2 2.35-5 5-5s4.45 1.8 5 5M13.6 14.6c2.4.15 4 1.7 4.5 4.6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "channel":
      return <svg className="app-icon" {...common}><path d="M4 13.2 18.2 5.4c.75-.42 1.58.32 1.25 1.11l-5.35 12.8c-.33.8-1.48.74-1.73-.09l-1.45-4.86L6.1 12.9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><path d="m10.9 14.35 3.25-3.35" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "unread":
      return <svg className="app-icon" {...common}><circle cx="12" cy="12" r="4.5" fill="currentColor"/><circle cx="12" cy="12" r="8" stroke="currentColor" strokeWidth="1.6" opacity=".35"/></svg>;
    case "settings":
      return <svg className="app-icon" {...common}><path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" stroke="currentColor" strokeWidth="1.8"/><path d="M18.35 13.7c.08-.55.08-.85 0-1.4l1.45-1.1-1.7-2.95-1.7.7a7.1 7.1 0 0 0-1.2-.7L15 6.45h-3.4l-.25 1.8c-.42.18-.82.42-1.2.7l-1.7-.7-1.7 2.95 1.45 1.1c-.08.55-.08.85 0 1.4l-1.45 1.1 1.7 2.95 1.7-.7c.38.28.78.52 1.2.7l.25 1.8H15l.25-1.8c.42-.18.82-.42 1.2-.7l1.7.7 1.7-2.95-1.5-1.1Z" stroke="currentColor" strokeWidth="1.45" strokeLinejoin="round"/></svg>;
    case "profile":
      return <svg className="app-icon" {...common}><path d="M12 12.2a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4.8 20.2c.75-4 3.2-6 7.2-6s6.45 2 7.2 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "search":
      return <svg className="app-icon" {...common}><circle cx="10.8" cy="10.8" r="5.6" stroke="currentColor" strokeWidth="1.9"/><path d="m15.2 15.2 4 4" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round"/></svg>;
    case "check":
      return <svg className="app-icon" {...common}><path d="m5.2 12.4 4 4L18.8 7" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "mute":
      return <svg className="app-icon" {...common}><path d="M5 10.2v3.6h3.1l4.4 3.6V6.6l-2.4 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><path d="m18.5 8.5-7 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "smile":
      return <svg className="app-icon" {...common}><circle cx="12" cy="12" r="8" stroke="currentColor" strokeWidth="1.8"/><path d="M8.8 10h.01M15.2 10h.01M8.7 14.2c1.8 1.6 4.8 1.6 6.6 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "plus":
      return <svg className="app-icon" {...common}><path d="M12 5.5v13M5.5 12h13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
    case "phone":
      return <svg className="app-icon" {...common}><path d="M8.1 5.1 10 8.8c.28.55.14 1.22-.34 1.62l-1.15.95a10.2 10.2 0 0 0 4.15 4.13l.94-1.14c.4-.49 1.07-.63 1.63-.35l3.66 1.9c.67.34.96 1.14.66 1.83-.56 1.31-1.9 2.15-3.33 2.02C9.85 19.17 4.83 14.15 4.25 7.78c-.13-1.43.71-2.77 2.02-3.33.69-.3 1.49 0 1.83.66Z" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "video":
      return <svg className="app-icon" {...common}><rect x="4" y="7" width="11" height="10" rx="2.2" stroke="currentColor" strokeWidth="1.8"/><path d="m15 10 4.2-2.3c.53-.29 1.18.09 1.18.7v7.2c0 .61-.65.99-1.18.7L15 14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "more":
      return <svg className="app-icon" {...common}><path d="M6.5 12h.01M12 12h.01M17.5 12h.01" stroke="currentColor" strokeWidth="3" strokeLinecap="round"/></svg>;
    case "refresh":
      return <svg className="app-icon" {...common}><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"/><path d="M19.6 4.2v3.4h-3.4" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "chevron-down":
      return <svg className="app-icon" {...common}><path d="m6 9.5 6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "shield":
      return <svg className="app-icon" {...common}><path d="M12 3.2 19 6v5.7c0 4.25-2.86 7.42-7 9.1-4.14-1.68-7-4.85-7-9.1V6l7-2.8Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/><path d="m8.6 12 2.15 2.15 4.65-5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"/></svg>;
  }
}

function MessageStatus({ message }: { message: Message }) {
  if (message.localStatus === "sending") return <span className="message-status sending" aria-label="Sending" />;
  if (message.readAt) return <span className="message-status seen" aria-label="Seen"><i /><i /></span>;
  if (message.deliveredAt) return <span className="message-status delivered" aria-label="Delivered"><i /></span>;
  return <span className="message-status sent" aria-label="Sent"><i /></span>;
}

function ConnectionIndicator({ status }: { status: ConnectionStatus }) {
  if (status === "connected") return null;
  return (
    <div className="connection-indicator" title="Connecting" aria-label="Connecting">
      <span className="signal-dots"><i /><i /><i /></span>
    </div>
  );
}

export function DesktopShell() {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  // Profile cache is user-scoped. Never hydrate a global profile here; otherwise
  // switching accounts can briefly show the previous account's photo/role.
  const [me, setMe] = useState<User | null>(null);
  const [profileLoadState, setProfileLoadState] = useState<"idle" | "loading" | "loaded" | "error">("idle");
  const [profileError, setProfileError] = useState("");
  const [desktopSessionToken, setDesktopSessionToken] = useState(() => getDesktopSessionToken());
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");
  const [threads, setThreads] = useState<DesktopThread[]>(() => readCache("desktop:threads", []));
  const [searchHits, setSearchHits] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [activeId, setActiveId] = useState(() => readCache("desktop:activeThreadId", ""));
  const [messagesByThread, setMessagesByThread] = useState<Record<string, Message[]>>(() => readCache("desktop:messages", {}));
  const [postsByThread, setPostsByThread] = useState<Record<string, ChannelPost[]>>(() => readCache("desktop:posts", {}));
  const [selectedMessages, setSelectedMessages] = useState<string[]>([]);
  const [composer, setComposer] = useState("");
  const [replyTo, setReplyTo] = useState<Message | null>(null);
  const [editingMessage, setEditingMessage] = useState<Message | null>(null);
  const [forwardingMessage, setForwardingMessage] = useState<Message | null>(null);
  const [toast, setToast] = useState("");
  const [messageSearch, setMessageSearch] = useState("");
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [isDraggingFiles, setIsDraggingFiles] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [showEmoji, setShowEmoji] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("connected");
  const composerRef = useRef<HTMLTextAreaElement>(null);
  const activeThreadRef = useRef<DesktopThread | undefined>(undefined);
  const [viewer, setViewer] = useState<{ url: string; type: string } | null>(null);
  const [callState, setCallState] = useState<CallState | null>(null);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [profileTarget, setProfileTarget] = useState<ProfileTarget | null>(null);
  const [activePresence, setActivePresence] = useState<PresenceInfo | null>(null);
  const [theme, setTheme] = useState<ThemeMode>(() => readCache("desktop:theme", "dark"));
  const [view, setView] = useState<DesktopView>("messaging");
  const [prefs, setPrefs] = useState<DesktopPreferences>(() => readCache("desktop:prefs", defaultPreferences));
  const [profileStatus, setProfileStatus] = useState<{ loading: boolean; error: string; success: string }>({ loading: false, error: "", success: "" });
  const [showScrollDown, setShowScrollDown] = useState(false);
  const [unreadBelow, setUnreadBelow] = useState(0);
  const [unreadDividerIndex, setUnreadDividerIndex] = useState(-1);
  const [unreadDividerCount, setUnreadDividerCount] = useState(0);
  const messageScrollRef = useRef<HTMLDivElement>(null);
  const positionedThreadRef = useRef("");
  const prevMsgCountRef = useRef(0);
  const dragDepthRef = useRef(0);
  // Furthest-down content position the user has actually viewed. Only messages
  // below this are "new" — scrolling back up over seen messages never inflates it.
  const seenBottomRef = useRef(0);
  const realtimeRef = useRef<DesktopRealtime | null>(null);
  const engineRef = useRef<DesktopCallEngine | null>(null);
  const threadsRef = useRef<DesktopThread[]>([]);
  const prefsRef = useRef<DesktopPreferences>(prefs);
  const meRef = useRef<User | null>(me);
  const activeThread = threads.find((thread) => thread.id === activeId) || threads[0];
  const activeMessages = activeThread?.kind === "channel" ? [] : messagesByThread[activeThread?.id || ""] || [];
  const activePosts = activeThread?.kind === "channel" ? postsByThread[activeThread.id] || [] : [];
  const activeIsCommunity = activeThread?.kind === "group";
  const isAuthenticated = Boolean(firebaseUser || desktopSessionToken);

  const filteredThreads = useMemo(() => {
    return threads.filter((thread) => {
      const matchesFilter = filter === "all" || (filter === "unread" ? thread.unreadCount > 0 : thread.kind === filter);
      const term = query.trim().toLowerCase();
      const matchesQuery = !term || `${thread.title} ${thread.lastPreview || ""}`.toLowerCase().includes(term);
      return matchesFilter && matchesQuery;
    });
  }, [filter, query, threads]);
  const visibleSearchHits = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return [];
    return searchHits.filter((hit) => {
      if (filter !== "all" && filter !== hit.kind) return false;
      if (hit.kind === "user") {
        const user = hit.user;
        return !threads.some((thread) => thread.kind === "chat" && (thread.raw as Conversation).otherUserId === user.uid);
      }
      return !threads.some((thread) => thread.kind === hit.kind && thread.id === hit.room.id);
    });
  }, [filter, query, searchHits, threads]);

  const searchedMessages = useMemo(() => {
    if (!messageSearch.trim()) return activeMessages;
    const term = messageSearch.toLowerCase();
    return activeMessages.filter((message) => `${message.text || ""} ${message.senderName || ""}`.toLowerCase().includes(term));
  }, [activeMessages, messageSearch]);

  const unreadTotal = threads.reduce((sum, thread) => sum + thread.unreadCount, 0);

  useEffect(() => observeAuth(setFirebaseUser), []);
  useEffect(() => {
    const uid = firebaseUser?.uid || "";
    if (!uid) return;
    const cached = readCache<User | null>(profileCacheKey(uid), null);
    if (cached?.uid === uid) setMe(cached);
    else if (meRef.current?.uid && meRef.current.uid !== uid) setMe(null);
  }, [firebaseUser?.uid]);
  useEffect(() => {
    const handleRevoked = () => {
      setDesktopSessionToken("");
      setMe(null);
      setThreads([]);
      setMessagesByThread({});
      setPostsByThread({});
      setActiveId("");
      setAuthError("This device was logged out from another device.");
      realtimeRef.current?.stop();
      engineRef.current?.destroy();
    };
    window.addEventListener("adischat:session-revoked", handleRevoked);
    return () => window.removeEventListener("adischat:session-revoked", handleRevoked);
  }, []);
  useEffect(() => setAppBadge(unreadTotal), [unreadTotal]);
  useEffect(() => writeCache("desktop:threads", threads), [threads]);
  useEffect(() => writeCache("desktop:messages", messagesByThread), [messagesByThread]);
  useEffect(() => writeCache("desktop:posts", postsByThread), [postsByThread]);
  useEffect(() => writeCache("desktop:activeThreadId", activeId), [activeId]);
  useEffect(() => writeCache("desktop:theme", theme), [theme]);
  useEffect(() => writeCache("desktop:prefs", prefs), [prefs]);
  useEffect(() => {
    activeThreadRef.current = activeThread;
  });
  useEffect(() => {
    meRef.current = me;
    if (me?.uid) writeCache(profileCacheKey(me.uid), me);
  }, [me]);
  useEffect(() => setTheme(prefs.theme), [prefs.theme]);
  useEffect(() => {
    threadsRef.current = threads;
  }, [threads]);
  useEffect(() => {
    prefsRef.current = prefs;
  }, [prefs]);

  // Real call engine: WebRTC P2P over the existing /api/calls signaling backend.
  useEffect(() => {
    const engine = new DesktopCallEngine({
      onState: setCallState,
      onLocalStream: setLocalStream,
      onRemoteStream: setRemoteStream,
      getSoundEnabled: () => prefsRef.current.sound,
      onFinished: (info) => {
        if (!info.isCaller) return;
        const thread = threadsRef.current.find(
          (item) => item.kind === "chat" && (item.raw as Conversation).otherUserId === info.peerUid
        );
        if (!thread) return;
        const kindLabel = info.type === "video" ? "Video call" : "Voice call";
        const minutes = String(Math.floor(info.durationSeconds / 60)).padStart(2, "0");
        const seconds = String(info.durationSeconds % 60).padStart(2, "0");
        const text =
          info.outcome === "ended" ? `${kindLabel} - ${minutes}:${seconds}`
          : info.outcome === "missed" ? `Missed ${kindLabel.toLowerCase()}`
          : info.outcome === "rejected" ? `${kindLabel} declined`
          : info.outcome === "cancelled" ? `Cancelled ${kindLabel.toLowerCase()}`
          : `${kindLabel} failed`;
        void api
          .sendMessage(thread.id, text, { type: "call", durationMs: info.durationSeconds * 1000 })
          .then((sent) => {
            setMessagesByThread((current) => ({ ...current, [thread.id]: [...(current[thread.id] || []), sent] }));
          })
          .catch(() => undefined);
      }
    });
    engineRef.current = engine;
    return () => {
      engine.destroy();
      engineRef.current = null;
    };
  }, []);

  // Listen for incoming calls once the signed-in uid is known.
  useEffect(() => {
    if (me?.uid) engineRef.current?.startIncomingWatcher(me.uid);
    return () => engineRef.current?.stopIncomingWatcher();
  }, [me?.uid]);

  // Presence heartbeat so other devices see this desktop as online.
  useEffect(() => {
    if (!isAuthenticated) return;
    const beat = () => void api.heartbeat().catch(() => undefined);
    beat();
    const timer = window.setInterval(beat, 45_000);
    return () => window.clearInterval(timer);
  }, [isAuthenticated]);

  // Live presence for the open private chat (header status + profile panel accuracy).
  useEffect(() => {
    const conversation = activeThread?.kind === "chat" ? (activeThread.raw as Conversation) : null;
    const uid = conversation?.otherUserId;
    if (!uid || !isAuthenticated) {
      setActivePresence(null);
      return;
    }
    let cancelled = false;
    const poll = () =>
      void api
        .getPresence(uid)
        .then((presence) => {
          if (!cancelled) setActivePresence(presence);
        })
        .catch(() => undefined);
    poll();
    const timer = window.setInterval(poll, 30_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [activeThread?.id, activeThread?.kind, isAuthenticated]);

  // Fetch the authenticated user profile on its own — never bundled with the
  // thread/conversation calls — so a conversation/community failure can't blank
  // the profile, and a profile failure can't blank the chats. Retries a few times
  // before surfacing an error, and only then falls back to a placeholder.
  const loadProfile = useCallback(async () => {
    if (!isAuthenticated) return;
    setProfileLoadState("loading");
    setProfileError("");
    const attempts = 3;
    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      try {
        const profile = await api.getMe();
        if (!profile.uid) throw new Error("Profile response had no user id");
        setMe(profile);
        setProfileLoadState("loaded");
        // TODO(temporary): remove once QR-login profile hydration is confirmed in the field.
        console.info("[desktop-auth] profile loaded", { userId: profile.uid, username: profile.username, role: profile.role, isAdmin: isAdminUser(profile) });
        return;
      } catch (error) {
        console.warn(`[desktop-auth] profile fetch attempt ${attempt}/${attempts} failed`, error);
        if (attempt === attempts) {
          setProfileError(error instanceof Error ? error.message : "Could not load profile");
          // Keep any cached profile (and its admin role) rather than wiping to a placeholder.
          setProfileLoadState((current) => (meRef.current?.uid ? "loaded" : "error"));
          return;
        }
        await new Promise((resolve) => window.setTimeout(resolve, attempt * 800));
      }
    }
  }, [isAuthenticated]);

  const loadThreads = useCallback(async () => {
    if (!isAuthenticated) return;
    // Each source is independent. A source that fails yields null so its existing
    // (cached) threads are preserved by mergeThreads — a failing community API can
    // never blank the groups/channels that are already known.
    const [convRes, groupRes, channelRes] = await Promise.allSettled([
      api.getConversations(),
      api.getCommunities("group"),
      api.getCommunities("channel")
    ]);
    const conversations = convRes.status === "fulfilled" ? convRes.value : null;
    const groups = groupRes.status === "fulfilled" ? groupRes.value : null;
    const channels = channelRes.status === "fulfilled" ? channelRes.value : null;
    // TODO(temporary): chat/group/channel loading diagnostics.
    console.info("[desktop-load] threads", {
      conversations: conversations?.length ?? `FAILED: ${(convRes as PromiseRejectedResult).reason}`,
      groups: groups?.length ?? `FAILED: ${(groupRes as PromiseRejectedResult).reason}`,
      channels: channels?.length ?? `FAILED: ${(channelRes as PromiseRejectedResult).reason}`
    });
    setThreads((current) => {
      const merged = mergeThreads(current, conversations, groups, channels);
      const active = current.find((thread) => thread.id === activeId);
      const next = active && !merged.some((thread) => thread.id === active.id && thread.kind === active.kind)
        ? [active, ...merged].sort(sortThreads)
        : merged;
      setActiveId((id) => id || next[0]?.id || "");
      return next;
    });
  }, [activeId, isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) return;
    void requestNotificationPermission();
    void loadProfile();
    void loadThreads();
  }, [isAuthenticated, loadProfile, loadThreads]);

  useEffect(() => {
    if (!isAuthenticated) return;
    const timer = window.setInterval(() => {
      void loadThreads();
    }, 8000);
    return () => window.clearInterval(timer);
  }, [isAuthenticated, loadThreads]);

  useEffect(() => {
    const term = query.trim();
    if (!isAuthenticated || term.length < 2) {
      setSearchHits([]);
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timer = window.setTimeout(async () => {
      const [usersRes, groupsRes, channelsRes] = await Promise.allSettled([
        filter === "group" || filter === "channel" ? Promise.resolve([]) : api.searchUsers(term),
        filter === "chat" || filter === "channel" || filter === "unread" ? Promise.resolve([]) : api.getCommunities("group", term),
        filter === "chat" || filter === "group" || filter === "unread" ? Promise.resolve([]) : api.getCommunities("channel", term)
      ]);
      if (cancelled) return;
      const hits: SearchHit[] = [
        ...(usersRes.status === "fulfilled" ? usersRes.value.map((user): SearchHit => ({ kind: "user", user })) : []),
        ...(groupsRes.status === "fulfilled" ? groupsRes.value.map((room): SearchHit => ({ kind: "group", room })) : []),
        ...(channelsRes.status === "fulfilled" ? channelsRes.value.map((room): SearchHit => ({ kind: "channel", room })) : [])
      ];
      setSearchHits(hits);
      setSearching(false);
    }, 260);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [filter, isAuthenticated, query]);

  useEffect(() => {
    if (!isAuthenticated) return;
    realtimeRef.current?.stop();
    realtimeRef.current = new DesktopRealtime(
      () => threadsRef.current,
      () => activeThreadRef.current,
      {
        onMessages: (threadId, messages) => {
          setMessagesByThread((current) => {
            const previousLast = current[threadId]?.at(-1)?.id;
            const nextLast = messages.at(-1)?.id;
            const thread = threadsRef.current.find((item) => item.id === threadId);
            if (thread && previousLast && nextLast && previousLast !== nextLast && activeId !== threadId) {
              notifyNewMessage(thread, messages.at(-1)!);
            }
            return { ...current, [threadId]: messages };
          });
          const last = messages.at(-1);
          if (last) {
            setThreads((current) => current.map((thread) =>
              thread.id === threadId
                ? {
                    ...thread,
                    lastPreview: messagePreviewText(last),
                    lastAt: last.timestamp || thread.lastAt,
                    unreadCount: activeThreadRef.current?.id === threadId ? 0 : thread.unreadCount
                  }
                : thread
            ).sort(sortThreads));
          }
        },
        onChannelPosts: (threadId, posts) => {
          setPostsByThread((current) => ({ ...current, [threadId]: posts }));
          const last = posts.at(-1);
          if (last) {
            setThreads((current) => current.map((thread) =>
              thread.id === threadId
                ? {
                    ...thread,
                    lastPreview: postPreviewText(last),
                    lastAt: last.createdAt || thread.lastAt,
                    unreadCount: activeThreadRef.current?.id === threadId ? 0 : thread.unreadCount
                  }
                : thread
            ).sort(sortThreads));
          }
        },
        onStatus: setConnectionStatus
      }
    );
    realtimeRef.current.start();
    return () => realtimeRef.current?.stop();
  }, [isAuthenticated]);

  // Loads the open thread's history/posts from the API. Reused by the open-thread
  // effect and the manual Refresh button so both always pull fresh server data.
  const loadActiveThread = useCallback(async (thread: DesktopThread | undefined) => {
    if (!thread) return;
    try {
      if (thread.kind === "channel") {
        const response = await api.getChannelPosts(thread.id);
        const posts = Array.isArray(response) ? response : response.posts || [];
        console.info("[desktop-load] channel posts", { channelId: thread.id, count: posts.length });
        setPostsByThread((current) => ({ ...current, [thread.id]: posts }));
        return;
      }
      const messages = thread.kind === "group" ? await api.getCommunityMessages(thread.id) : await api.getMessages(thread.id);
      console.info("[desktop-load] messages", { threadId: thread.id, kind: thread.kind, count: messages.length });
      setMessagesByThread((current) => ({ ...current, [thread.id]: messages }));
      void api.markRead(thread.id, thread.kind === "group").catch(() => undefined);
    } catch (error) {
      console.warn("[desktop-load] active thread FAILED", { id: thread.id, kind: thread.kind, error: String(error) });
    }
  }, []);

  useEffect(() => {
    // Always load existing history immediately from the API on open — never wait
    // for the realtime poller/websocket to populate it.
    void loadActiveThread(activeThread);
  }, [activeThread?.id, activeThread?.kind, loadActiveThread]);

  // Realtime fallback now polls automatically every 2s. The UI intentionally has
  // no manual refresh button; degraded network state is shown by the premium
  // animated indicator only while reconnecting/disconnected.
  const handleRefresh = useCallback(async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.allSettled([loadThreads(), loadActiveThread(activeThread)]);
    } finally {
      setRefreshing(false);
    }
  }, [refreshing, loadThreads, loadActiveThread, activeThread]);

  // ── Chat scroll: open at unread/last position, scroll-to-bottom FAB ──────────
  // Manual eased animation (native `behavior: "smooth"` is unreliable inside some
  // webviews); pass smooth=false for an instant jump.
  const scrollMessagesToBottom = useCallback((smooth = true) => {
    const el = messageScrollRef.current;
    if (!el) return;
    const targetTop = el.scrollHeight - el.clientHeight;
    const start = el.scrollTop;
    const dist = targetTop - start;
    if (!smooth || Math.abs(dist) < 4) {
      el.scrollTop = targetTop;
      return;
    }
    const duration = Math.min(480, 140 + Math.abs(dist) * 0.35);
    const startedAt = performance.now();
    const step = (nowTs: number) => {
      const progress = Math.min(1, (nowTs - startedAt) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      el.scrollTop = start + dist * eased;
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
    // Safety: guarantee we land at the bottom even if rAF is throttled.
    window.setTimeout(() => {
      const box = messageScrollRef.current;
      if (box) box.scrollTop = box.scrollHeight - box.clientHeight;
    }, duration + 70);
  }, []);

  const recomputeScrollState = useCallback(() => {
    const el = messageScrollRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    setShowScrollDown(distanceFromBottom > 240);
    // The read boundary only moves DOWN: it's the lowest content offset the user
    // has scrolled to. Scrolling back up leaves it unchanged, so already-seen
    // messages are never counted as new.
    const viewBottomOffset = el.scrollTop + el.clientHeight;
    seenBottomRef.current = Math.max(seenBottomRef.current, viewBottomOffset);
    const boundary = seenBottomRef.current;
    const containerTop = el.getBoundingClientRect().top;
    // Count only messages that start below the read boundary = genuinely unseen.
    let newBelow = 0;
    el.querySelectorAll(".messages > .message, .channel-posts > .channel-post").forEach((node) => {
      const offsetTop = node.getBoundingClientRect().top - containerTop + el.scrollTop;
      if (offsetTop >= boundary - 12) newBelow += 1;
    });
    setUnreadBelow(newBelow);
  }, []);

  // Position on open (first unread, else bottom) and follow new messages when near
  // the bottom. The active history is loaded by loadActiveThread; this just places it.
  useEffect(() => {
    const el = messageScrollRef.current;
    if (!el || !activeThread) return;
    const count = activeThread.kind === "channel" ? activePosts.length : searchedMessages.length;
    const freshOpen = positionedThreadRef.current !== activeThread.id;

    if (freshOpen) {
      if (count === 0) return; // wait until this thread's history has loaded
      positionedThreadRef.current = activeThread.id;
      prevMsgCountRef.current = count;
      seenBottomRef.current = 0; // fresh read boundary per chat
      const unread = activeThread.kind === "channel" ? 0 : Math.min(activeThread.unreadCount || 0, count);
      setUnreadDividerCount(unread);
      setUnreadDividerIndex(unread > 0 ? count - unread : -1);
      // Place at the first unread message (else the bottom). Compute the offset from
      // the message node directly so it doesn't depend on the divider's render timing,
      // and retry briefly to survive late layout (fonts/images settling).
      const place = () => {
        const box = messageScrollRef.current;
        if (!box) return;
        const divider = box.querySelector(".unread-divider") as HTMLElement | null;
        const nodes = box.querySelectorAll(".messages > .message");
        const target = divider || (unread > 0 ? (nodes[count - unread] as HTMLElement | undefined) : undefined);
        if (target) {
          const top = target.getBoundingClientRect().top - box.getBoundingClientRect().top + box.scrollTop;
          box.scrollTop = Math.max(0, top - 56);
        } else {
          box.scrollTop = box.scrollHeight;
        }
        recomputeScrollState();
      };
      requestAnimationFrame(() => requestAnimationFrame(place));
      window.setTimeout(place, 140);
      return;
    }

    // Same thread, new message(s) arrived.
    if (count > prevMsgCountRef.current) {
      const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
      if (distanceFromBottom < 320) requestAnimationFrame(() => scrollMessagesToBottom(true));
      else recomputeScrollState();
    }
    prevMsgCountRef.current = count;
  }, [activeThread?.id, activeThread?.kind, activeThread?.unreadCount, searchedMessages.length, activePosts.length, recomputeScrollState, scrollMessagesToBottom]);

  async function handleDesktopQrApproved(status: DesktopQrStatus) {
    if (!status.desktopToken) return;
    // TODO(temporary): remove these auth-flow logs once QR profile hydration is confirmed.
    console.info("[desktop-auth] QR approved", { userId: status.userId });
    // If the approval token is a Firebase custom token, exchange it for a real
    // Firebase session so BOTH the main API and the community/wallet API accept
    // the resulting ID token. This is what makes groups and channels load — they
    // are served by the community worker, which only validates Firebase tokens.
    const tokenShape = describeToken(status.desktopToken);
    console.info("[desktop-auth] token shape", tokenShape);
    const exchange = await trySignInWithCustomToken(status.desktopToken);
    console.info("[desktop-auth] custom-token exchange", exchange);
    // Surface the diagnosis in Settings > About so it can be read without devtools.
    writeCache("desktop:lastQrDiag", { at: Date.now(), tokenShape, exchange });
    saveDesktopSession({ token: status.desktopToken, userId: status.userId, expiresAt: status.expiresAt });
    console.info("[desktop-auth] desktop session saved");
    if (!exchange.ok) setDesktopSessionToken(status.desktopToken);
    setAuthError("");
    // Seed the user id immediately so downstream effects (incoming-call watcher,
    // self-profile) have it before the full profile fetch resolves.
    if (status.userId && !meRef.current?.uid) {
      setMe((current) => current?.uid ? current : { uid: status.userId as string, name: "" });
    }
    // Becoming authenticated triggers loadProfile() via effect; nothing else needed.
  }

  function showToast(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(""), 1800);
  }

  // Inserts an emoji at the textarea caret (or appends), keeping focus + caret.
  function insertEmoji(emoji: string) {
    const ta = composerRef.current;
    if (!ta) {
      setComposer((current) => current + emoji);
      return;
    }
    const start = ta.selectionStart ?? composer.length;
    const end = ta.selectionEnd ?? composer.length;
    setComposer(composer.slice(0, start) + emoji + composer.slice(end));
    requestAnimationFrame(() => {
      ta.focus();
      const caret = start + emoji.length;
      ta.setSelectionRange(caret, caret);
    });
  }

  async function sendText() {
    if (!activeThread || !composer.trim() || activeThread.kind === "channel") return;
    setShowEmoji(false);
    const text = composer.trim();

    if (editingMessage) {
      const target = editingMessage;
      setEditingMessage(null);
      setComposer("");
      setMessagesByThread((current) => ({
        ...current,
        [activeThread.id]: (current[activeThread.id] || []).map((message) =>
          message.id === target.id ? { ...message, text, edited: true } : message
        )
      }));
      try {
        await api.edit(activeThread.id, target.id, text, activeThread.kind === "group");
        showToast("Message edited");
      } catch (error) {
        showToast(error instanceof Error ? error.message : "Edit failed");
      }
      return;
    }

    const reply = replyTo;
    const local: Message = {
      id: `local-${Date.now()}`,
      text,
      type: "text",
      senderId: me?.uid,
      senderName: me?.name,
      timestamp: Date.now(),
      localStatus: "sending",
      replyToMessageId: reply?.id,
      replySenderName: reply?.senderName || (reply ? "Message" : ""),
      replyPreview: messagePreview(reply)
    };
    setComposer("");
    setReplyTo(null);
    setMessagesByThread((current) => ({ ...current, [activeThread.id]: [...(current[activeThread.id] || []), local] }));
    try {
      const extra = reply ? { replyToMessageId: reply.id } : undefined;
      const sent =
        activeThread.kind === "group"
          ? await api.sendCommunityMessage(activeThread.id, text, extra)
          : await api.sendMessage(activeThread.id, text, extra);
      setMessagesByThread((current) => ({
        ...current,
        [activeThread.id]: (current[activeThread.id] || []).map((message) => (message.id === local.id ? sent : message))
      }));
    } catch (error) {
      setMessagesByThread((current) => ({
        ...current,
        [activeThread.id]: (current[activeThread.id] || []).map((message) =>
          message.id === local.id ? { ...message, localStatus: "failed", localError: error instanceof Error ? error.message : "Failed" } : message
        )
      }));
    }
  }
  async function uploadFiles(files: FileList | File[]) {
    if (!activeThread || activeThread.kind === "channel") {
      showToast("Choose a chat or group to send files");
      return;
    }
    const list = Array.from(files).filter((file) => file.size > 0);
    if (!list.length) return;
    for (const file of list) {
      try {
        const mediaKind = file.type.startsWith("image/") ? "image" : file.type.startsWith("video/") ? "video" : "file";
        setUploadProgress(1);
        const uploaded = await api.uploadMedia(file, setUploadProgress);
        const media = { type: mediaKind as Message["type"], mediaUrl: uploaded.url, mediaMimeType: file.type };
        if (activeThread.kind === "group") await api.sendCommunityMessage(activeThread.id, file.name, media);
        else await api.sendMessage(activeThread.id, file.name, media);
      } catch (error) {
        showToast(error instanceof Error ? error.message : "File upload failed");
      } finally {
        setUploadProgress(null);
      }
    }
  }

  function canDropFiles() {
    return Boolean(activeThread && activeThread.kind !== "channel");
  }

  function handleThreadDragEnter(event: DragEvent<HTMLElement>) {
    if (!event.dataTransfer.types.includes("Files") || !canDropFiles()) return;
    event.preventDefault();
    dragDepthRef.current += 1;
    setIsDraggingFiles(true);
  }

  function handleThreadDragOver(event: DragEvent<HTMLElement>) {
    if (!isDraggingFiles || !canDropFiles()) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  }

  function handleThreadDragLeave(event: DragEvent<HTMLElement>) {
    if (!isDraggingFiles) return;
    event.preventDefault();
    dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
    if (dragDepthRef.current === 0) setIsDraggingFiles(false);
  }

  function handleThreadDrop(event: DragEvent<HTMLElement>) {
    if (!canDropFiles()) return;
    event.preventDefault();
    dragDepthRef.current = 0;
    setIsDraggingFiles(false);
    if (event.dataTransfer.files.length) void uploadFiles(event.dataTransfer.files);
  }

  function startCall(type: "voice" | "video") {
    const receiverUid = (activeThread?.raw as Conversation | undefined)?.otherUserId;
    if (!receiverUid || !activeThread) return;
    void engineRef.current?.startOutgoing(
      { uid: receiverUid, name: activeThread.title, photoUrl: activeThread.avatar },
      type
    );
  }

  function callUser(user: { uid: string; name: string; photoUrl?: string }, type: "voice" | "video") {
    setProfileTarget(null);
    void engineRef.current?.startOutgoing(user, type);
  }

  function openProfile(target: ProfileTarget) {
    if (!target.uid) {
      showToast("Profile is not available for this user");
      return;
    }
    setProfileTarget(target);
  }

  function openProfileForThread(thread: DesktopThread) {
    if (thread.kind !== "chat") return;
    const conversation = thread.raw as Conversation;
    openProfile({
      uid: conversation.otherUserId || "",
      name: thread.title,
      photoUrl: thread.avatar,
      conversationId: thread.id
    });
  }

  function openProfileForMessage(message: Message) {
    if (!activeThread) return;
    if (message.senderId && me?.uid && message.senderId === me.uid) {
      openProfile({ uid: me.uid, name: me.name, photoUrl: me.photoUrl, username: me.username });
      return;
    }
    if (activeThread.kind === "chat") {
      openProfileForThread(activeThread);
      return;
    }
    openProfile({
      uid: message.senderId || "",
      name: message.senderName || "AdisChat user",
      photoUrl: message.senderPhotoUrl
    });
  }

  async function messageUser(uid: string) {
    if (!uid) return;
    const existing = threads.find(
      (thread) => thread.kind === "chat" && (thread.raw as Conversation).otherUserId === uid
    );
    setProfileTarget(null);
    setView("messaging");
    if (existing) {
      setActiveId(existing.id);
      return;
    }
    try {
      const { conversationId } = await api.startConversation(uid);
      await loadThreads();
      setActiveId(conversationId);
    } catch (error) {
      showToast(error instanceof Error ? error.message : "Could not open chat");
    }
  }

  function openCommunitySearchResult(room: CommunityRoom) {
    const nextThread: DesktopThread = {
      id: room.id,
      kind: room.type,
      title: room.name || (room.type === "group" ? "Group" : "Channel"),
      subtitle: room.description,
      avatar: room.photoUrl,
      lastPreview: room.lastPostPreview || room.lastMessage || "No updates yet",
      lastAt: room.lastPostAt || room.lastMessageAt,
      unreadCount: room.unreadCount || 0,
      pinned: false,
      online: false,
      verified: room.verified,
      raw: room
    };
    setThreads((current) => {
      const exists = current.some((thread) => thread.kind === nextThread.kind && thread.id === nextThread.id);
      return exists ? current : [nextThread, ...current].sort(sortThreads);
    });
    setActiveId(room.id);
    setView("messaging");
  }

  function openSearchHit(hit: SearchHit) {
    if (hit.kind === "user") {
      void messageUser(hit.user.uid);
      return;
    }
    openCommunitySearchResult(hit.room);
  }

  if (!isAuthenticated) {
    return <QrLoginScreen error={authError} setError={setAuthError} onApproved={handleDesktopQrApproved} />;
  }

  return (
    <main className={`desktop-shell theme-${theme}`}>
      <aside className="left-sidebar">
        <div className="logo">A</div>
        <nav>
          {navItems.map((item) => (
            <button
              key={item.id}
              className={filter === item.id ? "active" : ""}
              onClick={() => {
                if (item.id === "settings") {
                  setView("settings");
                  return;
                }
                if (item.id === "profile") {
                  setView("profile");
                  return;
                }
                setView("messaging");
                setFilter(item.id);
              }}
              title={item.label}
            >
              <AppIcon name={item.icon} />
              <small>{item.label}</small>
            </button>
          ))}
          {isAdminUser(me) ? (
            <button
              className={view === "admin" ? "active admin-nav" : "admin-nav"}
              onClick={() => setView("admin")}
              title="Admin Panel"
            >
              <AppIcon name="shield" />
              <small>Admin</small>
            </button>
          ) : null}
        </nav>
        <button
          className={`avatar-button ${isAdminUser(me) ? "is-admin" : ""}`}
          onClick={() => me?.uid && openProfile({ uid: me.uid, name: me.name, photoUrl: me.photoUrl, username: me.username })}
          title={isAdminUser(me) ? `${me?.name || "My profile"} - Admin` : "My profile"}
          disabled={!me?.uid}
        >
          {profileLoadState === "loading" && !me?.uid ? (
            <span className="avatar-skeleton" aria-label="Loading profile" />
          ) : me?.photoUrl ? (
            <img src={me.photoUrl} alt="" />
          ) : (
            avatarLabel(me?.name || "A")
          )}
          {isAdminUser(me) ? <span className="admin-dot" aria-hidden /> : null}
        </button>
      </aside>

      <section className="thread-panel">
        <div className="panel-header">
          <div>
            <h2>AdisChat</h2>
            <p>{unreadTotal ? `${unreadTotal} unread` : "All caught up"}</p>
          </div>
          <div className="header-actions">
            <ConnectionIndicator status={connectionStatus} />
            <button
              className={`auto-refresh-toggle ${autoRefresh ? "on" : ""}`}
              onClick={() => setAutoRefresh((value) => !value)}
              aria-pressed={autoRefresh}
              title={autoRefresh ? "Auto-refresh on — checking for new messages every 2s" : "Turn on auto-refresh (every 2s)"}
            >
              <span className="live-dots"><i /><i /><i /></span>
            </button>
            <button className={`refresh-button ${refreshing ? "is-refreshing" : ""}`} onClick={() => void handleRefresh()} disabled={refreshing} aria-label="Refresh">
              <AppIcon name="refresh" />
              <span>{refreshing ? "Refreshing" : "Refresh"}</span>
            </button>
          </div>
        </div>
        <div className="search-box">
          <AppIcon name="search" />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search chats, groups, channels" />
        </div>
        <div className="filter-row">
          {(["all", "unread", "chat", "group", "channel"] as Filter[]).map((item) => (
            <button key={item} className={filter === item ? "active" : ""} onClick={() => setFilter(item)}>
              {item}
            </button>
          ))}
        </div>
        <div className="thread-list">
          {filteredThreads.map((thread) => (
            <button key={`${thread.kind}-${thread.id}`} className={`thread-row ${activeThread?.id === thread.id ? "active" : ""}`} onClick={() => setActiveId(thread.id)}>
              <Avatar
                title={thread.title}
                src={thread.avatar}
                online={thread.online}
                onClick={thread.kind === "chat" ? () => openProfileForThread(thread) : undefined}
              />
              <span className="thread-main">
                <span className="thread-title">{thread.title}{thread.verified ? <span className="verified-badge"><AppIcon name="check" /></span> : null}</span>
                <span className="thread-preview">{thread.lastPreview}</span>
              </span>
              <span className="thread-meta">
                <span>{formatTime(thread.lastAt)}</span>
                {thread.muted ? <span className="muted-icon"><AppIcon name="mute" /></span> : null}
                {thread.unreadCount ? <strong>{thread.unreadCount}</strong> : null}
              </span>
            </button>
          ))}
          {visibleSearchHits.length ? <div className="search-results-label">More results</div> : null}
          {visibleSearchHits.map((hit) => {
            const title = hit.kind === "user" ? hit.user.name || hit.user.username || "AdisChat user" : hit.room.name || (hit.kind === "group" ? "Group" : "Channel");
            const avatar = hit.kind === "user" ? hit.user.photoUrl : hit.room.photoUrl;
            const subtitle = hit.kind === "user"
              ? [hit.user.username ? `@${hit.user.username}` : "", hit.user.bio || hit.user.about || ""].filter(Boolean).join(" - ")
              : hit.room.description || (hit.kind === "group" ? "Group" : "Channel");
            const verified = hit.kind === "user" ? hit.user.verified || (hit.user.verificationLevel || 0) >= 3 : hit.room.verified;
            return (
              <button key={`search-${hit.kind}-${hit.kind === "user" ? hit.user.uid : hit.room.id}`} className="thread-row search-hit" onClick={() => openSearchHit(hit)}>
                <Avatar title={title} src={avatar} online={false} />
                <span className="thread-main">
                  <span className="thread-title">{title}{verified ? <span className="verified-badge"><AppIcon name="check" /></span> : null}</span>
                  <span className="thread-preview">{subtitle || "Open from search"}</span>
                </span>
                <span className="thread-meta"><span>{hit.kind}</span></span>
              </button>
            );
          })}
          {searching ? <div className="search-results-label">Searching...</div> : null}
          {query.trim().length >= 2 && !searching && !filteredThreads.length && !visibleSearchHits.length ? (
            <div className="empty-results">No users, groups, or channels found</div>
          ) : null}
        </div>
      </section>

      <section
        className="chat-panel"
        onDragEnter={handleThreadDragEnter}
        onDragOver={handleThreadDragOver}
        onDragLeave={handleThreadDragLeave}
        onDrop={handleThreadDrop}
        onPaste={(event) => {
          const files = event.clipboardData.files;
          if (files.length) void uploadFiles(files);
        }}
      >
        {isDraggingFiles ? (
          <div className="drop-zone-overlay" aria-live="polite">
            <span>Drop files to send</span>
            <small>Images, videos, documents, and media</small>
          </div>
        ) : null}
        {view === "profile" ? (
          <ProfilePage
            user={me}
            firebaseUser={firebaseUser}
            prefs={prefs}
            status={profileStatus}
            loadState={profileLoadState}
            loadError={profileError}
            onRetry={() => void loadProfile()}
            onBack={() => setView("messaging")}
            onPrefsChange={(next) => setPrefs((current) => ({ ...current, ...next }))}
            onSave={async (patch) => {
              if (!me?.uid) return;
              setProfileStatus({ loading: true, error: "", success: "" });
              try {
                const updated = await api.updateUser(me.uid, { ...me, ...patch });
                setMe(updated);
                setProfileStatus({ loading: false, error: "", success: "Profile saved" });
              } catch (error) {
                setProfileStatus({ loading: false, error: error instanceof Error ? error.message : "Could not save profile", success: "" });
              }
            }}
            onUploadPhoto={async (file) => {
              if (!me?.uid) return;
              setProfileStatus({ loading: true, error: "", success: "" });
              try {
                const cropped = await cropAvatarFile(file);
                const uploaded = await api.uploadAvatar(me.uid, cropped);
                setMe({ ...me, photoUrl: uploaded.photoUrl });
                setProfileStatus({ loading: false, error: "", success: "Profile photo updated" });
              } catch (error) {
                setProfileStatus({ loading: false, error: error instanceof Error ? error.message : "Could not upload photo", success: "" });
              }
            }}
            onRemovePhoto={async () => {
              if (!me?.uid) return;
              setProfileStatus({ loading: true, error: "", success: "" });
              try {
                const updated = await api.removeAvatar(me.uid, me);
                setMe({ ...updated, photoUrl: "" });
                setProfileStatus({ loading: false, error: "", success: "Profile photo removed" });
              } catch (error) {
                setProfileStatus({ loading: false, error: error instanceof Error ? error.message : "Could not remove photo", success: "" });
              }
            }}
          />
        ) : view === "settings" ? (
          <SettingsPage
            user={me}
            prefs={prefs}
            setPrefs={(next) => setPrefs((current) => ({ ...current, ...next }))}
            onBack={() => setView("messaging")}
            onEditProfile={() => setView("profile")}
            onLogout={() => {
              clearDesktopSession();
              setDesktopSessionToken("");
              setMe(null);
              setProfileLoadState("idle");
              void logout();
            }}
          />
        ) : view === "admin" && isAdminUser(me) ? (
          <AdminPanel user={me} onBack={() => setView("messaging")} />
        ) : activeThread ? (
          <>
            <ChatHeader
              thread={activeThread}
              presence={activePresence}
              onVoice={() => startCall("voice")}
              onVideo={() => startCall("video")}
              onOpenProfile={activeThread.kind === "chat" ? () => openProfileForThread(activeThread) : undefined}
              search={messageSearch}
              setSearch={setMessageSearch}
            />
            <div className="message-scroll" ref={messageScrollRef} onScroll={recomputeScrollState}>
              {activeThread.kind === "channel" ? (
                <ChannelTimeline
                  posts={activePosts}
                  onOpenImage={(url) => setViewer({ url, type: "image" })}
                  onOpenAuthor={(post) =>
                    openProfile({ uid: post.authorUid || "", name: post.authorName || "Admin", photoUrl: post.authorPhotoUrl })
                  }
                />
              ) : (
                <MessageTimeline
                  messages={searchedMessages}
                  currentUid={me?.uid || ""}
                  group={activeThread.kind === "group"}
                  selected={selectedMessages}
                  setSelected={setSelectedMessages}
                  unreadAt={messageSearch.trim() ? -1 : unreadDividerIndex}
                  unreadCount={unreadDividerCount}
                  onOpenImage={(url) => setViewer({ url, type: "image" })}
                  onOpenProfile={openProfileForMessage}
                  onAction={(action, message) => {
                    if (action === "profile") openProfileForMessage(message);
                    if (action === "reply") { setReplyTo(message); setEditingMessage(null); }
                    if (action === "forward") setForwardingMessage(message);
                    if (action === "edit") { setEditingMessage(message); setReplyTo(null); setComposer(message.text || ""); }
                    if (action === "copy") void copyMessage(message).then(() => showToast("Copied"));
                    if (action === "copyImage") {
                      void copyImageMessage(message)
                        .then(() => showToast("Image copied"))
                        .catch(() => showToast("Copy image is not supported on this device. Use Save image instead."));
                    }
                    if (action === "copyCaption") {
                      void copyCaption(message).then(() => showToast("Caption copied"));
                    }
                    if (action === "saveImage") void saveImageMessage(message).then(() => showToast("Saving image"));
                    if (action === "delete" && window.confirm("Delete this message for you?")) {
                      void api.delete(activeThread.id, message.id, "me", activeIsCommunity).then(() => showToast("Deleted"));
                    }
                    if (action === "pin") void api.pin(activeThread.id, message.id, true, activeIsCommunity).then(() => showToast("Pinned"));
                    if (action === "save") void api.star(activeThread.id, message.id, true, activeIsCommunity).then(() => showToast("Saved"));
                  }}
                />
              )}
            </div>
            {showScrollDown ? (
              <button className="scroll-down-fab" onClick={() => scrollMessagesToBottom(true)} aria-label="Scroll to latest messages">
                <AppIcon name="chevron-down" />
                {unreadBelow > 0 ? <span className="scroll-down-count">{unreadBelow > 99 ? "99+" : unreadBelow}</span> : null}
              </button>
            ) : null}
            {uploadProgress !== null ? <div className="upload-progress">Uploading {uploadProgress}%</div> : null}
            {activeThread.kind !== "channel" ? (
              <div className="composer-wrap">
                {replyTo ? <div className="composer-preview"><strong>Replying to {replyTo.senderName || "message"}</strong><span>{messagePreview(replyTo)}</span><button onClick={() => setReplyTo(null)}>Cancel</button></div> : null}
                {editingMessage ? <div className="composer-preview"><strong>Editing message</strong><span>{messagePreview(editingMessage)}</span><button onClick={() => { setEditingMessage(null); setComposer(""); }}>Cancel</button></div> : null}
              {showEmoji ? (
                <div className="emoji-popover" onMouseDown={(event) => event.preventDefault()}>
                  {EMOJIS.map((emoji) => (
                    <button type="button" key={emoji} onClick={() => insertEmoji(emoji)}>{emoji}</button>
                  ))}
                </div>
              ) : null}
              <div className="composer">
                <button type="button" title="Emoji" className={`composer-icon ${showEmoji ? "active" : ""}`} onClick={() => setShowEmoji((value) => !value)}><AppIcon name="smile" /></button>
                <textarea ref={composerRef} value={composer} onChange={(event) => setComposer(event.target.value)} onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    void sendText();
                  }
                  if (event.key === "Escape" && showEmoji) setShowEmoji(false);
                }} placeholder="Write a message" />
                <label title="Attach file">
                  <AppIcon name="plus" />
                  <input type="file" multiple hidden onChange={(event) => event.target.files && void uploadFiles(event.target.files)} />
                </label>
                <button onClick={() => void sendText()}>{editingMessage ? "Save" : "Send"}</button>
              </div>
              </div>
            ) : null}
          </>
        ) : (
          <div className="empty-state">Select a chat, group, or channel</div>
        )}
      </section>

      {forwardingMessage ? <ForwardPicker threads={threads} message={forwardingMessage} onOpenProfile={openProfileForThread} onClose={() => setForwardingMessage(null)} onForward={async (thread) => {
        const payload = { type: forwardingMessage.type, mediaUrl: forwardingMessage.mediaUrl, mediaMimeType: forwardingMessage.mediaMimeType, durationMs: forwardingMessage.durationMs, forwarded: true, forwardedFromMessageId: forwardingMessage.id, forwardedFromSenderName: forwardingMessage.senderName || "" };
        if (thread.kind === "group") await api.sendCommunityMessage(thread.id, messagePreview(forwardingMessage), payload);
        else if (thread.kind === "chat") await api.sendMessage(thread.id, messagePreview(forwardingMessage), payload);
        setForwardingMessage(null); showToast("Forwarded");
      }} /> : null}
      {toast ? <div className="desktop-toast">{toast}</div> : null}
      {viewer ? <ImageViewer viewer={viewer} onClose={() => setViewer(null)} /> : null}
      {profileTarget ? (
        <ProfilePanel
          target={profileTarget}
          isSelf={Boolean(me?.uid && profileTarget.uid === me.uid)}
          threads={threads}
          messagesByThread={messagesByThread}
          onClose={() => setProfileTarget(null)}
          onMessage={(uid) => void messageUser(uid)}
          onVoiceCall={(user) => callUser(user, "voice")}
          onVideoCall={(user) => callUser(user, "video")}
          onEditOwnProfile={() => {
            setProfileTarget(null);
            setView("profile");
          }}
          onOpenImage={(url) => setViewer({ url, type: "image" })}
          onToast={showToast}
        />
      ) : null}
      {callState && engineRef.current ? (
        <CallLayer engine={engineRef.current} state={callState} localStream={localStream} remoteStream={remoteStream} />
      ) : null}
      <SettingsStrip theme={theme} setTheme={setTheme} />
    </main>
  );
}

function QrLoginScreen({ error, setError, onApproved }: { error: string; setError: (value: string) => void; onApproved: (status: DesktopQrStatus) => void }) {
  const [session, setSession] = useState<DesktopQrSession | null>(null);
  const [status, setStatus] = useState<DesktopQrStatus["status"]>("pending");
  const [qrDataUrl, setQrDataUrl] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [loading, setLoading] = useState(true);
  // Email/password is an alternative to QR. It produces a real Firebase session,
  // which BOTH the main API and the community/wallet API accept — so groups and
  // channels load (the QR desktop token is rejected by the community API).
  const [mode, setMode] = useState<"qr" | "email">("qr");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [signingIn, setSigningIn] = useState(false);

  async function emailSignIn(event: React.FormEvent) {
    event.preventDefault();
    setSigningIn(true);
    setError("");
    try {
      // On success, observeAuth() in DesktopShell flips isAuthenticated and this
      // screen unmounts. No desktop token is stored, so getAuthToken() returns the
      // real Firebase ID token everywhere.
      await loginWithEmail(email, password);
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setSigningIn(false);
    }
  }

  const device = useMemo(() => {
    const nav = typeof navigator !== "undefined" ? navigator : null;
    const platform = nav?.platform || "Desktop";
    const os = platform.toLowerCase().includes("win") ? "Windows" : platform.toLowerCase().includes("mac") ? "macOS" : platform.toLowerCase().includes("linux") ? "Linux" : "Desktop";
    let deviceId = readCache("desktop:deviceId", "");
    if (!deviceId) {
      deviceId = `desktop_${crypto.randomUUID()}`;
      writeCache("desktop:deviceId", deviceId);
    }
    return { desktopDeviceId: deviceId, desktopDeviceName: `${os} Desktop`, desktopOs: os };
  }, []);

  const createSession = useCallback(async () => {
    setLoading(true);
    setError("");
    setQrDataUrl("");
    try {
      const next = await api.createDesktopQrSession(device);
      setSession(next);
      setStatus(next.status);
      setSecondsLeft(Math.max(0, Math.ceil((next.expiresAt - Date.now()) / 1000)));
      setQrDataUrl(await QRCode.toDataURL(next.qrPayload, { width: 280, margin: 2, color: { dark: "#07110b", light: "#ffffff" } }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not generate QR code");
    } finally {
      setLoading(false);
    }
  }, [device, setError]);

  useEffect(() => { void createSession(); }, [createSession]);

  useEffect(() => {
    if (!session) return;
    const timer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((session.expiresAt - Date.now()) / 1000));
      setSecondsLeft(left);
      if (left <= 0 && status !== "approved") setStatus("expired");
    }, 1000);
    return () => window.clearInterval(timer);
  }, [session, status]);

  useEffect(() => {
    if (!session || status === "approved" || status === "expired" || status === "rejected") return;
    const poll = window.setInterval(async () => {
      try {
        const next = await api.getDesktopQrStatus(session.qrSessionId);
        setStatus(next.status);
        if (next.status === "approved" && next.desktopToken) {
          onApproved(next);
          window.clearInterval(poll);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Could not check QR status");
      }
    }, 2000);
    return () => window.clearInterval(poll);
  }, [session, status, onApproved, setError]);

  const statusText = status === "scanned" ? "Waiting for confirmation..." : status === "approved" ? "Desktop connected" : status === "expired" ? "QR code expired. Generate a new code." : status === "rejected" ? "Login request rejected" : "Waiting for confirmation...";

  return (
    <main className="auth-shell qr-auth-shell">
      <section className="qr-auth-card">
        <div className="qr-auth-copy">
          <div className="brand-mark">A</div>
          <p className="eyebrow">{mode === "qr" ? "Secure device linking" : "Email sign in"}</p>
          <h1>Log in to AdisChat Desktop</h1>
          {mode === "qr" ? (
            <>
              <p>Open AdisChat on your phone and scan this QR code.</p>
              <ol>
                <li>Open AdisChat on your phone</li>
                <li>Go to Settings &gt; Linked Devices</li>
                <li>Tap Link Desktop</li>
                <li>Scan the QR code</li>
              </ol>
            </>
          ) : (
            <p>Sign in with your AdisChat email and password to load your chats, groups, and channels.</p>
          )}
        </div>

        {mode === "qr" ? (
          <div className="qr-card">
            <div className={`qr-frame ${loading ? "loading" : ""} ${status}`}>
              {qrDataUrl && status !== "expired" ? <img src={qrDataUrl} alt="AdisChat Desktop login QR code" /> : <div className="qr-skeleton" />}
            </div>
            <div className="qr-status-row">
              <span>{statusText}</span>
              <strong>{status === "expired" ? "Expired" : `${secondsLeft}s`}</strong>
            </div>
            {error ? <div className="error">{error}</div> : null}
            <button onClick={() => void createSession()} disabled={loading}>{loading ? "Generating..." : "Refresh QR code"}</button>
            <button className="auth-alt-link" onClick={() => { setError(""); setMode("email"); }}>Sign in with email instead</button>
          </div>
        ) : (
          <div className="qr-card">
            <form className="email-auth-form" onSubmit={emailSignIn}>
              <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" type="email" autoComplete="username" autoFocus />
              <input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Password" type="password" autoComplete="current-password" />
              {error ? <div className="error">{error}</div> : null}
              <button type="submit" disabled={signingIn || !email || !password}>{signingIn ? "Signing in..." : "Sign in"}</button>
            </form>
            <button className="auth-alt-link" onClick={() => { setError(""); setMode("qr"); }}>Use QR code instead</button>
          </div>
        )}
      </section>
    </main>
  );
}
/**
 * Human-readable summary of the last QR-link token exchange, shown in Settings >
 * About so the desktop-token vs. Firebase-session state is visible without
 * devtools. TODO(temporary): remove once QR login is confirmed in the field.
 */
function linkDiagnostic(): string {
  const diag = readCache<{ tokenShape?: { looksLikeFirebaseCustomToken?: boolean; jwt?: boolean; aud?: string }; exchange?: { ok?: boolean; errorCode?: string } } | null>("desktop:lastQrDiag", null);
  if (!diag) return "Email/password or unknown";
  if (diag.exchange?.ok) return "Firebase session (groups & channels OK)";
  const shape = diag.tokenShape?.looksLikeFirebaseCustomToken
    ? "custom-token"
    : diag.tokenShape?.jwt
      ? "JWT (not custom)"
      : "opaque token";
  return `Desktop token only - ${shape} (${diag.exchange?.errorCode || "no exchange"})`;
}

/**
 * Maps Firebase Auth errors to short, user-facing messages. Never surfaces raw
 * "Firebase: Error (auth/...)" / "internal error" technical strings.
 */
function friendlyAuthError(error: unknown): string {
  const code = (error as { code?: string })?.code || "";
  switch (code) {
    case "auth/missing-password":
      return "Please enter your password.";
    case "auth/missing-email":
    case "auth/invalid-email":
      return "Please enter a valid email.";
    case "auth/too-many-requests":
      return "Too many attempts. Please wait a moment and try again.";
    case "auth/network-request-failed":
      return "Network error. Check your connection and try again.";
    case "auth/user-disabled":
      return "This account has been disabled.";
    default:
      // invalid-credential / wrong-password / user-not-found and anything else
      // collapse to one clear message — no technical details.
      return "Wrong username or password.";
  }
}

function messagePreview(message?: Message | null) {
  if (!message) return "";
  const text = (message.text || "").trim();
  if (text) return text;
  if (message.type === "image") return "Photo";
  if (message.type === "video") return "Video";
  if (message.type === "voice") return "Voice message";
  if (message.mediaUrl) return "File";
  return "Message";
}

function ForwardPicker({ threads, message, onClose, onForward, onOpenProfile }: {
  threads: DesktopThread[];
  message: Message;
  onClose: () => void;
  onForward: (thread: DesktopThread) => Promise<void>;
  onOpenProfile: (thread: DesktopThread) => void;
}) {
  const [query, setQuery] = useState("");
  const [busyId, setBusyId] = useState("");
  const targets = threads.filter((thread) => {
    if (thread.kind === "channel") return false;
    const term = query.trim().toLowerCase();
    return !term || `${thread.title} ${thread.lastPreview || ""}`.toLowerCase().includes(term);
  });

  async function forwardTo(thread: DesktopThread) {
    setBusyId(thread.id);
    try {
      await onForward(thread);
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="forward-overlay" onClick={onClose}>
      <section className="forward-sheet" onClick={(event) => event.stopPropagation()}>
        <header>
          <div>
            <strong>Forward message</strong>
            <span>{messagePreview(message)}</span>
          </div>
          <button onClick={onClose} aria-label="Close">Close</button>
        </header>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search chats and groups" autoFocus />
        <div className="forward-list">
          {targets.map((thread) => (
            <button key={thread.id} onClick={() => void forwardTo(thread)} disabled={Boolean(busyId)}>
              <Avatar
                title={thread.title}
                src={thread.avatar}
                online={thread.online}
                onClick={thread.kind === "chat" ? () => onOpenProfile(thread) : undefined}
              />
              <span><strong>{thread.title}</strong><small>{thread.kind === "group" ? "Group" : "Private chat"}</small></span>
              <b>{busyId === thread.id ? "Sending" : "Forward"}</b>
            </button>
          ))}
          {!targets.length ? <p>No matching chats or groups</p> : null}
        </div>
      </section>
    </div>
  );
}
async function copyMessage(message: Message) {
  if (message.type === "image" && message.mediaUrl) throw new Error("Use copyImageMessage for images");
  const value = (message.text || "").trim();
  if (!value) return;
  await navigator.clipboard.writeText(value);
}

async function copyCaption(message: Message) {
  const value = (message.text || "").trim();
  if (!value) return;
  await navigator.clipboard.writeText(value);
}

async function copyImageMessage(message: Message) {
  if (message.type !== "image" || !message.mediaUrl) throw new Error("No image to copy");
  if (!navigator.clipboard?.write || typeof ClipboardItem === "undefined") {
    throw new Error("Image clipboard unsupported");
  }
  const response = await fetch(message.mediaUrl, { credentials: "include" });
  if (!response.ok) throw new Error("Image fetch failed");
  const blob = await response.blob();
  const mime = blob.type || message.mediaMimeType || "image/png";
  if (!["image/png", "image/jpeg", "image/webp"].includes(mime)) {
    throw new Error("Image type unsupported");
  }
  await navigator.clipboard.write([new ClipboardItem({ [mime]: blob })]);
}

async function saveImageMessage(message: Message) {
  if (message.type !== "image" || !message.mediaUrl) return;
  const response = await fetch(message.mediaUrl, { credentials: "include" });
  const blob = response.ok ? await response.blob() : null;
  const url = blob ? URL.createObjectURL(blob) : message.mediaUrl;
  const extension = (blob?.type || message.mediaMimeType || "image/jpeg").includes("png")
    ? "png"
    : (blob?.type || message.mediaMimeType || "image/jpeg").includes("webp")
      ? "webp"
      : "jpg";
  const link = document.createElement("a");
  link.href = url;
  link.download = `adischat-image-${Date.now()}.${extension}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  if (blob) URL.revokeObjectURL(url);
}

function Avatar({ title, src, online, onClick }: { title: string; src?: string; online?: boolean; onClick?: () => void }) {
  return (
    <span
      className={`avatar ${onClick ? "clickable" : ""}`}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-label={onClick ? `Open ${title} profile` : undefined}
      onClick={
        onClick
          ? (event) => {
              event.stopPropagation();
              onClick();
            }
          : undefined
      }
      onKeyDown={
        onClick
          ? (event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                event.stopPropagation();
                onClick();
              }
            }
          : undefined
      }
    >
      {src ? <img src={src} alt="" /> : avatarLabel(title)}
      {online ? <i /> : null}
    </span>
  );
}

function presenceLabel(presence: PresenceInfo | null) {
  if (presence?.online) return "Online";
  if (presence?.lastSeen) {
    const date = new Date(presence.lastSeen);
    const sameDay = date.toDateString() === new Date().toDateString();
    const time = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date);
    return sameDay ? `Last seen today at ${time}` : `Last seen ${new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date)}`;
  }
  return "Last seen recently";
}

function ChatHeader({ thread, presence, onVoice, onVideo, onOpenProfile, search, setSearch }: {
  thread: DesktopThread;
  presence: PresenceInfo | null;
  onVoice: () => void;
  onVideo: () => void;
  onOpenProfile?: () => void;
  search: string;
  setSearch: (v: string) => void;
}) {
  return (
    <header className="chat-header">
      <Avatar title={thread.title} src={thread.avatar} online={presence?.online || thread.online} onClick={onOpenProfile} />
      <div
        className={onOpenProfile ? "header-identity clickable" : "header-identity"}
        role={onOpenProfile ? "button" : undefined}
        tabIndex={onOpenProfile ? 0 : undefined}
        onClick={onOpenProfile}
        onKeyDown={
          onOpenProfile
            ? (event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  onOpenProfile();
                }
              }
            : undefined
        }
      >
        <h1>{thread.title}</h1>
        <p className={presence?.online ? "online" : ""}>
          {thread.kind === "channel" ? "Channel posts" : thread.kind === "group" ? "Group conversation" : presenceLabel(presence)}
        </p>
      </div>
      <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search inside chat" />
      {thread.kind === "chat" ? <button onClick={onVoice} aria-label="Voice call"><AppIcon name="phone" /></button> : null}
      {thread.kind === "chat" ? <button onClick={onVideo} aria-label="Video call"><AppIcon name="video" /></button> : null}
      <button aria-label="More options"><AppIcon name="more" /></button>
    </header>
  );
}

function actionLabel(action: string) {
  switch (action) {
    case "copyImage": return "Copy image";
    case "copyCaption": return "Copy caption";
    case "saveImage": return "Save image";
    default: return action;
  }
}

function MessageTimeline({ messages, currentUid, group, selected, setSelected, unreadAt = -1, unreadCount = 0, onOpenImage, onOpenProfile, onAction }: {
  messages: Message[];
  currentUid: string;
  group: boolean;
  selected: string[];
  setSelected: (next: string[]) => void;
  unreadAt?: number;
  unreadCount?: number;
  onOpenImage: (url: string) => void;
  onOpenProfile: (message: Message) => void;
  onAction: (action: string, message: Message) => void;
}) {
  return (
    <div className="messages">
      {messages.map((message, index) => {
        const mine = message.senderId === currentUid;
        const checked = selected.includes(message.id);
        const divider = unreadAt >= 0 && index === unreadAt && unreadCount > 0 ? (
          <div className="unread-divider"><span>{unreadCount} new message{unreadCount > 1 ? "s" : ""}</span></div>
        ) : null;
        if (message.type === "call") {
          return (
            <Fragment key={message.id}>
              {divider}
              <article className="message system-call">
                <span className="call-chip">
                  <AppIcon name="phone" />
                  {message.text || "Call"}
                  <small>{formatTime(message.timestamp)}</small>
                </span>
              </article>
            </Fragment>
          );
        }
        const isImage = message.type === "image" && Boolean(message.mediaUrl);
        const hasCaption = Boolean(message.text?.trim());
        const actions = isImage
          ? (mine
              ? ["reply", "forward", "copyImage", ...(hasCaption ? ["copyCaption"] : []), "saveImage", "delete", "pin", "save", "select"]
              : ["profile", "reply", "forward", "copyImage", ...(hasCaption ? ["copyCaption"] : []), "saveImage", "delete", "pin", "save", "select"])
          : (mine
              ? ["reply", "forward", "copy", "edit", "delete", "pin", "save", "select"]
              : ["profile", "reply", "forward", "copy", "delete", "pin", "save", "select"]);
        return (
          <Fragment key={message.id}>
          {divider}
          <article id={`msg-${message.id}`} className={`message ${mine ? "mine" : "theirs"} ${checked ? "selected" : ""}`} onClick={() => selected.length && setSelected(checked ? selected.filter((id) => id !== message.id) : [...selected, message.id])} onContextMenu={(event) => event.preventDefault()}>
            {group && !mine ? (
              <Avatar title={message.senderName || "A"} src={message.senderPhotoUrl} onClick={() => onOpenProfile(message)} />
            ) : null}
            <div className="bubble">
              {group && !mine ? (
                <b
                  className="sender-name"
                  role="button"
                  tabIndex={0}
                  onClick={(event) => {
                    event.stopPropagation();
                    onOpenProfile(message);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      event.stopPropagation();
                      onOpenProfile(message);
                    }
                  }}
                >
                  {message.senderName}
                </b>
              ) : null}
              {message.forwarded ? <em>Forwarded</em> : null}
              {message.type === "image" && message.mediaUrl ? <img src={message.mediaUrl} alt="" onClick={() => onOpenImage(message.mediaUrl!)} /> : null}
              {message.type === "video" && message.mediaUrl ? <video src={message.mediaUrl} controls /> : null}
              {message.type === "voice" && message.mediaUrl ? <audio src={message.mediaUrl} controls /> : null}
              {message.type === "file" || message.type === "document" ? <a href={message.mediaUrl} download>{message.text || "Download file"}</a> : null}
              {message.text ? <p>{message.text}</p> : null}
              <footer>
                <span>{formatTime(message.timestamp)}</span>
                {message.edited ? <span>edited</span> : null}
                {!group && mine ? <MessageStatus message={message} /> : null}
              </footer>
              <menu>
                {actions.map((action) => (
                  <button key={action} onClick={() => action === "select" ? setSelected([...selected, message.id]) : onAction(action, message)}>{actionLabel(action)}</button>
                ))}
              </menu>
            </div>
          </article>
          </Fragment>
        );
      })}
    </div>
  );
}

function ChannelTimeline({ posts, onOpenImage, onOpenAuthor }: { posts: ChannelPost[]; onOpenImage: (url: string) => void; onOpenAuthor: (post: ChannelPost) => void }) {
  return (
    <div className="channel-posts">
      {posts.map((post) => (
        <article key={post.id} className="channel-post">
          <header>
            <strong
              className={post.authorUid ? "author-link" : undefined}
              role={post.authorUid ? "button" : undefined}
              tabIndex={post.authorUid ? 0 : undefined}
              onClick={post.authorUid ? () => onOpenAuthor(post) : undefined}
              onKeyDown={
                post.authorUid
                  ? (event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onOpenAuthor(post);
                      }
                    }
                  : undefined
              }
            >
              {post.authorName || "Admin"}
            </strong>
            <span>Admin</span>
            <time>{formatTime(post.createdAt)}</time>
          </header>
          {post.title ? <h2>{post.title}</h2> : null}
          {post.mediaUrl && post.postType === "image" ? <img src={post.mediaUrl} alt="" onClick={() => onOpenImage(post.mediaUrl!)} /> : null}
          {post.mediaUrl && post.postType === "video" ? <video src={post.mediaUrl} controls /> : null}
          <p>{post.body}</p>
          <footer><span>{post.views || 0} views</span><span>{Object.keys(post.reactions || {}).length} reactions</span></footer>
        </article>
      ))}
    </div>
  );
}

function ImageViewer({ viewer, onClose }: { viewer: { url: string; type: string }; onClose: () => void }) {
  const [scale, setScale] = useState(1);
  const [editMode, setEditMode] = useState(false);
  const [cropEnabled, setCropEnabled] = useState(false);
  const [caption, setCaption] = useState("");
  const [emoji, setEmoji] = useState(String.fromCodePoint(0x2728));
  const [showEmoji, setShowEmoji] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function downloadImage() {
    setError("");
    if (!editMode && !cropEnabled && !caption.trim() && !showEmoji) {
      const link = document.createElement("a");
      link.href = viewer.url;
      link.download = "adischat-image";
      link.click();
      return;
    }

    setSaving(true);
    try {
      const blob = await renderEditedImage(viewer.url, {
        cropEnabled,
        caption: caption.trim(),
        emoji: showEmoji ? emoji : ""
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "adischat-edited-image.jpg";
      link.click();
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      setError(downloadError instanceof Error ? downloadError.message : "Could not export edited image");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="viewer"
      onWheel={(event) => setScale((value) => Math.min(5, Math.max(0.5, value + (event.deltaY > 0 ? -0.1 : 0.1))))}
      role="dialog"
      aria-modal="true"
      aria-label="Image viewer"
    >
      <header className="viewer-toolbar">
        <button className="viewer-icon-button" onClick={onClose} aria-label="Close image viewer">x</button>
        <div>
          <strong>{editMode ? "Edit image" : "Image preview"}</strong>
          <span>{editMode ? "Crop, add text, add emoji, then download" : "Scroll to zoom"}</span>
        </div>
        <div className="viewer-toolbar-actions">
          <button onClick={() => setScale((value) => Math.max(0.5, value - 0.25))}>-</button>
          <button onClick={() => setScale(1)}>{Math.round(scale * 100)}%</button>
          <button onClick={() => setScale((value) => Math.min(5, value + 0.25))}>+</button>
          <button className={editMode ? "active" : ""} onClick={() => setEditMode((value) => !value)}>Edit</button>
          <button className="primary" onClick={() => void downloadImage()} disabled={saving}>{saving ? "Saving..." : "Download"}</button>
        </div>
      </header>

      <div className="viewer-stage">
        <div className={`viewer-media-wrap ${cropEnabled ? "crop-active" : ""}`}>
          <img className="viewer-image" src={viewer.url} alt="" style={{ transform: `scale(${scale})` }} />
          {cropEnabled ? <span className="crop-frame" aria-hidden="true" /> : null}
          {caption.trim() ? <span className="viewer-text-overlay">{caption}</span> : null}
          {showEmoji ? <span className="viewer-emoji-overlay">{emoji}</span> : null}
        </div>
      </div>

      {editMode ? (
        <section className="viewer-edit-panel" aria-label="Image edit tools">
          <button className={cropEnabled ? "active" : ""} onClick={() => setCropEnabled((value) => !value)}>Crop</button>
          <label>
            <span>Text</span>
            <input value={caption} maxLength={72} onChange={(event) => setCaption(event.target.value)} placeholder="Add text" />
          </label>
          <label>
            <span>Emoji</span>
            <select value={emoji} onChange={(event) => { setEmoji(event.target.value); setShowEmoji(true); }}>
              {[String.fromCodePoint(0x2728), String.fromCodePoint(0x2764, 0xfe0f), String.fromCodePoint(0x1f602), String.fromCodePoint(0x1f525), String.fromCodePoint(0x2705), String.fromCodePoint(0x1f389), String.fromCodePoint(0x1f64f), String.fromCodePoint(0x2b50)].map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <button className={showEmoji ? "active" : ""} onClick={() => setShowEmoji((value) => !value)}>Emoji</button>
          <button onClick={() => { setCropEnabled(false); setCaption(""); setShowEmoji(false); setScale(1); }}>Reset</button>
        </section>
      ) : null}
      {error ? <div className="viewer-error">{error}</div> : null}
    </div>
  );
}

async function renderEditedImage(src: string, edits: { cropEnabled: boolean; caption: string; emoji: string }) {
  const response = await fetch(src, { mode: "cors" });
  if (!response.ok) throw new Error("Could not load image for editing");
  const blob = await response.blob();
  const bitmap = await createImageBitmap(blob);
  const sourceSize = edits.cropEnabled ? Math.min(bitmap.width, bitmap.height) : undefined;
  const sx = edits.cropEnabled ? Math.floor((bitmap.width - sourceSize!) / 2) : 0;
  const sy = edits.cropEnabled ? Math.floor((bitmap.height - sourceSize!) / 2) : 0;
  const sw = edits.cropEnabled ? sourceSize! : bitmap.width;
  const sh = edits.cropEnabled ? sourceSize! : bitmap.height;
  const maxSide = 2200;
  const ratio = Math.min(1, maxSide / Math.max(sw, sh));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(sw * ratio));
  canvas.height = Math.max(1, Math.round(sh * ratio));
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Image editor is not available");
  ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);

  if (edits.caption || edits.emoji) {
    const padding = Math.max(18, Math.round(canvas.width * 0.035));
    ctx.textAlign = "center";
    ctx.shadowColor = "rgba(0, 0, 0, 0.55)";
    ctx.shadowBlur = 18;
    ctx.fillStyle = "#ffffff";
    ctx.strokeStyle = "rgba(0, 0, 0, 0.55)";
    ctx.lineWidth = Math.max(3, Math.round(canvas.width * 0.004));

    if (edits.emoji) {
      const emojiSize = Math.max(44, Math.round(canvas.width * 0.085));
      ctx.font = `${emojiSize}px "Segoe UI Emoji", "Apple Color Emoji", sans-serif`;
      ctx.fillText(edits.emoji, canvas.width / 2, padding + emojiSize);
    }

    if (edits.caption) {
      const fontSize = Math.max(28, Math.round(canvas.width * 0.045));
      ctx.font = `800 ${fontSize}px Inter, system-ui, sans-serif`;
      const y = canvas.height - padding - fontSize * 0.2;
      ctx.strokeText(edits.caption, canvas.width / 2, y);
      ctx.fillText(edits.caption, canvas.width / 2, y);
    }
  }

  return await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((value) => (value ? resolve(value) : reject(new Error("Could not export edited image"))), "image/jpeg", 0.92)
  );
}

async function cropAvatarFile(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file);
  const size = Math.min(bitmap.width, bitmap.height);
  const sx = Math.floor((bitmap.width - size) / 2);
  const sy = Math.floor((bitmap.height - size) / 2);
  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 512;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Image crop is not available");
  ctx.drawImage(bitmap, sx, sy, size, size, 0, 0, 512, 512);
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((value) => (value ? resolve(value) : reject(new Error("Could not crop image"))), "image/jpeg", 0.9)
  );
  return new File([blob], "avatar.jpg", { type: "image/jpeg" });
}

function validateProfile(values: Partial<User>) {
  const name = (values.name || "").trim();
  const username = (values.username || "").trim();
  const bio = (values.bio || values.about || "").trim();
  if (name.length < 2) return "Display name must be at least 2 characters";
  if (username && !/^[a-zA-Z0-9_]{3,24}$/.test(username)) return "Username must be 3-24 letters, numbers, or underscores";
  if (bio.length > 160) return "Bio must be 160 characters or less";
  return "";
}

function AdminPanel({ user, onBack }: { user: User | null; onBack: () => void }) {
  const [stats, setStats] = useState<AdminStats>({});
  const [users, setUsers] = useState<User[]>([]);
  const [verifications, setVerifications] = useState<AdminVerificationRequest[]>([]);
  const [channels, setChannels] = useState<AdminChannelVerification[]>([]);
  const [games, setGames] = useState<AdminGameApproval[]>([]);
  const [reports, setReports] = useState<AdminReport[]>([]);
  const [logs, setLogs] = useState<AdminReport[]>([]);
  const [status, setStatus] = useState("pending");
  const [query, setQuery] = useState("");
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const loadAdmin = useCallback(async () => {
    setBusy(true);
    setError("");
    const [statsRes, usersRes, verRes, channelRes, gameRes, reportRes, logRes] = await Promise.allSettled([
      api.getAdminStats(),
      api.getAdminUsers(query, 0),
      api.getAdminVerifications(status),
      api.getAdminChannelVerifications(status),
      api.getAdminGameApprovals(status),
      api.getAdminReports(),
      api.getAdminAuditLogs()
    ]);
    if (statsRes.status === "fulfilled") setStats(statsRes.value);
    if (usersRes.status === "fulfilled") setUsers(usersRes.value);
    if (verRes.status === "fulfilled") setVerifications(verRes.value);
    if (channelRes.status === "fulfilled") setChannels(channelRes.value);
    if (gameRes.status === "fulfilled") setGames(gameRes.value);
    if (reportRes.status === "fulfilled") setReports(reportRes.value);
    if (logRes.status === "fulfilled") setLogs(logRes.value);
    const failed = [statsRes, usersRes, verRes, channelRes, gameRes].find((res) => res.status === "rejected") as PromiseRejectedResult | undefined;
    if (failed) {
      const message = failed.reason instanceof Error ? failed.reason.message : "Could not load one admin section";
      setError(/admin only|forbidden|403/i.test(message) ? "Admin access required by server. Make sure this desktop session belongs to the admin account, then sign out and link/sign in again." : message);
    }
    setBusy(false);
  }, [query, status]);

  useEffect(() => {
    void loadAdmin();
    const timer = window.setInterval(() => void loadAdmin(), 5000);
    return () => window.clearInterval(timer);
  }, [loadAdmin]);

  async function confirmAction(label: string, action: () => Promise<unknown>) {
    if (!window.confirm(label)) return;
    setBusy(true);
    setNotice("");
    setError("");
    try {
      await action();
      setNotice("Admin action completed");
      await loadAdmin();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Admin action failed");
    } finally {
      setBusy(false);
    }
  }

  async function runUserAction(label: string, action: () => Promise<unknown>, success: string) {
    await confirmAction(label, async () => {
      await action();
      setNotice(success);
      await loadAdmin();
      if (selectedUser?.uid) {
        const fresh = await api.getUser(selectedUser.uid).catch(() => null);
        if (fresh) setSelectedUser(fresh as User);
      }
    });
  }

  const visibleUsers = users.filter((item) => {
    const term = query.trim().toLowerCase();
    return !term || `${item.name} ${item.username} ${item.email} ${item.phone}`.toLowerCase().includes(term);
  });

  return (
    <div className="detail-page admin-page">
      <PageTop title="Admin Panel" subtitle={`Secure review workspace - ${user?.name || "Admin"}`} onBack={onBack} />
      <div className="admin-toolbar">
        <div className="search-box admin-search">
          <AppIcon name="search" />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search users, requests, channels" />
        </div>
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="pending">Pending</option>
          <option value="approved">Approved</option>
          <option value="rejected">Rejected</option>
          <option value="all">All</option>
        </select>
        <button className="soft-button" disabled={busy} onClick={() => void loadAdmin()}>{busy ? "Syncing" : "Sync"}</button>
      </div>
      {notice ? <div className="success floating">{notice}</div> : null}
      {error ? <div className="error admin-alert">{error}</div> : null}

      <section className="admin-metrics">
        <AdminMetric label="Users" value={stats.totalUsers || visibleUsers.length} />
        <AdminMetric label="Pending IDs" value={stats.pendingVerifications || verifications.length} tone="green" />
        <AdminMetric label="Blue badges" value={stats.blueBadge || 0} tone="blue" />
        <AdminMetric label="Reports" value={reports.length} tone="red" />
      </section>

      <div className="admin-grid">
        <AdminTable title="User Verification Requests" columns={["Applicant", "Document", "Status", "Action"]}>
          {verifications.map((item) => {
            const uid = item.uid || (item as any).user_uid || "";
            const name = item.fullName || (item as any).full_name || item.userName || (item as any).user_name || uid;
            return (
              <tr key={`verify-${uid}`}>
                <td><strong>{name}</strong><span>{item.userEmail || (item as any).user_email || ""}</span></td>
                <td>{item.documentType || (item as any).document_type || "Identity"}</td>
                <td><StatusPill status={item.status || "pending"} /></td>
                <td><AdminActions onApprove={() => confirmAction(`Approve verification for ${name}?`, () => api.approveAdminVerification(uid))} onReject={() => confirmAction(`Reject verification for ${name}?`, () => api.rejectAdminVerification(uid, "Rejected from desktop admin"))} /></td>
              </tr>
            );
          })}
          {!verifications.length ? <EmptyAdminRow cols={4} label="No verification requests" /> : null}
        </AdminTable>

        <AdminTable title="Channel Verification Requests" columns={["Channel", "Owner", "Status", "Action"]}>
          {channels.map((item) => {
            const channelId = item.channelId || (item as any).channel_id || "";
            const name = item.channelName || (item as any).channel_name || channelId;
            return (
              <tr key={`channel-${channelId}`}>
                <td><strong>{name}</strong><span>{item.note || "Blue badge request"}</span></td>
                <td>{item.ownerUid || (item as any).owner_uid || "Owner"}</td>
                <td><StatusPill status={item.status || "pending"} /></td>
                <td><AdminActions onApprove={() => confirmAction(`Approve channel badge for ${name}?`, () => api.approveAdminChannelVerification(channelId))} onReject={() => confirmAction(`Reject channel badge for ${name}?`, () => api.rejectAdminChannelVerification(channelId, "Rejected from desktop admin"))} /></td>
              </tr>
            );
          })}
          {!channels.length ? <EmptyAdminRow cols={4} label="No channel requests" /> : null}
        </AdminTable>

        <AdminTable title="Group Approval Requests" columns={["Group/Channel", "Owner", "Status", "Action"]}>
          {games.map((item) => {
            const channelId = item.channelId || (item as any).channel_id || "";
            const name = item.channelName || (item as any).channel_name || channelId;
            return (
              <tr key={`game-${channelId}`}>
                <td><strong>{name}</strong><span>{item.description || "Game/group approval"}</span></td>
                <td>{item.ownerUid || (item as any).owner_uid || "Owner"}</td>
                <td><StatusPill status={item.status || "pending"} /></td>
                <td><AdminActions onApprove={() => confirmAction(`Approve request for ${name}?`, () => api.approveAdminGameApproval(channelId, "Approved from desktop admin"))} onReject={() => confirmAction(`Reject request for ${name}?`, () => api.rejectAdminGameApproval(channelId, "Rejected from desktop admin"))} /></td>
              </tr>
            );
          })}
          {!games.length ? <EmptyAdminRow cols={4} label="No group approval requests" /> : null}
        </AdminTable>

        <AdminTable title="Reported Content Review" columns={["Reason", "Thread", "Reporter", "Status"]}>
          {reports.map((item) => (
            <tr key={item.id}>
              <td><strong>{item.reason || "Report"}</strong><span>{item.details || "No details"}</span></td>
              <td>{item.threadType || "chat"} / {item.threadId || "-"}</td>
              <td>{item.reporterUid || "-"}</td>
              <td><StatusPill status={item.status || "open"} /></td>
            </tr>
          ))}
          {!reports.length ? <EmptyAdminRow cols={4} label="No reported content returned by backend" /> : null}
        </AdminTable>

        <AdminTable title="Users" columns={["User", "Email", "Role", "Status", "Actions"]}>
          {visibleUsers.slice(0, 30).map((item) => (
            <tr key={item.uid} className={`admin-user-row ${selectedUser?.uid === item.uid ? "selected" : ""}`} onClick={() => setSelectedUser(item)}>
              <td><strong>{item.name || item.username || item.uid}</strong><span>@{item.username || "no_username"}</span></td>
              <td>{item.email || "No email"}</td>
              <td>{item.role || "user"}</td>
              <td><StatusPill status={item.deleted ? "deleted" : item.paused || item.disabled ? "paused" : (item.verificationLevel || 0) >= 3 ? "verified" : "active"} /></td>
              <td>
                <button className="admin-row-action" onClick={(event) => { event.stopPropagation(); setSelectedUser(item); }}>
                  Manage
                </button>
              </td>
            </tr>
          ))}
          {!visibleUsers.length ? <EmptyAdminRow cols={5} label="No users found" /> : null}
        </AdminTable>

        <AdminTable title="Activity Logs / Audit Trail" columns={["Event", "Actor", "Target", "Time"]}>
          {logs.map((item) => (
            <tr key={item.id}>
              <td><strong>{item.reason || "Admin event"}</strong><span>{item.details || "Audit entry"}</span></td>
              <td>{item.reporterUid || "-"}</td>
              <td>{item.reportedUid || item.threadId || "-"}</td>
              <td>{formatDate(item.createdAt)}</td>
            </tr>
          ))}
          {!logs.length ? <EmptyAdminRow cols={4} label="Audit endpoint not enabled yet" /> : null}
        </AdminTable>
      </div>
      {selectedUser ? (
        <AdminUserDrawer
          user={selectedUser}
          currentAdminUid={user?.uid || ""}
          busy={busy}
          onClose={() => setSelectedUser(null)}
          onGrantBadge={() => runUserAction(`Give blue badge to ${selectedUser.name || selectedUser.username || selectedUser.uid}?`, () => api.grantAdminBlueBadge(selectedUser.uid), "Blue badge granted")}
          onRemoveBadge={() => runUserAction(`Remove blue badge from ${selectedUser.name || selectedUser.username || selectedUser.uid}?`, () => api.removeAdminBlueBadge(selectedUser.uid), "Blue badge removed")}
          onPause={() => runUserAction(`Pause ${selectedUser.name || selectedUser.username || selectedUser.uid}? They will lose access until reactivated.`, () => api.setAdminUserStatus(selectedUser.uid, "paused"), "User paused")}
          onReactivate={() => runUserAction(`Reactivate ${selectedUser.name || selectedUser.username || selectedUser.uid}?`, () => api.setAdminUserStatus(selectedUser.uid, "active"), "User reactivated")}
          onMakeAdmin={() => runUserAction(`Give admin role to ${selectedUser.name || selectedUser.username || selectedUser.uid}?`, () => api.setAdminUserRole(selectedUser.uid, "admin"), "Admin role granted")}
          onRemoveAdmin={() => runUserAction(`Remove admin role from ${selectedUser.name || selectedUser.username || selectedUser.uid}?`, () => api.setAdminUserRole(selectedUser.uid, "user"), "Admin role removed")}
          onDelete={() => runUserAction(`Delete ${selectedUser.name || selectedUser.username || selectedUser.uid}? This is permanent.`, () => api.deleteAdminUser(selectedUser.uid), "User deleted")}
        />
      ) : null}
    </div>
  );
}

function AdminMetric({ label, value, tone = "default" }: { label: string; value: number; tone?: "default" | "green" | "blue" | "red" }) {
  return <div className={`admin-metric ${tone}`}><span>{label}</span><strong>{value}</strong></div>;
}

function AdminUserDrawer({
  user,
  currentAdminUid,
  busy,
  onClose,
  onGrantBadge,
  onRemoveBadge,
  onPause,
  onReactivate,
  onMakeAdmin,
  onRemoveAdmin,
  onDelete
}: {
  user: User;
  currentAdminUid: string;
  busy: boolean;
  onClose: () => void;
  onGrantBadge: () => void;
  onRemoveBadge: () => void;
  onPause: () => void;
  onReactivate: () => void;
  onMakeAdmin: () => void;
  onRemoveAdmin: () => void;
  onDelete: () => void;
}) {
  const verified = Boolean(user.verified || (user.verificationLevel || 0) >= 3);
  const paused = Boolean(user.paused || user.disabled || user.status === "paused");
  const admin = isAdminUser(user);
  const self = user.uid === currentAdminUid;
  const title = user.name || user.username || user.uid || "AdisChat user";

  return (
    <aside className="admin-user-drawer" aria-label="User admin actions">
      <header>
        <div className="admin-user-avatar">{user.photoUrl ? <img src={user.photoUrl} alt="" /> : <span>{avatarLabel(title)}</span>}</div>
        <div>
          <h3>{title}</h3>
          <p>@{user.username || "no_username"} · {user.email || "No email"}</p>
          <div className="admin-user-pills">
            <StatusPill status={admin ? "admin" : user.role || "user"} />
            <StatusPill status={paused ? "paused" : user.deleted ? "deleted" : "active"} />
            <StatusPill status={verified ? "verified" : "not verified"} />
          </div>
        </div>
        <button className="admin-drawer-close" onClick={onClose} aria-label="Close user actions">x</button>
      </header>

      <div className="admin-user-details">
        <InfoRow label="User ID" value={user.uid || "-"} />
        <InfoRow label="Phone" value={user.phone || "Not available"} />
        <InfoRow label="Joined" value={formatDate(user.joinedAt || user.createdAt)} />
        <InfoRow label="Last seen" value={user.lastSeen ? formatDate(user.lastSeen) : "Not available"} />
      </div>

      <div className="admin-user-action-grid">
        {verified ? (
          <button disabled={busy} onClick={onRemoveBadge}>Remove blue badge</button>
        ) : (
          <button disabled={busy} onClick={onGrantBadge}>Give blue badge</button>
        )}
        {paused ? (
          <button disabled={busy} onClick={onReactivate}>Reactivate user</button>
        ) : (
          <button disabled={busy} onClick={onPause}>Pause user</button>
        )}
        {admin ? (
          <button disabled={busy || self} onClick={onRemoveAdmin}>Remove admin</button>
        ) : (
          <button disabled={busy} onClick={onMakeAdmin}>Make admin</button>
        )}
        <button className="danger" disabled={busy || self} onClick={onDelete}>Delete user</button>
      </div>
      {self ? <p className="admin-user-note">You cannot remove admin access or delete your current desktop account.</p> : null}
    </aside>
  );
}

function StatusPill({ status }: { status: string }) {
  return <span className={`status-pill ${status.toLowerCase()}`}>{status}</span>;
}

function AdminActions({ onApprove, onReject }: { onApprove: () => void; onReject: () => void }) {
  return <div className="admin-actions"><button onClick={onApprove}>Approve</button><button className="danger" onClick={onReject}>Reject</button></div>;
}

function AdminTable({ title, columns, children }: { title: string; columns: string[]; children: React.ReactNode }) {
  return (
    <section className="admin-table-card">
      <header><h3>{title}</h3><span>{columns.length} fields</span></header>
      <div className="admin-table-wrap">
        <table>
          <thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </section>
  );
}

function EmptyAdminRow({ cols, label }: { cols: number; label: string }) {
  return <tr><td colSpan={cols} className="admin-empty">{label}</td></tr>;
}

function ProfilePage({
  user,
  firebaseUser,
  prefs,
  status,
  loadState,
  loadError,
  onRetry,
  onBack,
  onPrefsChange,
  onSave,
  onUploadPhoto,
  onRemovePhoto
}: {
  user: User | null;
  firebaseUser: FirebaseUser | null;
  prefs: DesktopPreferences;
  status: { loading: boolean; error: string; success: string };
  loadState: "idle" | "loading" | "loaded" | "error";
  loadError: string;
  onRetry: () => void;
  onBack: () => void;
  onPrefsChange: (patch: Partial<DesktopPreferences>) => void;
  onSave: (patch: Partial<User>) => Promise<void>;
  onUploadPhoto: (file: File) => Promise<void>;
  onRemovePhoto: () => Promise<void>;
}) {
  const [values, setValues] = useState<Partial<User>>({});
  const [localError, setLocalError] = useState("");
  const hasProfile = Boolean(user?.uid);
  const admin = isAdminUser(user);

  useEffect(() => {
    setValues({
      name: user?.name || firebaseUser?.displayName || "",
      username: user?.username || "",
      phone: user?.phone || "",
      email: user?.email || firebaseUser?.email || "",
      bio: user?.bio || user?.about || "",
      language: user?.language || prefs.language,
      themePreference: prefs.theme
    });
  }, [firebaseUser?.displayName, firebaseUser?.email, prefs.language, prefs.theme, user]);

  // No profile yet: show a skeleton while loading, or an error + retry on failure.
  // Never fall through to an empty "AdisChat user" form. (Placed after all hooks so
  // hook order stays stable across the loading -> loaded transition.)
  if (!hasProfile && loadState !== "loaded") {
    return (
      <div className="detail-page">
        <PageTop title="Profile" subtitle="Real account data from AdisChat" onBack={onBack} />
        {loadState === "error" ? (
          <section className="profile-load-state error">
            <h2>Couldn&apos;t load your profile</h2>
            <p>{loadError || "Please check your connection and try again."}</p>
            <button className="primary-action" onClick={onRetry}>Retry</button>
          </section>
        ) : (
          <section className="profile-load-state">
            <div className="profile-hero skeleton">
              <span className="avatar skeleton-box" />
              <div>
                <span className="skeleton-line wide" />
                <span className="skeleton-line" />
              </div>
            </div>
            <p className="profile-load-hint">Loading your profile...</p>
          </section>
        )}
      </div>
    );
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const validation = validateProfile(values);
    setLocalError(validation);
    if (validation) return;
    await onSave(values);
    onPrefsChange({ language: values.language === "am" ? "am" : "en", theme: (values.themePreference as ThemeMode) || prefs.theme });
  }

  return (
    <div className="detail-page">
      <PageTop title="Profile" subtitle="Real account data from AdisChat" onBack={onBack} />
      {loadError && hasProfile ? (
        <div className="profile-refresh-warning">
          <span>Showing your saved profile - couldn&apos;t refresh ({loadError}).</span>
          <button onClick={onRetry}>Retry</button>
        </div>
      ) : null}
      <section className="profile-hero">
        <Avatar title={values.name || "A"} src={user?.photoUrl || firebaseUser?.photoURL || ""} online />
        <div>
          <h2>{values.name || user?.name || "AdisChat user"}{admin ? <span className="admin-badge">Admin</span> : null}</h2>
          <p>{values.username ? `@${values.username}` : "No username yet"}</p>
          <span>Online now</span>
        </div>
        <label className="soft-button">
          Upload photo
          <input type="file" accept="image/*" hidden onChange={(event) => event.target.files?.[0] && void onUploadPhoto(event.target.files[0])} />
        </label>
        <button className="ghost-button" onClick={() => void onRemovePhoto()}>Remove</button>
      </section>

      <form className="settings-grid" onSubmit={submit}>
        <div className="settings-card wide">
          <h3>Edit Profile</h3>
          <Field label="Display name" value={values.name || ""} onChange={(name) => setValues((v) => ({ ...v, name }))} />
          <Field label="Username" value={values.username || ""} prefix="@" onChange={(username) => setValues((v) => ({ ...v, username }))} />
          <Field label="Phone" value={values.phone || ""} onChange={(phone) => setValues((v) => ({ ...v, phone }))} />
          <Field label="Email" value={values.email || ""} onChange={(email) => setValues((v) => ({ ...v, email }))} />
          <label className="field">
            <span>Bio/About</span>
            <textarea value={values.bio || ""} maxLength={160} onChange={(event) => setValues((v) => ({ ...v, bio: event.target.value, about: event.target.value }))} />
          </label>
          {(localError || status.error) ? <div className="error">{localError || status.error}</div> : null}
          {status.success ? <div className="success">{status.success}</div> : null}
          <button className="primary-action" disabled={status.loading}>{status.loading ? "Saving..." : "Save profile"}</button>
        </div>

        <div className="settings-card">
          <h3>Account</h3>
          <InfoRow label="Full name" value={user?.name || firebaseUser?.displayName || "Not set"} />
          <InfoRow label="Username" value={user?.username ? `@${user.username}` : "Not set"} />
          <InfoRow label="Phone" value={user?.phone || "Not available"} />
          <InfoRow label="Email" value={user?.email || firebaseUser?.email || "Not available"} />
          <InfoRow label="Joined" value={formatDate(user?.joinedAt || user?.createdAt || firebaseUser?.metadata.creationTime)} />
          <InfoRow label="Last seen" value={user?.lastSeen ? formatDate(user.lastSeen) : "Online now"} />
          <InfoRow label="Role" value={admin ? `${user?.role || "Admin"} (admin access)` : user?.role || "Member"} />
        </div>

        <div className="settings-card">
          <h3>Preferences</h3>
          <SelectField label="Language" value={values.language || prefs.language} options={[["en", "English"], ["am", "Amharic"]]} onChange={(language) => setValues((v) => ({ ...v, language }))} />
          <SelectField label="Theme" value={values.themePreference || prefs.theme} options={[["dark", "Dark"], ["light", "Light"], ["system", "System"]]} onChange={(themePreference) => setValues((v) => ({ ...v, themePreference }))} />
        </div>
      </form>
    </div>
  );
}

function SettingsPage({
  user,
  prefs,
  setPrefs,
  onBack,
  onEditProfile,
  onLogout
}: {
  user: User | null;
  prefs: DesktopPreferences;
  setPrefs: (patch: Partial<DesktopPreferences>) => void;
  onBack: () => void;
  onEditProfile: () => void;
  onLogout: () => void;
}) {
  const [notice, setNotice] = useState("");
  const cacheBytes = typeof window === "undefined"
    ? 0
    : Object.keys(localStorage).reduce((sum, key) => sum + key.length + (localStorage.getItem(key)?.length || 0), 0);

  function clearCache() {
    ["desktop:messages", "desktop:posts", "desktop:threads"].forEach((key) => localStorage.removeItem(key));
    setNotice("Cached conversations cleared");
  }

  return (
    <div className="detail-page">
      <PageTop title="Settings" subtitle="Desktop settings connected to this client" onBack={onBack} />
      {notice ? <div className="success floating">{notice}</div> : null}
      <div className="settings-grid">
        <SettingsCard title="Account">
          <ActionRow label="Edit profile" value={user?.name || "Profile"} onClick={onEditProfile} />
          <ActionRow label="Change username" value={user?.username ? `@${user.username}` : "Not set"} onClick={onEditProfile} />
          <ActionRow label="Active sessions/devices" value="Current desktop session" />
          <ActionRow label="Logout" danger onClick={onLogout} />
        </SettingsCard>

        <SettingsCard title="Privacy">
          <SelectSetting label="Last seen & online" value={prefs.lastSeenVisibility} options={["everyone", "contacts", "nobody"]} onChange={(lastSeenVisibility) => setPrefs({ lastSeenVisibility: lastSeenVisibility as DesktopPreferences["lastSeenVisibility"] })} />
          <SelectSetting label="Profile photo visibility" value={prefs.profilePhotoVisibility} options={["everyone", "contacts", "nobody"]} onChange={(profilePhotoVisibility) => setPrefs({ profilePhotoVisibility: profilePhotoVisibility as DesktopPreferences["profilePhotoVisibility"] })} />
          <SelectSetting label="Who can message me" value={prefs.whoCanMessageMe} options={["everyone", "contacts"]} onChange={(whoCanMessageMe) => setPrefs({ whoCanMessageMe: whoCanMessageMe as DesktopPreferences["whoCanMessageMe"] })} />
          <ActionRow label="Blocked users" value="Manage in backend when endpoint is enabled" />
          <ToggleRow label="Read receipts" checked={prefs.readReceipts} onChange={(readReceipts) => setPrefs({ readReceipts })} />
        </SettingsCard>

        <SettingsCard title="Calls & Devices">
          <CallDeviceSettings />
        </SettingsCard>

        <SettingsCard title="Notifications">
          <ToggleRow label="Desktop notifications" checked={prefs.notificationsEnabled} onChange={async (notificationsEnabled) => { if (notificationsEnabled) await requestNotificationPermission(); setPrefs({ notificationsEnabled }); }} />
          <ToggleRow label="Message notifications" checked={prefs.messageNotifications} onChange={(messageNotifications) => setPrefs({ messageNotifications })} />
          <ToggleRow label="Group notifications" checked={prefs.groupNotifications} onChange={(groupNotifications) => setPrefs({ groupNotifications })} />
          <ToggleRow label="Channel notifications" checked={prefs.channelNotifications} onChange={(channelNotifications) => setPrefs({ channelNotifications })} />
          <ToggleRow label="Sound" checked={prefs.sound} onChange={(sound) => setPrefs({ sound })} />
          <ToggleRow label="Notification preview" checked={prefs.notificationPreview} onChange={(notificationPreview) => setPrefs({ notificationPreview })} />
        </SettingsCard>

        <SettingsCard title="Appearance">
          <SelectSetting label="Theme" value={prefs.theme} options={["dark", "light", "system"]} onChange={(theme) => setPrefs({ theme: theme as ThemeMode })} />
          <SelectSetting label="Font size" value={prefs.fontSize} options={["small", "normal", "large"]} onChange={(fontSize) => setPrefs({ fontSize: fontSize as DesktopPreferences["fontSize"] })} />
          <SelectSetting label="Chat background" value={prefs.chatBackground} options={["clean", "soft", "solid"]} onChange={(chatBackground) => setPrefs({ chatBackground: chatBackground as DesktopPreferences["chatBackground"] })} />
          <SelectSetting label="Bubble style" value={prefs.bubbleStyle} options={["modern", "compact"]} onChange={(bubbleStyle) => setPrefs({ bubbleStyle: bubbleStyle as DesktopPreferences["bubbleStyle"] })} />
        </SettingsCard>

        <SettingsCard title="Chat Settings">
          <ToggleRow label="Enter to send" checked={prefs.enterToSend} onChange={(enterToSend) => setPrefs({ enterToSend })} />
          <ToggleRow label="Media auto-download" checked={prefs.mediaAutoDownload} onChange={(mediaAutoDownload) => setPrefs({ mediaAutoDownload })} />
          <Field label="Download folder" value={prefs.downloadFolder} onChange={(downloadFolder) => setPrefs({ downloadFolder })} />
          <ToggleRow label="Link previews" checked={prefs.linkPreviews} onChange={(linkPreviews) => setPrefs({ linkPreviews })} />
          <ToggleRow label="Emoji/sticker suggestions" checked={prefs.emojiSuggestions} onChange={(emojiSuggestions) => setPrefs({ emojiSuggestions })} />
        </SettingsCard>

        <SettingsCard title="Storage">
          <InfoRow label="Cache size" value={`${Math.max(1, Math.round(cacheBytes / 1024))} KB`} />
          <ActionRow label="Clear cache" onClick={clearCache} />
          <ToggleRow label="Auto-delete temporary files" checked={prefs.autoDeleteTemp} onChange={(autoDeleteTemp) => setPrefs({ autoDeleteTemp })} />
          <InfoRow label="Downloaded files location" value={prefs.downloadFolder} />
        </SettingsCard>

        <SettingsCard title="Security">
          <ToggleRow label="App lock" checked={prefs.appLock} onChange={(appLock) => setPrefs({ appLock })} />
          <ActionRow label="PIN / biometrics" value="Uses OS support when enabled" />
          <ActionRow label="Two-step verification" value="Backend placeholder" />
          <ActionRow label="Active devices" value="Current desktop session" />
        </SettingsCard>

        <SettingsCard title="Language">
          <SelectSetting label="App language" value={prefs.language} options={["en", "am"]} onChange={(language) => setPrefs({ language: language as DesktopPreferences["language"] })} />
          <ActionRow label="Future languages" value="Ready" />
        </SettingsCard>

        <SettingsCard title="About">
          <InfoRow label="App version" value="1.2.5" />
          <InfoRow label="Link diagnostic" value={linkDiagnostic()} />
          <ActionRow label="Terms of Service" value="Open in browser" />
          <ActionRow label="Privacy Policy" value="Open in browser" />
          <ActionRow label="Community Guidelines" value="Open in browser" />
          <ActionRow label="Contact support" value="support@adischat.com" />
        </SettingsCard>
      </div>
    </div>
  );
}

function PageTop({ title, subtitle, onBack }: { title: string; subtitle: string; onBack: () => void }) {
  return (
    <header className="detail-top">
      <button onClick={onBack}>Back</button>
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
    </header>
  );
}

function Field({ label, value, onChange, prefix = "" }: { label: string; value: string; onChange: (value: string) => void; prefix?: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      <div className="input-wrap">{prefix ? <b>{prefix}</b> : null}<input value={value} onChange={(event) => onChange(event.target.value)} /></div>
    </label>
  );
}

function SelectField({ label, value, options, onChange }: { label: string; value: string; options: string[][]; onChange: (value: string) => void }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
      </select>
    </label>
  );
}

function SettingsCard({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="settings-card"><h3>{title}</h3>{children}</section>;
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="settings-row"><span>{label}</span><strong>{value}</strong></div>;
}

function ActionRow({ label, value = "", danger, onClick }: { label: string; value?: string; danger?: boolean; onClick?: () => void }) {
  return <button className={`settings-row action ${danger ? "danger" : ""}`} onClick={onClick}><span>{label}</span><strong>{value || "Open"}</strong></button>;
}

function ToggleRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return <label className="settings-row"><span>{label}</span><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} /></label>;
}

function SelectSetting({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <label className="settings-row">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

function SettingsStrip({ theme, setTheme }: { theme: ThemeMode; setTheme: (theme: ThemeMode) => void }) {
  return (
    <div className="settings-strip">
      {(["dark", "light", "system"] as ThemeMode[]).map((item) => (
        <button key={item} className={theme === item ? "active" : ""} onClick={() => setTheme(item)}>{item}</button>
      ))}
    </div>
  );
}
