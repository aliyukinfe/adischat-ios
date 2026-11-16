"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DesktopCallEngine, CallState } from "@/lib/callEngine";
import {
  listMediaDevices,
  loadDevicePreferences,
  meterMicrophone,
  openDevicePermissionSettings,
  saveDevicePreferences,
  stopStream,
  watchDeviceChanges,
  type DeviceLists,
  type DevicePreferences
} from "@/lib/callMedia";

type CallIconName = "phone" | "video" | "mic" | "mic-off" | "cam" | "cam-off" | "speaker" | "switch" | "end" | "gear" | "close";

function CallIcon({ name }: { name: CallIconName }) {
  const common = { width: 22, height: 22, viewBox: "0 0 24 24", fill: "none", xmlns: "http://www.w3.org/2000/svg", "aria-hidden": true };
  switch (name) {
    case "phone":
      return <svg {...common}><path d="M8.1 5.1 10 8.8c.28.55.14 1.22-.34 1.62l-1.15.95a10.2 10.2 0 0 0 4.15 4.13l.94-1.14c.4-.49 1.07-.63 1.63-.35l3.66 1.9c.67.34.96 1.14.66 1.83-.56 1.31-1.9 2.15-3.33 2.02C9.85 19.17 4.83 14.15 4.25 7.78c-.13-1.43.71-2.77 2.02-3.33.69-.3 1.49 0 1.83.66Z" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "video":
      return <svg {...common}><rect x="4" y="7" width="11" height="10" rx="2.2" stroke="currentColor" strokeWidth="1.8"/><path d="m15 10 4.2-2.3c.53-.29 1.18.09 1.18.7v7.2c0 .61-.65.99-1.18.7L15 14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "mic":
      return <svg {...common}><rect x="9" y="3.5" width="6" height="11" rx="3" stroke="currentColor" strokeWidth="1.8"/><path d="M5.8 12.2a6.2 6.2 0 0 0 12.4 0M12 18.4v2.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "mic-off":
      return <svg {...common}><rect x="9" y="3.5" width="6" height="11" rx="3" stroke="currentColor" strokeWidth="1.8"/><path d="M5.8 12.2a6.2 6.2 0 0 0 12.4 0M12 18.4v2.2M4.5 4.5l15 15" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "cam":
      return <svg {...common}><rect x="3.5" y="6.5" width="12" height="11" rx="2.4" stroke="currentColor" strokeWidth="1.8"/><path d="m15.5 10 4-2.2c.5-.27 1.1.08 1.1.65v7.1c0 .57-.6.92-1.1.65l-4-2.2" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/></svg>;
    case "cam-off":
      return <svg {...common}><rect x="3.5" y="6.5" width="12" height="11" rx="2.4" stroke="currentColor" strokeWidth="1.8"/><path d="m15.5 10 4-2.2c.5-.27 1.1.08 1.1.65v7.1c0 .57-.6.92-1.1.65l-4-2.2M4 4l16 16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "speaker":
      return <svg {...common}><path d="M5 10v4h3l4.5 3.6V6.4L8 10H5Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><path d="M16 9.2a4 4 0 0 1 0 5.6M18.4 7a7.4 7.4 0 0 1 0 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
    case "switch":
      return <svg {...common}><path d="M7 8.5h8.5l-2.2-2.2M17 15.5H8.5l2.2 2.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
    case "end":
      return <svg {...common}><path d="M3.5 13.4c5.1-4.4 11.9-4.4 17 0l-1.6 2.6c-.4.65-1.23.9-1.92.58l-2.4-1.1a1.5 1.5 0 0 1-.87-1.36v-1.2a13 13 0 0 0-3.42 0v1.2c0 .58-.34 1.11-.87 1.36l-2.4 1.1c-.69.32-1.52.07-1.92-.58L3.5 13.4Z" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"/></svg>;
    case "gear":
      return <svg {...common}><path d="M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z" stroke="currentColor" strokeWidth="1.8"/><path d="M18.35 13.7c.08-.55.08-.85 0-1.4l1.45-1.1-1.7-2.95-1.7.7a7.1 7.1 0 0 0-1.2-.7L15 6.45h-3.4l-.25 1.8c-.42.18-.82.42-1.2.7l-1.7-.7-1.7 2.95 1.45 1.1c-.08.55-.08.85 0 1.4l-1.45 1.1 1.7 2.95 1.7-.7c.38.28.78.52 1.2.7l.25 1.8H15l.25-1.8c.42-.18.82-.42 1.2-.7l1.7.7 1.7-2.95-1.5-1.1Z" stroke="currentColor" strokeWidth="1.45" strokeLinejoin="round"/></svg>;
    case "close":
      return <svg {...common}><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
  }
}

function callAvatarLabel(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "A";
}

function CallAvatar({ name, photoUrl, large }: { name: string; photoUrl?: string; large?: boolean }) {
  return (
    <span className={`call-avatar ${large ? "large" : ""}`}>
      {photoUrl ? <img src={photoUrl} alt="" /> : callAvatarLabel(name)}
    </span>
  );
}

function useDuration(connectedAt: number) {
  const [, force] = useState(0);
  useEffect(() => {
    if (!connectedAt) return;
    const timer = window.setInterval(() => force((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [connectedAt]);
  if (!connectedAt) return "";
  const seconds = Math.max(0, Math.round((Date.now() - connectedAt) / 1000));
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}

function applySink(element: HTMLMediaElement | null, sinkId: string) {
  if (!element || !sinkId) return;
  const sinkable = element as HTMLMediaElement & { setSinkId?: (id: string) => Promise<void> };
  void sinkable.setSinkId?.(sinkId).catch(() => undefined);
}

// ── Device settings (shared with the Settings page) ──────────────────────────

export function CallDeviceSettings({ engine, inCall, onClose }: { engine?: DesktopCallEngine | null; inCall?: boolean; onClose?: () => void }) {
  const [devices, setDevices] = useState<DeviceLists>({ microphones: [], cameras: [], speakers: [] });
  const [prefs, setPrefs] = useState<DevicePreferences>(() => loadDevicePreferences());
  const [micLevel, setMicLevel] = useState(0);
  const [testing, setTesting] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState("");
  const testStreamRef = useRef<MediaStream | null>(null);
  const meterStopRef = useRef<() => void>(() => undefined);
  const previewStreamRef = useRef<MediaStream | null>(null);
  const previewVideoRef = useRef<HTMLVideoElement | null>(null);

  const refreshDevices = useCallback(() => {
    void listMediaDevices().then(setDevices);
  }, []);

  useEffect(() => {
    refreshDevices();
    return watchDeviceChanges(refreshDevices);
  }, [refreshDevices]);

  useEffect(() => () => {
    meterStopRef.current();
    stopStream(testStreamRef.current);
    stopStream(previewStreamRef.current);
  }, []);

  function persist(next: DevicePreferences) {
    setPrefs(next);
    saveDevicePreferences(next);
    engine?.applyDevicePreferences(next);
  }

  async function toggleMicTest() {
    if (testing) {
      meterStopRef.current();
      stopStream(testStreamRef.current);
      testStreamRef.current = null;
      setTesting(false);
      setMicLevel(0);
      return;
    }
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: prefs.microphoneId ? { deviceId: { ideal: prefs.microphoneId } } : true
      });
      testStreamRef.current = stream;
      meterStopRef.current = meterMicrophone(stream, setMicLevel);
      setTesting(true);
      refreshDevices();
    } catch {
      setError("No microphone found.");
    }
  }

  async function toggleCameraPreview() {
    if (previewing) {
      stopStream(previewStreamRef.current);
      previewStreamRef.current = null;
      if (previewVideoRef.current) previewVideoRef.current.srcObject = null;
      setPreviewing(false);
      return;
    }
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: prefs.cameraId ? { deviceId: { ideal: prefs.cameraId } } : true
      });
      previewStreamRef.current = stream;
      if (previewVideoRef.current) {
        previewVideoRef.current.srcObject = stream;
        void previewVideoRef.current.play().catch(() => undefined);
      }
      setPreviewing(true);
      refreshDevices();
    } catch {
      setError("No camera found.");
    }
  }

  function deviceOptions(list: MediaDeviceInfo[], fallbackLabel: string) {
    return list.map((device, index) => (
      <option key={device.deviceId || index} value={device.deviceId}>
        {device.label || `${fallbackLabel} ${index + 1}`}
      </option>
    ));
  }

  return (
    <section className={`call-device-settings ${inCall ? "in-call" : ""}`} onClick={(event) => event.stopPropagation()}>
      <header>
        <strong>Call devices</strong>
        {onClose ? <button className="call-icon-button" onClick={onClose} aria-label="Close device settings"><CallIcon name="close" /></button> : null}
      </header>
      <label>
        <span>Microphone</span>
        <select value={prefs.microphoneId} onChange={(event) => persist({ ...prefs, microphoneId: event.target.value })}>
          <option value="">System default</option>
          {deviceOptions(devices.microphones, "Microphone")}
        </select>
      </label>
      <label>
        <span>Camera</span>
        <select value={prefs.cameraId} onChange={(event) => persist({ ...prefs, cameraId: event.target.value })}>
          <option value="">System default</option>
          {deviceOptions(devices.cameras, "Camera")}
        </select>
      </label>
      <label>
        <span>Speaker</span>
        <select value={prefs.speakerId} onChange={(event) => persist({ ...prefs, speakerId: event.target.value })}>
          <option value="">System default</option>
          {deviceOptions(devices.speakers, "Speaker")}
        </select>
      </label>
      <div className="device-test-row">
        <button onClick={() => void toggleMicTest()}>{testing ? "Stop mic test" : "Test microphone"}</button>
        <div className="mic-meter" aria-label="Microphone level">
          <i style={{ width: `${Math.round(micLevel * 100)}%` }} />
        </div>
      </div>
      <div className="device-test-row">
        <button onClick={() => void toggleCameraPreview()}>{previewing ? "Stop camera preview" : "Camera preview"}</button>
      </div>
      <video ref={previewVideoRef} className={`camera-preview ${previewing ? "visible" : ""}`} muted playsInline />
      {error ? <p className="call-error-text">{error}</p> : null}
    </section>
  );
}

// ── Video stage with draggable local preview ─────────────────────────────────

function VideoStage({ remoteStream, localStream, speakerId, cameraEnabled, peerName, peerPhotoUrl }: {
  remoteStream: MediaStream | null;
  localStream: MediaStream | null;
  speakerId: string;
  cameraEnabled: boolean;
  peerName: string;
  peerPhotoUrl?: string;
}) {
  const remoteRef = useRef<HTMLVideoElement | null>(null);
  const localRef = useRef<HTMLVideoElement | null>(null);
  const [pip, setPip] = useState({ x: 0, y: 0 });
  const dragRef = useRef<{ startX: number; startY: number; baseX: number; baseY: number; dragging: boolean }>({ startX: 0, startY: 0, baseX: 0, baseY: 0, dragging: false });

  useEffect(() => {
    if (remoteRef.current) {
      remoteRef.current.srcObject = remoteStream;
      if (remoteStream) void remoteRef.current.play().catch(() => undefined);
    }
    applySink(remoteRef.current, speakerId);
  }, [remoteStream, speakerId]);

  useEffect(() => {
    if (localRef.current) {
      localRef.current.srcObject = localStream;
      if (localStream) void localRef.current.play().catch(() => undefined);
    }
  }, [localStream]);

  const remoteHasVideo = Boolean(remoteStream?.getVideoTracks().length);

  return (
    <div className="video-stage">
      <video ref={remoteRef} className={`remote-video ${remoteHasVideo ? "" : "hidden"}`} playsInline autoPlay />
      {!remoteHasVideo ? (
        <div className="remote-placeholder">
          <CallAvatar name={peerName} photoUrl={peerPhotoUrl} large />
        </div>
      ) : null}
      <div
        className="local-pip"
        style={{ transform: `translate(${pip.x}px, ${pip.y}px)` }}
        onPointerDown={(event) => {
          dragRef.current = { startX: event.clientX, startY: event.clientY, baseX: pip.x, baseY: pip.y, dragging: true };
          (event.target as HTMLElement).setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          if (!dragRef.current.dragging) return;
          setPip({
            x: dragRef.current.baseX + (event.clientX - dragRef.current.startX),
            y: dragRef.current.baseY + (event.clientY - dragRef.current.startY)
          });
        }}
        onPointerUp={() => {
          dragRef.current.dragging = false;
        }}
      >
        <video ref={localRef} muted playsInline autoPlay className={cameraEnabled ? "" : "camera-off"} />
        {!cameraEnabled ? <span className="pip-camera-off"><CallIcon name="cam-off" /></span> : null}
      </div>
    </div>
  );
}

// ── Main overlay ─────────────────────────────────────────────────────────────

export function CallLayer({ engine, state, localStream, remoteStream }: {
  engine: DesktopCallEngine;
  state: CallState;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
}) {
  const duration = useDuration(state.connectedAt);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [showDevices, setShowDevices] = useState(false);
  const [cameras, setCameras] = useState<MediaDeviceInfo[]>([]);
  const hideTimerRef = useRef(0);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);

  const isVideo = state.type === "video";
  const inActiveCall = state.phase === "connected" || state.phase === "reconnecting" || state.phase === "connecting";
  const endLabel = state.isCaller && !state.connectedAt && (state.phase === "calling" || state.phase === "ringing") ? "Cancel" : "End";

  // Voice calls: remote audio element (video calls play audio through the video element).
  useEffect(() => {
    if (isVideo) return;
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = remoteStream;
      if (remoteStream) void remoteAudioRef.current.play().catch(() => undefined);
    }
    applySink(remoteAudioRef.current, state.speakerId);
  }, [remoteStream, state.speakerId, isVideo]);

  useEffect(() => {
    void listMediaDevices().then((lists) => setCameras(lists.cameras));
  }, []);

  // Auto-hide controls during a connected video call; reappear on mouse move.
  const pokeControls = useCallback(() => {
    setControlsVisible(true);
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    hideTimerRef.current = window.setTimeout(() => setControlsVisible(false), 3200);
  }, []);

  useEffect(() => {
    if (isVideo && state.phase === "connected") pokeControls();
    else setControlsVisible(true);
    return () => {
      if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    };
  }, [isVideo, state.phase, pokeControls]);

  const statusLine = useMemo(() => {
    if (state.phase === "connected") return duration;
    return state.statusText;
  }, [state.phase, state.statusText, duration]);

  // ── Incoming call modal ──
  if (state.phase === "incoming") {
    return (
      <div className="call-shell" role="dialog" aria-modal="true" aria-label="Incoming call">
        <section className="incoming-card">
          <CallAvatar name={state.peerName} photoUrl={state.peerPhotoUrl} large />
          <h2>{state.peerName}</h2>
          <p className="incoming-type">
            <CallIcon name={isVideo ? "video" : "phone"} />
            {isVideo ? "Incoming video call" : "Incoming voice call"}
          </p>
          <div className="incoming-actions">
            <button className="round-action decline" onClick={() => engine.decline()} aria-label="Decline call">
              <CallIcon name="end" />
              <small>Decline</small>
            </button>
            <button className="round-action answer" onClick={() => void engine.accept()} aria-label="Answer call">
              <CallIcon name={isVideo ? "video" : "phone"} />
              <small>Answer</small>
            </button>
          </div>
        </section>
      </div>
    );
  }

  // ── Permission pre-prompt / permission errors ──
  if (state.phase === "permissions" || state.phase === "permission-error") {
    const denied = state.phase === "permission-error";
    return (
      <div className="call-shell" role="dialog" aria-modal="true" aria-label="Call permissions">
        <section className="permission-card">
          <span className={`permission-icon ${denied ? "denied" : ""}`}>
            <CallIcon name={isVideo ? "cam" : "mic"} />
          </span>
          <h2>{denied ? "Permission needed" : isVideo ? "Camera & microphone" : "Microphone"}</h2>
          <p>
            {denied
              ? state.permissionError?.message || state.statusText
              : isVideo
                ? "AdisChat needs your camera and microphone to start this video call. Choose Allow when your system asks."
                : "AdisChat needs your microphone to start this voice call. Choose Allow when your system asks."}
          </p>
          {denied ? (
            <div className="permission-actions">
              <button
                className="soft-button"
                onClick={() => openDevicePermissionSettings(state.permissionError?.code === "no-camera" || isVideo ? "camera" : "microphone")}
              >
                Check device permissions
              </button>
              <button className="ghost-button" onClick={() => engine.dismiss()}>Close</button>
            </div>
          ) : (
            <div className="permission-actions">
              <span className="permission-waiting">Waiting for permission...</span>
              <button className="ghost-button" onClick={() => engine.end()}>Cancel</button>
            </div>
          )}
        </section>
      </div>
    );
  }

  // ── Active video call ──
  if (isVideo && inActiveCall) {
    return (
      <div className="call-shell video-call" onMouseMove={pokeControls} role="dialog" aria-modal="true" aria-label="Video call">
        <VideoStage
          remoteStream={remoteStream}
          localStream={localStream}
          speakerId={state.speakerId}
          cameraEnabled={state.cameraEnabled}
          peerName={state.peerName}
          peerPhotoUrl={state.peerPhotoUrl}
        />
        <header className={`call-top-bar ${controlsVisible ? "" : "faded"}`}>
          <div>
            <strong>{state.peerName}</strong>
            <span>{state.phase === "connected" ? duration : statusLine}</span>
          </div>
          <span className="participant-pill">{state.participantCount} in call</span>
          {state.quality === "poor" ? <span className="quality-banner">Poor connection</span> : null}
          {state.phase === "reconnecting" ? <span className="quality-banner">Reconnecting...</span> : null}
          {state.phase === "connecting" ? <span className="quality-banner soft">Connecting...</span> : null}
        </header>
        <footer className={`call-controls ${controlsVisible ? "" : "faded"}`}>
          <button className={`control ${state.muted ? "active" : ""}`} onClick={() => engine.toggleMute()} aria-label={state.muted ? "Unmute" : "Mute"}>
            <CallIcon name={state.muted ? "mic-off" : "mic"} />
            <small>{state.muted ? "Unmute" : "Mute"}</small>
          </button>
          <button className={`control ${state.cameraEnabled ? "" : "active"}`} onClick={() => engine.toggleCamera()} aria-label={state.cameraEnabled ? "Turn camera off" : "Turn camera on"}>
            <CallIcon name={state.cameraEnabled ? "cam" : "cam-off"} />
            <small>Camera</small>
          </button>
          {cameras.length > 1 ? (
            <button
              className="control"
              onClick={() => {
                const current = localStream?.getVideoTracks()[0]?.getSettings().deviceId || "";
                const index = cameras.findIndex((camera) => camera.deviceId === current);
                const next = cameras[(index + 1) % cameras.length];
                if (next) void engine.switchCamera(next.deviceId);
              }}
              aria-label="Switch camera"
            >
              <CallIcon name="switch" />
              <small>Flip</small>
            </button>
          ) : null}
          <button className="control" onClick={() => setShowDevices((value) => !value)} aria-label="Audio and video devices">
            <CallIcon name="speaker" />
            <small>Devices</small>
          </button>
          <button className="control end" onClick={() => engine.end()} aria-label="End call">
            <CallIcon name="end" />
            <small>End</small>
          </button>
        </footer>
        {showDevices ? <CallDeviceSettings engine={engine} inCall onClose={() => setShowDevices(false)} /> : null}
      </div>
    );
  }

  // ── Voice call (outgoing, active, and all end states) ──
  const terminal = ["ended", "rejected", "missed", "failed", "offline"].includes(state.phase);
  return (
    <div className="call-shell" role="dialog" aria-modal="true" aria-label={isVideo ? "Video call" : "Voice call"}>
      <audio ref={remoteAudioRef} autoPlay hidden />
      <section className={`voice-card ${terminal ? "terminal" : ""}`}>
        <CallAvatar name={state.peerName} photoUrl={state.peerPhotoUrl} large />
        <h2>{state.peerName}</h2>
        <p className="incoming-type">
          <CallIcon name={isVideo ? "video" : "phone"} />
          {isVideo ? "Video call" : "Voice call"}
        </p>
        <p className="voice-status">
          {state.phase === "connected" ? duration : statusLine}
          {state.phase === "connected" && state.quality === "poor" ? <span className="quality-inline">Poor connection</span> : null}
        </p>
        {state.phase === "reconnecting" ? <p className="quality-banner">Reconnecting...</p> : null}
        {state.phase === "connected" ? <span className="participant-pill">{state.participantCount} in call</span> : null}
        {terminal ? (
          <div className="voice-controls">
            <button className="control" onClick={() => engine.dismiss()} aria-label="Close">
              <CallIcon name="close" />
              <small>Close</small>
            </button>
          </div>
        ) : (
          <div className="voice-controls">
            {inActiveCall ? (
              <>
                <button className={`control ${state.muted ? "active" : ""}`} onClick={() => engine.toggleMute()} aria-label={state.muted ? "Unmute" : "Mute"}>
                  <CallIcon name={state.muted ? "mic-off" : "mic"} />
                  <small>{state.muted ? "Unmute" : "Mute"}</small>
                </button>
                <button className="control" onClick={() => setShowDevices((value) => !value)} aria-label="Speaker and devices">
                  <CallIcon name="speaker" />
                  <small>Speaker</small>
                </button>
              </>
            ) : null}
            <button className="control end" onClick={() => engine.end()} aria-label={`${endLabel} call`}>
              <CallIcon name="end" />
              <small>{endLabel}</small>
            </button>
          </div>
        )}
      </section>
      {showDevices ? <CallDeviceSettings engine={engine} inCall onClose={() => setShowDevices(false)} /> : null}
    </div>
  );
}
