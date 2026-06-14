import { api } from "./api";
import type { ChannelPost, DesktopThread, Message } from "./types";

type RealtimeHandlers = {
  onMessages: (threadId: string, messages: Message[]) => void;
  onChannelPosts: (threadId: string, posts: ChannelPost[]) => void;
  onPresence?: (threadId: string, status: string) => void;
  onStatus?: (status: "connected" | "reconnecting" | "disconnected") => void;
};

export class DesktopRealtime {
  private socket?: WebSocket;
  private pollHandle?: number;
  private stopped = false;

  constructor(
    private getThreads: () => DesktopThread[],
    private getActiveThread: () => DesktopThread | undefined,
    private handlers: RealtimeHandlers
  ) {}

  start() {
    this.stopped = false;
    const wsUrl = process.env.NEXT_PUBLIC_ADISCHAT_WS_URL;
    if (wsUrl) this.connectWebSocket(wsUrl);
    else this.handlers.onStatus?.("disconnected");
    this.startPollingFallback();
  }

  stop() {
    this.stopped = true;
    this.socket?.close();
    if (this.pollHandle) window.clearInterval(this.pollHandle);
    this.handlers.onStatus?.("disconnected");
  }

  private connectWebSocket(wsUrl: string) {
    try {
      this.socket = new WebSocket(wsUrl);
      this.handlers.onStatus?.("reconnecting");
      this.socket.onopen = () => this.handlers.onStatus?.("connected");
      this.socket.onmessage = (event) => {
        const payload = JSON.parse(event.data) as {
          type: string;
          threadId?: string;
          messages?: Message[];
          posts?: ChannelPost[];
          status?: string;
        };
        if (payload.type === "messages" && payload.threadId && payload.messages) {
          this.handlers.onMessages(payload.threadId, payload.messages);
        }
        if (payload.type === "channel_posts" && payload.threadId && payload.posts) {
          this.handlers.onChannelPosts(payload.threadId, payload.posts);
        }
        if (payload.type === "presence" && payload.threadId && payload.status) {
          this.handlers.onPresence?.(payload.threadId, payload.status);
        }
      };
      this.socket.onclose = () => {
        if (!this.stopped) {
          this.handlers.onStatus?.("reconnecting");
          window.setTimeout(() => this.connectWebSocket(wsUrl), 2500);
        }
      };
    } catch {
      // Existing Android behavior polls. Desktop keeps that fallback until a WS URL is configured.
      this.handlers.onStatus?.("disconnected");
    }
  }

  private startPollingFallback() {
    const poll = async () => {
      const activeThread = this.getActiveThread();
      const active = uniqueThreads([activeThread, ...this.getThreads().slice(0, 30)]);
      await Promise.allSettled(
        active.map(async (thread) => {
          if (thread.kind === "channel") {
            const response = await api.getChannelPosts(thread.id);
            const posts = Array.isArray(response) ? response : response.posts || [];
            this.handlers.onChannelPosts(thread.id, posts);
          } else {
            const messages =
              thread.kind === "group" ? await api.getCommunityMessages(thread.id) : await api.getMessages(thread.id);
            this.handlers.onMessages(thread.id, messages);
          }
        })
      );
    };
    void poll();
    this.pollHandle = window.setInterval(poll, 2000);
  }
}

function uniqueThreads(threads: Array<DesktopThread | undefined>): DesktopThread[] {
  const seen = new Set<string>();
  return threads.filter((thread): thread is DesktopThread => {
    if (!thread) return false;
    const key = `${thread.kind}:${thread.id}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
