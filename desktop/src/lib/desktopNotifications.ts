import type { DesktopThread, Message } from "./types";

export async function requestNotificationPermission() {
  if (typeof window === "undefined" || !("Notification" in window)) return "unsupported";
  if (Notification.permission === "default") return Notification.requestPermission();
  return Notification.permission;
}

export function notifyNewMessage(thread: DesktopThread, message: Message) {
  if (typeof window === "undefined" || !("Notification" in window)) return;
  if (Notification.permission !== "granted") return;
  const body = message.text || (message.type ? `Sent a ${message.type}` : "New message");
  new Notification(thread.title, {
    body,
    tag: `${thread.kind}:${thread.id}`,
    silent: false
  });
}

export function setAppBadge(count: number) {
  const nav = navigator as Navigator & {
    setAppBadge?: (count: number) => Promise<void>;
    clearAppBadge?: () => Promise<void>;
  };
  if (count > 0) void nav.setAppBadge?.(count);
  else void nav.clearAppBadge?.();
}
