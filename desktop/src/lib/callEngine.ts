import { api } from "./api";
import {
  loadDevicePreferences,
  requestCallMedia,
  stopStream,
  type DevicePreferences,
  type MediaErrorCode
} from "./callMedia";
import type { CallSession, IceServer } from "./types";

const RING_TIMEOUT_MS = 45_000;
const SIGNAL_POLL_MS = 800;
const STATUS_POLL_MS = 2_000;
const INCOMING_POLL_MS = 3_000;
const AUTO_DISMISS_MS = 1_800;

export type CallPhase =
  | "permissions"
  | "permission-error"
  | "offline"
  | "incoming"
  | "calling"
  | "ringing"
  | "connecting"
  | "connected"
  | "reconnecting"
  | "ended"
  | "rejected"
  | "missed"
  | "failed";

export type CallQuality = "" | "excellent" | "good" | "poor";

export type CallState = {
  callId: string;
  type: "voice" | "video";
  isCaller: boolean;
  phase: CallPhase;
  statusText: string;
  peerUid: string;
  peerName: string;
  peerPhotoUrl: string;
  muted: boolean;
  cameraEnabled: boolean;
  speakerId: string;
  connectedAt: number;
  endReason: string;
  quality: CallQuality;
  latencyMs: number;
  participantCount: number;
  permissionError?: { code: MediaErrorCode; message: string };
};

export type CallFinishedInfo = {
  peerUid: string;
  type: "voice" | "video";
  outcome: "ended" | "missed" | "rejected" | "failed" | "cancelled";
  durationSeconds: number;
  isCaller: boolean;
};

type EngineHandlers = {
  onState: (state: CallState | null) => void;
  onLocalStream: (stream: MediaStream | null) => void;
  onRemoteStream: (stream: MediaStream | null) => void;
  onFinished?: (info: CallFinishedInfo) => void;
  getSoundEnabled?: () => boolean;
};

const TERMINAL: CallPhase[] = ["ended", "rejected", "missed", "failed", "offline", "permission-error"];

function isTerminal(phase: CallPhase) {
  return TERMINAL.includes(phase);
}

/** Synthesized ring tones so the desktop needs no audio assets. */
class ToneRinger {
  private context: AudioContext | null = null;
  private interval = 0;
  private stopFns: Array<() => void> = [];

  private burst(frequencies: number[], durationMs: number) {
    const ctx = this.context;
    if (!ctx) return;
    const gain = ctx.createGain();
    gain.gain.value = 0.08;
    gain.connect(ctx.destination);
    const oscillators = frequencies.map((frequency) => {
      const osc = ctx.createOscillator();
      osc.frequency.value = frequency;
      osc.type = "sine";
      osc.connect(gain);
      osc.start();
      return osc;
    });
    const timer = window.setTimeout(() => {
      oscillators.forEach((osc) => osc.stop());
      gain.disconnect();
    }, durationMs);
    this.stopFns.push(() => {
      window.clearTimeout(timer);
      oscillators.forEach((osc) => {
        try { osc.stop(); } catch { /* already stopped */ }
      });
      gain.disconnect();
    });
  }

  start(kind: "ringtone" | "ringback") {
    this.stop();
    const Ctor =
      typeof AudioContext !== "undefined"
        ? AudioContext
        : (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return;
    this.context = new Ctor();
    if (kind === "ringtone") {
      const ring = () => {
        this.burst([880, 1100], 350);
        window.setTimeout(() => this.burst([880, 1100], 350), 550);
      };
      ring();
      this.interval = window.setInterval(ring, 2400);
    } else {
      const ring = () => this.burst([425], 1000);
      ring();
      this.interval = window.setInterval(ring, 4000);
    }
  }

  stop() {
    if (this.interval) window.clearInterval(this.interval);
    this.interval = 0;
    this.stopFns.forEach((fn) => fn());
    this.stopFns = [];
    if (this.context) {
      void this.context.close().catch(() => undefined);
      this.context = null;
    }
  }
}

export class DesktopCallEngine {
  private state: CallState | null = null;
  private peer: RTCPeerConnection | null = null;
  private localStream: MediaStream | null = null;
  private remoteStream: MediaStream | null = null;
  private myUid = "";
  private lastSeq = 0;
  private remoteDescriptionSet = false;
  private pendingRemoteIce: RTCIceCandidateInit[] = [];
  private signalTimer = 0;
  private statusTimer = 0;
  private statsTimer = 0;
  private ringTimer = 0;
  private dismissTimer = 0;
  private incomingTimer = 0;
  private incomingFailures = 0;
  private lastIncomingId = "";
  private ringer = new ToneRinger();
  private stopped = false;

  constructor(private handlers: EngineHandlers) {}

  getState() {
    return this.state;
  }

  /** Watch the backend for incoming calls while signed in. */
  startIncomingWatcher(myUid: string) {
    this.myUid = myUid;
    this.stopIncomingWatcher();
    const poll = async () => {
      if (this.state) return;
      try {
        const call = await api.getActiveCall();
        this.incomingFailures = 0;
        if (!call || !call.id || call.status !== "ringing") return;
        if (call.receiverUid !== this.myUid) return;
        if (call.id === this.lastIncomingId) return; // already handled (declined/missed) — wait for backend to catch up
        this.onIncoming(call);
      } catch {
        // Endpoint may be unavailable on older deployments — back off instead of spamming.
        this.incomingFailures += 1;
        if (this.incomingFailures >= 3) {
          this.stopIncomingWatcher();
          this.incomingTimer = window.setInterval(() => void poll(), INCOMING_POLL_MS * 10);
        }
      }
    };
    this.incomingTimer = window.setInterval(() => void poll(), INCOMING_POLL_MS);
    void poll();
  }

  stopIncomingWatcher() {
    if (this.incomingTimer) window.clearInterval(this.incomingTimer);
    this.incomingTimer = 0;
  }

  destroy() {
    this.stopped = true;
    this.stopIncomingWatcher();
    this.cleanup();
    this.state = null;
    this.emit();
  }

  // ── Outgoing ───────────────────────────────────────────────────────────────

  async startOutgoing(peer: { uid: string; name: string; photoUrl?: string }, type: "voice" | "video") {
    if (this.state && !isTerminal(this.state.phase)) return;
    this.cleanup();
    this.setState({
      callId: "",
      type,
      isCaller: true,
      phase: "permissions",
      statusText: type === "video" ? "Camera and microphone access is needed" : "Microphone access is needed",
      peerUid: peer.uid,
      peerName: peer.name,
      peerPhotoUrl: peer.photoUrl || "",
      muted: false,
      cameraEnabled: type === "video",
      speakerId: loadDevicePreferences().speakerId,
      connectedAt: 0,
      endReason: "",
      quality: "",
      latencyMs: 0,
      participantCount: 1
    });

    const media = await requestCallMedia(type);
    if (!media.ok) {
      this.update({ phase: "permission-error", statusText: media.message, permissionError: { code: media.code, message: media.message } });
      return;
    }
    if (!this.state || this.state.phase !== "permissions") {
      stopStream(media.stream);
      return;
    }
    this.localStream = media.stream;
    this.handlers.onLocalStream(media.stream);

    // Offline receivers never ring: check presence first and close cleanly.
    let online = true;
    try {
      const presence = await api.getPresence(peer.uid);
      online = Boolean(presence?.online);
    } catch {
      online = true; // presence unavailable — attempt the call anyway
    }
    if (!online) {
      this.update({ phase: "offline", statusText: "User is not online", endReason: "User is not online" });
      this.releaseMedia();
      this.autoDismiss();
      return;
    }

    this.update({ phase: "calling", statusText: "Calling..." });
    try {
      const call = await api.startCall(peer.uid, type);
      const callId = call.id || call.callId || "";
      if (!callId) throw new Error("Call could not be created");
      if (!this.state || isTerminal(this.state.phase)) {
        void api.cancelCall(callId).catch(() => api.endCall(callId, "cancelled"));
        return;
      }
      this.update({ callId, phase: "ringing", statusText: "Ringing...", participantCount: 1 });
      if (this.soundEnabled()) this.ringer.start("ringback");
      await this.setupPeer(callId, type);
      await this.createOffer(callId);
      this.startSignalLoop(callId);
      this.startStatusLoop(callId);
      this.armRingTimeout(callId, "no-answer");
    } catch (error) {
      this.fail(error instanceof Error ? error.message : "Could not start call");
    }
  }

  // ── Incoming ───────────────────────────────────────────────────────────────

  private onIncoming(call: CallSession) {
    const callId = call.id || call.callId || "";
    if (!callId) return;
    this.lastIncomingId = callId;
    this.cleanup();
    this.setState({
      callId,
      type: call.type === "video" ? "video" : "voice",
      isCaller: false,
      phase: "incoming",
      statusText: call.type === "video" ? "Incoming video call" : "Incoming voice call",
      peerUid: call.callerUid || "",
      peerName: call.callerName || "Unknown caller",
      peerPhotoUrl: call.callerPhotoUrl || "",
      muted: false,
      cameraEnabled: call.type === "video",
      speakerId: loadDevicePreferences().speakerId,
      connectedAt: 0,
      endReason: "",
      quality: "",
      latencyMs: 0,
      participantCount: 1
    });
    if (this.soundEnabled()) this.ringer.start("ringtone");
    this.startStatusLoop(callId); // a caller cancel must close this screen immediately
    this.armRingTimeout(callId, "missed");
    this.notifyIncoming(call);
  }

  private notifyIncoming(call: CallSession) {
    if (typeof window === "undefined" || !("Notification" in window)) return;
    if (Notification.permission !== "granted") return;
    new Notification(call.type === "video" ? "Incoming video call" : "Incoming voice call", {
      body: call.callerName || "AdisChat call",
      tag: `call:${call.id}`
    });
  }

  async accept() {
    const current = this.state;
    if (!current || current.phase !== "incoming") return;
    this.ringer.stop();
    if (this.ringTimer) window.clearTimeout(this.ringTimer);
    this.update({ phase: "permissions", statusText: current.type === "video" ? "Camera and microphone access is needed" : "Microphone access is needed" });
    const media = await requestCallMedia(current.type);
    if (!media.ok) {
      this.update({ phase: "permission-error", statusText: media.message, permissionError: { code: media.code, message: media.message } });
      void api.rejectCall(current.callId).catch(() => undefined);
      return;
    }
    this.localStream = media.stream;
    this.handlers.onLocalStream(media.stream);
    this.update({ phase: "connecting", statusText: "Connecting..." });
    try {
      await api.acceptCall(current.callId);
      await this.setupPeer(current.callId, current.type);
      this.startSignalLoop(current.callId); // picks up the caller's waiting offer
      this.update({ participantCount: 2 });
    } catch (error) {
      this.fail(error instanceof Error ? error.message : "Could not join call");
    }
  }

  decline() {
    const current = this.state;
    if (!current) return;
    if (current.callId) void api.rejectCall(current.callId).catch(() => undefined);
    this.terminate("rejected", "Call declined");
  }

  // ── Hangup ─────────────────────────────────────────────────────────────────

  end() {
    const current = this.state;
    if (!current) return;
    if (isTerminal(current.phase)) {
      this.dismiss();
      return;
    }
    if (current.isCaller && current.connectedAt === 0) {
      if (current.callId) {
        void api.cancelCall(current.callId).catch(() => api.endCall(current.callId, "cancelled").catch(() => undefined));
      }
      this.terminate("ended", "Call cancelled");
      return;
    }
    const reason = current.isCaller ? "ended_by_caller" : "ended_by_receiver";
    if (current.callId) void api.endCall(current.callId, reason).catch(() => undefined);
    const duration = this.durationLabel(current.connectedAt);
    this.terminate("ended", duration ? `Call ended - ${duration}` : "Call ended");
  }

  dismiss() {
    if (this.dismissTimer) window.clearTimeout(this.dismissTimer);
    this.dismissTimer = 0;
    this.cleanup();
    this.state = null;
    this.emit();
  }

  // ── Controls ───────────────────────────────────────────────────────────────

  toggleMute() {
    const current = this.state;
    if (!current) return;
    const muted = !current.muted;
    this.localStream?.getAudioTracks().forEach((track) => {
      track.enabled = !muted;
    });
    this.update({ muted });
  }

  toggleCamera() {
    const current = this.state;
    if (!current || current.type !== "video") return;
    const cameraEnabled = !current.cameraEnabled;
    this.localStream?.getVideoTracks().forEach((track) => {
      track.enabled = cameraEnabled;
    });
    this.update({ cameraEnabled });
  }

  async switchCamera(deviceId: string) {
    if (!this.peer || !this.localStream || this.state?.type !== "video") return;
    try {
      const replacement = await navigator.mediaDevices.getUserMedia({
        video: { deviceId: { exact: deviceId }, width: { ideal: 1280 }, height: { ideal: 720 } }
      });
      const newTrack = replacement.getVideoTracks()[0];
      if (!newTrack) return;
      const sender = this.peer.getSenders().find((item) => item.track?.kind === "video");
      await sender?.replaceTrack(newTrack);
      this.localStream.getVideoTracks().forEach((track) => {
        track.stop();
        this.localStream?.removeTrack(track);
      });
      this.localStream.addTrack(newTrack);
      newTrack.enabled = this.state?.cameraEnabled ?? true;
      this.handlers.onLocalStream(this.localStream);
    } catch {
      // keep the current camera if the switch fails
    }
  }

  async switchMicrophone(deviceId: string) {
    if (!this.peer || !this.localStream) return;
    try {
      const replacement = await navigator.mediaDevices.getUserMedia({
        audio: { deviceId: { exact: deviceId }, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      const newTrack = replacement.getAudioTracks()[0];
      if (!newTrack) return;
      const sender = this.peer.getSenders().find((item) => item.track?.kind === "audio");
      await sender?.replaceTrack(newTrack);
      this.localStream.getAudioTracks().forEach((track) => {
        track.stop();
        this.localStream?.removeTrack(track);
      });
      this.localStream.addTrack(newTrack);
      newTrack.enabled = !(this.state?.muted ?? false);
    } catch {
      // keep the current microphone if the switch fails
    }
  }

  setSpeaker(deviceId: string) {
    this.update({ speakerId: deviceId });
  }

  applyDevicePreferences(prefs: DevicePreferences) {
    if (prefs.microphoneId) void this.switchMicrophone(prefs.microphoneId);
    if (prefs.cameraId && this.state?.type === "video") void this.switchCamera(prefs.cameraId);
    this.setSpeaker(prefs.speakerId);
  }

  // ── WebRTC ─────────────────────────────────────────────────────────────────

  private async setupPeer(callId: string, type: "voice" | "video") {
    let iceServers: RTCIceServer[] = [
      { urls: "stun:stun.cloudflare.com:3478" },
      { urls: "stun:stun.l.google.com:19302" },
      { urls: "stun:stun1.l.google.com:19302" },
      { urls: "stun:stun2.l.google.com:19302" }
    ];
    try {
      const token = await api.getCallToken(callId);
      const fromBackend = (token.realtime?.iceServers || []) as IceServer[];
      if (fromBackend.length) {
        iceServers = fromBackend.map((server) => ({
          urls: server.urls,
          username: server.username || undefined,
          credential: server.credential || undefined
        }));
      }
    } catch {
      // STUN fallback already set
    }

    const pc = new RTCPeerConnection({ iceServers, iceTransportPolicy: "all", iceCandidatePoolSize: 4 });
    this.peer = pc;
    this.remoteDescriptionSet = false;
    this.pendingRemoteIce = [];

    this.localStream?.getTracks().forEach((track) => {
      if (this.localStream) pc.addTrack(track, this.localStream);
    });
    if (type === "video" && !this.localStream?.getVideoTracks().length) {
      pc.addTransceiver("video", { direction: "recvonly" });
    }

    pc.onicecandidate = (event) => {
      if (!event.candidate) return;
      const payload = JSON.stringify({
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex
      });
      void api.sendCallSignal(callId, "ice", payload).catch(() => undefined);
    };

    pc.ontrack = (event) => {
      if (!this.remoteStream) {
        this.remoteStream = new MediaStream();
        this.handlers.onRemoteStream(this.remoteStream);
      }
      this.remoteStream.addTrack(event.track);
      this.handlers.onRemoteStream(this.remoteStream);
    };

    pc.onconnectionstatechange = () => {
      const current = this.state;
      if (!current || isTerminal(current.phase)) return;
      switch (pc.connectionState) {
        case "connected": {
          this.ringer.stop();
          const firstConnect = current.connectedAt === 0;
          this.update({
            phase: "connected",
            statusText: "",
            connectedAt: firstConnect ? Date.now() : current.connectedAt,
            participantCount: 2
          });
          if (firstConnect) void api.connectedCall(callId).catch(() => undefined);
          this.startStatsLoop();
          break;
        }
        case "disconnected":
          if (current.phase === "connected") this.update({ phase: "reconnecting", statusText: "Reconnecting..." });
          break;
        case "failed":
          void api.endCall(callId, "network_error").catch(() => undefined);
          this.terminate("failed", "Connection failed");
          break;
        default:
          break;
      }
    };
  }

  private async createOffer(callId: string) {
    const pc = this.peer;
    if (!pc) return;
    const offer = await pc.createOffer({
      offerToReceiveAudio: true,
      offerToReceiveVideo: this.state?.type === "video"
    });
    await pc.setLocalDescription(offer);
    await api.sendCallSignal(callId, "offer", JSON.stringify({ sdp: offer.sdp })).catch(() => undefined);
  }

  private async handleSignal(kind: string, payload: string) {
    const pc = this.peer;
    if (!pc) return;
    try {
      if (kind === "offer") {
        const { sdp } = JSON.parse(payload) as { sdp: string };
        await pc.setRemoteDescription({ type: "offer", sdp });
        this.remoteDescriptionSet = true;
        await this.drainPendingIce();
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        const callId = this.state?.callId || "";
        if (callId) await api.sendCallSignal(callId, "answer", JSON.stringify({ sdp: answer.sdp })).catch(() => undefined);
      } else if (kind === "answer") {
        const { sdp } = JSON.parse(payload) as { sdp: string };
        await pc.setRemoteDescription({ type: "answer", sdp });
        this.remoteDescriptionSet = true;
        await this.drainPendingIce();
      } else if (kind === "ice") {
        const parsed = JSON.parse(payload) as { candidate: string; sdpMid: string | null; sdpMLineIndex: number | null };
        const candidate: RTCIceCandidateInit = {
          candidate: parsed.candidate,
          sdpMid: parsed.sdpMid ?? undefined,
          sdpMLineIndex: parsed.sdpMLineIndex ?? undefined
        };
        if (this.remoteDescriptionSet) await pc.addIceCandidate(candidate);
        else this.pendingRemoteIce.push(candidate);
      }
    } catch {
      // tolerate malformed/duplicate signals
    }
  }

  private async drainPendingIce() {
    const pc = this.peer;
    if (!pc) return;
    const pending = this.pendingRemoteIce.splice(0);
    for (const candidate of pending) {
      await pc.addIceCandidate(candidate).catch(() => undefined);
    }
  }

  // ── Polling loops ──────────────────────────────────────────────────────────

  private startSignalLoop(callId: string) {
    if (this.signalTimer) window.clearInterval(this.signalTimer);
    this.lastSeq = 0;
    this.signalTimer = window.setInterval(() => {
      void (async () => {
        if (!this.state || isTerminal(this.state.phase)) return;
        try {
          const response = await api.getCallSignals(callId, this.lastSeq);
          for (const signal of response.signals || []) {
            if (signal.fromUid && signal.fromUid === this.myUid) {
              this.lastSeq = Math.max(this.lastSeq, signal.seq);
              continue;
            }
            this.lastSeq = Math.max(this.lastSeq, signal.seq);
            await this.handleSignal(signal.kind, signal.payload);
          }
          if (response.status) this.applyRemoteStatus(response.status);
        } catch {
          // transient polling errors are fine
        }
      })();
    }, SIGNAL_POLL_MS);
  }

  private startStatusLoop(callId: string) {
    if (this.statusTimer) window.clearInterval(this.statusTimer);
    this.statusTimer = window.setInterval(() => {
      void (async () => {
        const current = this.state;
        if (!current || isTerminal(current.phase)) {
          if (this.statusTimer) window.clearInterval(this.statusTimer);
          this.statusTimer = 0;
          return;
        }
        try {
          const call = await api.getCall(callId);
          if (call.status) this.applyRemoteStatus(call.status);
        } catch {
          // transient polling errors are fine
        }
      })();
    }, STATUS_POLL_MS);
  }

  private startStatsLoop() {
    if (this.statsTimer) window.clearInterval(this.statsTimer);
    this.statsTimer = window.setInterval(() => {
      void (async () => {
        const pc = this.peer;
        if (!pc || this.state?.phase !== "connected") return;
        try {
          const report = await pc.getStats();
          let rttMs = 0;
          report.forEach((stat) => {
            if (stat.type === "candidate-pair") {
              const pair = stat as RTCIceCandidatePairStats & { nominated?: boolean };
              if (typeof pair.currentRoundTripTime === "number" && (pair.nominated || rttMs === 0)) {
                rttMs = Math.round(pair.currentRoundTripTime * 1000);
              }
            }
          });
          const quality: CallQuality = rttMs >= 300 ? "poor" : rttMs >= 150 ? "good" : "excellent";
          this.update({ quality, latencyMs: rttMs });
        } catch {
          // stats unavailable — nothing to do
        }
      })();
    }, 1500);
  }

  private armRingTimeout(callId: string, mode: "no-answer" | "missed") {
    if (this.ringTimer) window.clearTimeout(this.ringTimer);
    this.ringTimer = window.setTimeout(() => {
      const current = this.state;
      if (!current) return;
      if (current.phase === "ringing" || current.phase === "calling" || current.phase === "incoming") {
        void api.endCall(callId, "missed_timeout").catch(() => undefined);
        this.terminate("missed", mode === "no-answer" ? "No answer" : "Missed call");
      }
    }, RING_TIMEOUT_MS);
  }

  /** Backend status is the source of truth for both peers. Idempotent. */
  private applyRemoteStatus(status: string) {
    const current = this.state;
    if (!current || isTerminal(current.phase)) return;
    switch (status) {
      case "ringing":
        break;
      case "accepted":
        if (current.phase === "ringing" || current.phase === "calling") {
          this.ringer.stop();
          this.update({ phase: "connecting", statusText: "Connecting...", participantCount: 2 });
        }
        break;
      case "connected":
        break; // WebRTC connection state drives "connected"
      case "completed":
      case "ended": {
        const duration = this.durationLabel(current.connectedAt);
        this.terminate("ended", duration ? `Call ended - ${duration}` : "Call ended");
        break;
      }
      case "cancelled":
        if (current.isCaller) this.terminate("ended", "Call ended");
        else this.terminate("missed", "Missed call");
        break;
      case "rejected":
        this.terminate("rejected", "Call declined");
        break;
      case "missed":
        this.terminate("missed", current.isCaller ? "No answer" : "Missed call");
        break;
      case "failed":
        this.terminate("failed", "Connection failed");
        break;
      default:
        break;
    }
  }

  // ── Lifecycle helpers ──────────────────────────────────────────────────────

  private terminate(phase: CallPhase, reason: string) {
    const current = this.state;
    if (!current || isTerminal(current.phase)) return;
    const durationSeconds = current.connectedAt > 0 ? Math.max(0, Math.round((Date.now() - current.connectedAt) / 1000)) : 0;
    this.update({ phase, statusText: reason, endReason: reason });
    this.handlers.onFinished?.({
      peerUid: current.peerUid,
      type: current.type,
      outcome:
        phase === "rejected" ? "rejected"
        : phase === "missed" ? "missed"
        : phase === "failed" ? "failed"
        : current.connectedAt > 0 ? "ended" : "cancelled",
      durationSeconds,
      isCaller: current.isCaller
    });
    this.cleanup();
    this.autoDismiss();
  }

  private fail(message: string) {
    const current = this.state;
    if (current?.callId) void api.endCall(current.callId, "network_error").catch(() => undefined);
    this.terminate("failed", message);
  }

  private autoDismiss() {
    if (this.dismissTimer) window.clearTimeout(this.dismissTimer);
    this.dismissTimer = window.setTimeout(() => {
      if (this.state && isTerminal(this.state.phase)) {
        this.state = null;
        this.emit();
      }
    }, AUTO_DISMISS_MS);
  }

  private durationLabel(connectedAt: number) {
    if (connectedAt <= 0) return "";
    const seconds = Math.max(0, Math.round((Date.now() - connectedAt) / 1000));
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  }

  private releaseMedia() {
    stopStream(this.localStream);
    this.localStream = null;
    this.handlers.onLocalStream(null);
  }

  private cleanup() {
    this.ringer.stop();
    if (this.signalTimer) window.clearInterval(this.signalTimer);
    if (this.statusTimer) window.clearInterval(this.statusTimer);
    if (this.statsTimer) window.clearInterval(this.statsTimer);
    if (this.ringTimer) window.clearTimeout(this.ringTimer);
    this.signalTimer = 0;
    this.statusTimer = 0;
    this.statsTimer = 0;
    this.ringTimer = 0;
    this.lastSeq = 0;
    this.remoteDescriptionSet = false;
    this.pendingRemoteIce = [];
    this.releaseMedia();
    this.remoteStream = null;
    this.handlers.onRemoteStream(null);
    try {
      this.peer?.close();
    } catch {
      // already closed
    }
    this.peer = null;
  }

  private soundEnabled() {
    return this.handlers.getSoundEnabled ? this.handlers.getSoundEnabled() : true;
  }

  private setState(state: CallState) {
    this.state = state;
    this.emit();
  }

  private update(patch: Partial<CallState>) {
    if (!this.state) return;
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  private emit() {
    if (!this.stopped) this.handlers.onState(this.state ? { ...this.state } : null);
  }
}
