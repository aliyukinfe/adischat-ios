"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import type { DesktopThread, Message, PresenceInfo, UserSummary } from "@/lib/types";

export type ProfileTarget = {
  uid: string;
  name?: string;
  photoUrl?: string;
  username?: string;
  conversationId?: string;
};

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "A";
}

function lastSeenLabel(presence: PresenceInfo | null) {
  if (!presence) return "";
  if (presence.online) return "Online";
  if (presence.lastSeen) {
    const date = new Date(presence.lastSeen);
    const sameDay = date.toDateString() === new Date().toDateString();
    const time = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date);
    if (sameDay) return `Last seen today at ${time}`;
    return `Last seen ${new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date)} at ${time}`;
  }
  return "Last seen recently";
}

export function ProfilePanel({
  target,
  isSelf,
  threads,
  messagesByThread,
  onClose,
  onMessage,
  onVoiceCall,
  onVideoCall,
  onEditOwnProfile,
  onOpenImage,
  onToast
}: {
  target: ProfileTarget;
  isSelf: boolean;
  threads: DesktopThread[];
  messagesByThread: Record<string, Message[]>;
  onClose: () => void;
  onMessage: (uid: string) => Promise<void> | void;
  onVoiceCall: (user: { uid: string; name: string; photoUrl?: string }) => void;
  onVideoCall?: (user: { uid: string; name: string; photoUrl?: string }) => void;
  onEditOwnProfile: () => void;
  onOpenImage: (url: string) => void;
  onToast: (message: string) => void;
}) {
  const [profile, setProfile] = useState<UserSummary | null>(null);
  const [presence, setPresence] = useState<PresenceInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const [confirming, setConfirming] = useState<"block" | "report" | "">("");

  const conversationId = useMemo(() => {
    if (target.conversationId) return target.conversationId;
    const thread = threads.find((item) => item.kind === "chat" && (item.raw as { otherUserId?: string }).otherUserId === target.uid);
    return thread?.id || "";
  }, [target.conversationId, target.uid, threads]);

  const mutualGroups = useMemo(() => {
    return threads.filter((thread) => {
      if (thread.kind !== "group") return false;
      const messages = messagesByThread[thread.id] || [];
      return messages.some((message) => message.senderId === target.uid);
    });
  }, [threads, messagesByThread, target.uid]);

  const sharedMedia = useMemo(() => {
    if (!conversationId) return [];
    return (messagesByThread[conversationId] || [])
      .filter((message) => message.type === "image" && message.mediaUrl)
      .slice(-6)
      .reverse();
  }, [conversationId, messagesByThread]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setProfile(null);
    setPresence(null);

    async function load() {
      if (!target.uid) {
        if (!cancelled) {
          setError("This user profile is not available.");
          setLoading(false);
        }
        return;
      }
      let summary: UserSummary | null = null;
      try {
        summary = await api.getUser(target.uid);
      } catch {
        // No direct profile endpoint on this deployment — fall back to user search.
        const query = target.username || target.name || "";
        if (query) {
          try {
            const results = await api.searchUsers(query);
            summary = results.find((item) => item.uid === target.uid) || null;
          } catch {
            summary = null;
          }
        }
      }
      if (cancelled) return;
      const merged: UserSummary = {
        uid: target.uid,
        name: summary?.name || target.name || "AdisChat user",
        username: summary?.username || target.username || "",
        photoUrl: summary?.photoUrl || target.photoUrl || "",
        bio: summary?.bio || summary?.about || "",
        verified: summary?.verified,
        verificationLevel: summary?.verificationLevel
      };
      setProfile(merged);
      setLoading(false);
      try {
        const live = await api.getPresence(target.uid);
        if (!cancelled) setPresence(live);
      } catch {
        if (!cancelled) setPresence(null);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [target.uid, target.name, target.username, target.photoUrl]);

  async function runAction(kind: "block" | "report") {
    if (!conversationId) return;
    setBusy(kind);
    try {
      if (kind === "block") await api.blockUser(conversationId, target.uid);
      else await api.reportUser(conversationId, target.uid, "report", "Reported from desktop profile");
      onToast(kind === "block" ? "User blocked" : "Report sent");
      setConfirming("");
    } catch (actionError) {
      onToast(actionError instanceof Error ? actionError.message : "Action failed");
    } finally {
      setBusy("");
    }
  }

  const callUser = { uid: target.uid, name: profile?.name || target.name || "AdisChat user", photoUrl: profile?.photoUrl || target.photoUrl };

  return (
    <div className="profile-overlay" onClick={onClose}>
      <aside className="profile-panel" onClick={(event) => event.stopPropagation()} role="dialog" aria-label="User profile">
        <header className="profile-panel-top">
          <strong>{isSelf ? "My profile" : "Profile"}</strong>
          <button onClick={onClose} aria-label="Close profile">Close</button>
        </header>

        {loading ? (
          <div className="profile-state">
            <span className="profile-spinner" aria-hidden />
            <p>Loading profile...</p>
          </div>
        ) : error ? (
          <div className="profile-state">
            <p className="error">{error}</p>
            <button className="ghost-button" onClick={onClose}>Close</button>
          </div>
        ) : !profile ? (
          <div className="profile-state">
            <p>No profile information available.</p>
            <button className="ghost-button" onClick={onClose}>Close</button>
          </div>
        ) : (
          <div className="profile-panel-body">
            <div className="profile-identity">
              <span className="profile-photo">
                {profile.photoUrl ? <img src={profile.photoUrl} alt="" /> : initials(profile.name || "A")}
                {presence?.online ? <i aria-label="Online" /> : null}
              </span>
              <h2>{profile.name}</h2>
              {profile.username ? <p className="profile-username">@{profile.username}</p> : null}
              <p className={`profile-presence ${presence?.online ? "online" : ""}`}>
                {presence ? lastSeenLabel(presence) : "Last seen recently"}
              </p>
            </div>

            {isSelf ? (
              <div className="profile-actions">
                <button className="profile-action primary" onClick={onEditOwnProfile}>Edit profile</button>
              </div>
            ) : (
              <div className="profile-actions">
                <button
                  className="profile-action primary"
                  disabled={busy === "message"}
                  onClick={() => {
                    setBusy("message");
                    void Promise.resolve(onMessage(target.uid)).finally(() => setBusy(""));
                  }}
                >
                  Message
                </button>
                <button className="profile-action" onClick={() => onVoiceCall(callUser)}>Voice call</button>
                {onVideoCall ? <button className="profile-action" onClick={() => onVideoCall(callUser)}>Video call</button> : null}
              </div>
            )}

            {profile.bio ? (
              <section className="profile-section">
                <h3>Bio</h3>
                <p>{profile.bio}</p>
              </section>
            ) : null}

            {!isSelf && sharedMedia.length ? (
              <section className="profile-section">
                <h3>Shared media</h3>
                <div className="profile-media-grid">
                  {sharedMedia.map((message) => (
                    <button key={message.id} onClick={() => message.mediaUrl && onOpenImage(message.mediaUrl)} aria-label="Open shared image">
                      <img src={message.mediaUrl} alt="" />
                    </button>
                  ))}
                </div>
              </section>
            ) : null}

            {!isSelf && mutualGroups.length ? (
              <section className="profile-section">
                <h3>Groups in common</h3>
                <ul className="profile-mutual-list">
                  {mutualGroups.slice(0, 5).map((group) => (
                    <li key={group.id}>{group.title}</li>
                  ))}
                </ul>
              </section>
            ) : null}

            {!isSelf && conversationId ? (
              <section className="profile-section danger-zone">
                {confirming ? (
                  <div className="profile-confirm">
                    <p>{confirming === "block" ? `Block ${profile.name}? They will no longer be able to message you.` : `Report ${profile.name} to AdisChat?`}</p>
                    <div>
                      <button className="profile-action danger" disabled={Boolean(busy)} onClick={() => void runAction(confirming)}>
                        {busy ? "Working..." : confirming === "block" ? "Block" : "Report"}
                      </button>
                      <button className="profile-action" onClick={() => setConfirming("")}>Cancel</button>
                    </div>
                  </div>
                ) : (
                  <div className="profile-actions">
                    <button className="profile-action danger" onClick={() => setConfirming("block")}>Block</button>
                    <button className="profile-action danger" onClick={() => setConfirming("report")}>Report</button>
                  </div>
                )}
              </section>
            ) : null}
          </div>
        )}
      </aside>
    </div>
  );
}
