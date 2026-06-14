export type ThreadKind = "chat" | "group" | "channel";
export type PresenceState = "online" | "last_seen" | "typing" | "recording" | "uploading" | "offline";

export type User = {
  uid: string;
  name: string;
  username?: string;
  photoUrl?: string;
  email?: string;
  phone?: string;
  dob?: string;
  bio?: string;
  about?: string;
  language?: string;
  themePreference?: string;
  lastSeen?: number;
  createdAt?: number;
  joinedAt?: number;
  role?: string;
  permissions?: string[];
  verified?: boolean;
  verificationLevel?: number;
};

/**
 * Raw user payload as returned by the backend. Field names vary by auth path
 * (Firebase vs. desktop QR session), so every known alias is optional here and
 * normalized into a `User` by `normalizeUser` in api.ts.
 */
export type RawUser = {
  uid?: string;
  userId?: string;
  id?: string;
  name?: string;
  displayName?: string;
  fullName?: string;
  full_name?: string;
  username?: string;
  handle?: string;
  photoUrl?: string;
  photo_url?: string;
  avatarUrl?: string;
  avatar_url?: string;
  profilePicture?: string;
  profile_picture?: string;
  email?: string;
  phone?: string;
  phoneNumber?: string;
  dob?: string;
  bio?: string;
  about?: string;
  language?: string;
  themePreference?: string;
  lastSeen?: number;
  last_seen?: number;
  createdAt?: number;
  created_at?: number;
  joinedAt?: number;
  joined_at?: number;
  role?: string;
  roles?: string[];
  permissions?: string[];
  verified?: boolean;
  is_verified?: boolean;
  isVerified?: boolean;
  verificationLevel?: number;
  verification_level?: number;
  user?: RawUser;
  profile?: RawUser;
  data?: RawUser;
};

export type DesktopPreferences = {
  notificationsEnabled: boolean;
  messageNotifications: boolean;
  groupNotifications: boolean;
  channelNotifications: boolean;
  sound: boolean;
  notificationPreview: boolean;
  theme: "dark" | "light" | "system";
  fontSize: "small" | "normal" | "large";
  chatBackground: "clean" | "soft" | "solid";
  bubbleStyle: "modern" | "compact";
  enterToSend: boolean;
  mediaAutoDownload: boolean;
  downloadFolder: string;
  linkPreviews: boolean;
  emojiSuggestions: boolean;
  autoDeleteTemp: boolean;
  appLock: boolean;
  language: "en" | "am";
  lastSeenVisibility: "everyone" | "contacts" | "nobody";
  profilePhotoVisibility: "everyone" | "contacts" | "nobody";
  whoCanMessageMe: "everyone" | "contacts";
  readReceipts: boolean;
  connectionIndicatorStyle?: "dots" | "orbit" | "bubble";
};

export type Conversation = {
  id: string;
  type?: string;
  otherUserId?: string;
  otherUserName?: string;
  otherUserPhotoUrl?: string;
  lastMessage?: string;
  lastMessageAt?: number;
  unreadCount?: number;
  muted?: boolean;
  pinned?: boolean;
  archived?: boolean;
  otherUserVerified?: boolean;
  otherUserVerificationLevel?: number;
};

export type CommunityRoom = {
  id: string;
  type: "group" | "channel";
  name: string;
  description?: string;
  ownerUid?: string;
  photoUrl?: string;
  memberCount?: number;
  joined?: boolean;
  lastMessage?: string;
  lastMessageAt?: number;
  lastPostAt?: number;
  lastPostPreview?: string;
  unreadCount?: number;
  verified?: boolean;
};

export type Message = {
  id: string;
  senderId?: string;
  senderRole?: string;
  senderName?: string;
  senderPhotoUrl?: string;
  type?: "text" | "image" | "video" | "voice" | "file" | "document" | "call";
  text?: string;
  mediaUrl?: string;
  mediaMimeType?: string;
  durationMs?: number;
  deliveredAt?: number;
  readAt?: number;
  deliveryStatus?: string;
  forwarded?: boolean;
  
  forwardedFromMessageId?: string;
  forwardedFromSenderName?: string;
  replyToMessageId?: string;
  replySenderName?: string;
  replyPreview?: string;
edited?: boolean;
  reactions?: Record<string, string>;
  deleted?: boolean;
  timestamp?: number;
  localStatus?: "sending" | "uploading" | "sent" | "delivered" | "seen" | "failed";
  localProgress?: number;
  localError?: string;
};

export type ChannelPost = {
  id: string;
  channelId?: string;
  authorUid?: string;
  authorName?: string;
  authorPhotoUrl?: string;
  title?: string;
  body?: string;
  postType?: "text" | "image" | "video" | "link";
  mediaUrl?: string;
  mediaMimeType?: string;
  linkUrl?: string;
  pinned?: boolean;
  views?: number;
  reactions?: Record<string, string>;
  createdAt?: number;
  localStatus?: "publishing" | "published" | "failed";
  localProgress?: number;
};

export type DesktopThread = {
  id: string;
  kind: ThreadKind;
  title: string;
  subtitle?: string;
  avatar?: string;
  lastPreview?: string;
  lastAt?: number;
  unreadCount: number;
  muted?: boolean;
  pinned?: boolean;
  archived?: boolean;
  online?: boolean;
  verified?: boolean;
  raw: Conversation | CommunityRoom;
};

export type CallSession = {
  id?: string;
  callId?: string;
  callerUid?: string;
  receiverUid?: string;
  type: "voice" | "video";
  status?: "ringing" | "accepted" | "connected" | "rejected" | "missed" | "cancelled" | "completed" | "ended" | "failed";
  startedAt?: number;
  acceptedAt?: number;
  connectedAt?: number;
  endedAt?: number;
  durationSeconds?: number;
  endReason?: string;
  cloudflareSessionId?: string;
  callerName?: string;
  callerPhotoUrl?: string;
  receiverName?: string;
  receiverPhotoUrl?: string;
};

export type UserSummary = {
  uid: string;
  name?: string;
  username?: string;
  phone?: string;
  photoUrl?: string;
  role?: string;
  verificationLevel?: number;
  verified?: boolean;
  bio?: string;
  about?: string;
  lastSeen?: number;
};

export type PresenceInfo = {
  uid?: string;
  online?: boolean;
  lastSeen?: number;
};

export type IceServer = {
  urls: string[] | string;
  username?: string;
  credential?: string;
};

export type CallRealtimeToken = {
  token?: string;
  expiresAt?: number;
  sfuUrl?: string;
  turnUrls?: string[];
  iceServers?: IceServer[];
  setupRequired?: boolean;
  message?: string;
};

export type CallSignal = {
  seq: number;
  fromUid?: string;
  kind: string;
  payload: string;
};

export type DesktopQrSession = {
  qrSessionId: string;
  qrPayload: string;
  expiresAt: number;
  status: "pending" | "scanned" | "approved" | "expired" | "rejected";
};

export type DesktopQrStatus = {
  qrSessionId: string;
  status: "pending" | "scanned" | "approved" | "expired" | "rejected";
  expiresAt: number;
  scannedAt?: number;
  approvedAt?: number;
  rejectedAt?: number;
  desktopToken?: string;
  userId?: string;
  device?: { name?: string; os?: string; location?: string; ip?: string };
};

export type LinkedDevice = {
  id: string;
  deviceType: "desktop" | "mobile" | "web";
  deviceName: string;
  os: string;
  appVersion?: string;
  ip?: string;
  location?: string;
  lastActiveAt: number;
  createdAt: number;
  expiresAt?: number;
  revokedAt?: number;
};

export type AdminStats = {
  totalUsers?: number;
  unverified?: number;
  emailVerified?: number;
  profileComplete?: number;
  blueBadge?: number;
  pendingVerifications?: number;
  approvedVerifications?: number;
  rejectedVerifications?: number;
};

export type AdminVerificationRequest = {
  uid: string;
  fullName?: string;
  documentType?: string;
  documentUrl?: string;
  selfieUrl?: string;
  status?: string;
  submittedAt?: number;
  userName?: string;
  userEmail?: string;
  userPhone?: string;
  userPhoto?: string;
  rejectionReason?: string;
};

export type AdminChannelVerification = {
  channelId: string;
  channelName?: string;
  ownerUid?: string;
  note?: string;
  status?: string;
  adminNote?: string;
  createdAt?: number;
  updatedAt?: number;
};

export type AdminGameApproval = {
  channelId: string;
  channelName?: string;
  ownerUid?: string;
  description?: string;
  status?: string;
  requestedAt?: number;
  reviewedAt?: number;
  adminNote?: string;
};

export type AdminReport = {
  id: string;
  reporterUid?: string;
  reportedUid?: string;
  threadType?: string;
  threadId?: string;
  messageId?: string;
  reason?: string;
  details?: string;
  createdAt?: number;
  status?: string;
};
