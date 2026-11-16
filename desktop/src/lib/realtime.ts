import { api } from "./api";
import type { ChannelPost, DesktopThread, Message } from "./types";

type RealtimeHandlers = {
  onMessages: (threadId: string, messages: Message[]) => void;
  onChannelPosts: (threadId: string, posts: ChannelPost[]) => void;
  onPresence?: (threadId: string, status: string) => void;
  onStatus?: (status: "connected" | "reconnecting" | "disconnected") => void;
  onSocketDisconnect?: () => void;
};

export class DesktopRealtime {
  private socket?: WebSocket;
  private pollHandle?: number;
  private visibilityHandle?: () => void;
  private stopped = false;
  private activePollRunning = false;
  private pollRequests: number[] = [];
  private socketConnected = false;

  constructor(
    private getThreads: () => DesktopThread[],
    private getActiveThread: () => DesktopThread | undefined,
    private handlers: RealtimeHandlers
  ) {}

  start() {
    this.stopped = false;
    const wsUrl = process.env.NEXT_PUBLIC_ADISCHAT_WS_URL;
    if (wsUrl) this.connectWebSocket(wsUrl);
    else {
      this.handlers.onStatus?.("disconnected");
      this.startPollingFallback();
    }
  }

  stop() {
    this.stopped = true;
    this.socketConnected = false;
    this.socket?.close();
    this.stopPollingFallback();
    this.handlers.onStatus?.("disconnected");
  }

  private connectWebSocket(wsUrl: string) {
    try {
      this.socket = new WebSocket(wsUrl);
      this.handlers.onStatus?.("reconnecting");
      this.socket.onopen = () => {
        this.socketConnected = true;
        this.stopPollingFallback();
        this.handlers.onStatus?.("connected");
      };
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
        this.socketConnected = false;
        if (!this.stopped) {
          this.handlers.onSocketDisconnect?.();
          this.handlers.onStatus?.("reconnecting");
          this.startPollingFallback();
          window.setTimeout(() => this.connectWebSocket(wsUrl), 2500);
        }
      };
      this.socket.onerror = () => {
        this.socketConnected = false;
        if (!this.stopped) this.startPollingFallback();
      };
    } catch {
      this.socketConnected = false;
      this.handlers.onStatus?.("disconnected");
      this.startPollingFallback();
    }
  }

  private stopPollingFallback() {
    if (this.pollHandle) window.clearInterval(this.pollHandle);
    this.pollHandle = undefined;
    this.visibilityHandle?.();
    this.visibilityHandle = undefined;
  }

  private startPollingFallback() {
    if (this.socketConnected || this.pollHandle) return;
    const schedule = () => {
      if (this.socketConnected) {
        this.stopPollingFallback();
        return;
      }
      if (this.pollHandle) window.clearInterval(this.pollHandle);
      const delayMs = document.hidden ? 45_000 : 12_000;
      this.pollHandle = window.setInterval(() => void poll("active-thread"), delayMs);
    };
    const poll = async (source: string) => {
      if (this.socketConnected || this.activePollRunning) return;
      const activeThread = this.getActiveThread();
      if (!activeThread) return;
      this.activePollRunning = true;
      try {
        this.recordPollRequest(source);
        if (activeThread.kind === "channel") {
          const response = await api.getChannelPosts(activeThread.id, activeThread.lastAt || 0);
          const posts = Array.isArray(response) ? response : response.posts || [];
          if (posts.length) this.handlers.onChannelPosts(activeThread.id, posts);
        } else {
          const messages =
            activeThread.kind === "group"
              ? await api.getCommunityMessages(activeThread.id, activeThread.lastAt || 0)
              : await api.getMessages(activeThread.id, activeThread.lastAt || 0);
          if (messages.length) this.handlers.onMessages(activeThread.id, messages);
        }
      } finally {
        this.activePollRunning = false;
      }
    };
    this.visibilityHandle = () => {
      document.removeEventListener("visibilitychange", schedule);
    };
    document.addEventListener("visibilitychange", schedule);
    void poll("initial-active-thread");
    schedule();
  }

  private recordPollRequest(source: string) {
    const now = Date.now();
    this.pollRequests = this.pollRequests.filter((time) => now - time < 60_000);
    this.pollRequests.push(now);
    const rpm = this.pollRequests.length;
    console.debug("[desktop-poll]", { source, requestsPerMinute: rpm, activeJobs: this.activePollRunning ? 1 : 0, knownThreads: this.getThreads().length });
    if (rpm > 100) {
      console.warn("[desktop-poll] excessive polling", { source, requestsPerMinute: rpm });
    }
  }
}