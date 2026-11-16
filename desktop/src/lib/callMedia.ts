import { readCache, writeCache } from "./cache";

export type MediaErrorCode =
  | "denied"
  | "no-microphone"
  | "no-camera"
  | "device-busy"
  | "not-available"
  | "unknown";

export type MediaResult =
  | { ok: true; stream: MediaStream }
  | { ok: false; code: MediaErrorCode; message: string };

export type DevicePreferences = {
  microphoneId: string;
  cameraId: string;
  speakerId: string;
};

export type DeviceLists = {
  microphones: MediaDeviceInfo[];
  cameras: MediaDeviceInfo[];
  speakers: MediaDeviceInfo[];
};

const DEVICE_PREFS_KEY = "desktop:callDevices";

function mediaLog(message: string, details: Record<string, unknown> = {}) {
  console.info(`[desktop-media] ${message}`, details);
}

export function loadDevicePreferences(): DevicePreferences {
  return readCache<DevicePreferences>(DEVICE_PREFS_KEY, { microphoneId: "", cameraId: "", speakerId: "" });
}

export function saveDevicePreferences(prefs: DevicePreferences) {
  writeCache(DEVICE_PREFS_KEY, prefs);
}

export function mediaErrorMessage(code: MediaErrorCode, video: boolean): string {
  switch (code) {
    case "denied":
      return video
        ? "Camera and microphone permission are required for video calls."
        : "Microphone permission is required for voice calls.";
    case "no-camera":
      return "No camera found.";
    case "no-microphone":
      return "No microphone found.";
    case "device-busy":
      return "Your camera or microphone is already being used by another app.";
    case "not-available":
      return "Camera and microphone are not available on this device.";
    default:
      return video ? "Could not access camera and microphone." : "Could not access microphone.";
  }
}

function classifyMediaError(error: unknown, video: boolean): { code: MediaErrorCode; message: string } {
  const name = error instanceof DOMException ? error.name : "";
  let code: MediaErrorCode = "unknown";
  if (name === "NotAllowedError" || name === "PermissionDeniedError" || name === "SecurityError") code = "denied";
  else if (name === "NotFoundError" || name === "DevicesNotFoundError" || name === "OverconstrainedError") {
    code = video ? "no-camera" : "no-microphone";
  } else if (name === "NotReadableError" || name === "TrackStartError" || name === "AbortError") code = "device-busy";
  else if (name === "TypeError") code = "not-available";
  return { code, message: mediaErrorMessage(code, video) };
}

/**
 * Requests microphone (voice) or microphone + camera (video) access.
 * Falls back to default devices when a saved device id no longer exists.
 */
export async function requestCallMedia(type: "voice" | "video", prefs?: DevicePreferences): Promise<MediaResult> {
  mediaLog("media request starting", {
    type,
    hasMediaDevices: typeof navigator !== "undefined" && Boolean(navigator.mediaDevices),
    hasGetUserMedia: typeof navigator !== "undefined" && Boolean(navigator.mediaDevices?.getUserMedia),
  });
  if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
    mediaLog("media request unavailable", { type });
    return { ok: false, code: "not-available", message: mediaErrorMessage("not-available", type === "video") };
  }
  const selected = prefs || loadDevicePreferences();
  const audio: MediaTrackConstraints = { echoCancellation: true, noiseSuppression: true, autoGainControl: true };
  if (selected.microphoneId) audio.deviceId = { ideal: selected.microphoneId };
  const video: MediaTrackConstraints | false =
    type === "video"
      ? selected.cameraId
        ? { deviceId: { ideal: selected.cameraId }, width: { ideal: 1280 }, height: { ideal: 720 } }
        : { width: { ideal: 1280 }, height: { ideal: 720 } }
      : false;
  mediaLog("getUserMedia requested", {
    type,
    audio: true,
    video: Boolean(video),
    hasPreferredMicrophone: Boolean(selected.microphoneId),
    hasPreferredCamera: Boolean(selected.cameraId),
  });
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio, video });
    mediaLog("getUserMedia granted", {
      type,
      audioTracks: stream.getAudioTracks().length,
      videoTracks: stream.getVideoTracks().length,
    });
    return { ok: true, stream };
  } catch (error) {
    mediaLog("getUserMedia failed", {
      type,
      errorName: error instanceof DOMException ? error.name : "unknown",
      retryingWithDefaults: Boolean(selected.microphoneId || selected.cameraId),
    });
    // A stale saved device id can produce OverconstrainedError — retry with defaults once.
    if (selected.microphoneId || selected.cameraId) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
          video: type === "video" ? { width: { ideal: 1280 }, height: { ideal: 720 } } : false
        });
        mediaLog("getUserMedia retry granted", {
          type,
          audioTracks: stream.getAudioTracks().length,
          videoTracks: stream.getVideoTracks().length,
        });
        return { ok: true, stream };
      } catch (retryError) {
        mediaLog("getUserMedia retry failed", {
          type,
          errorName: retryError instanceof DOMException ? retryError.name : "unknown",
        });
        return { ok: false, ...classifyMediaError(retryError, type === "video") };
      }
    }
    return { ok: false, ...classifyMediaError(error, type === "video") };
  }
}

export async function listMediaDevices(): Promise<DeviceLists> {
  if (typeof navigator === "undefined" || !navigator.mediaDevices?.enumerateDevices) {
    return { microphones: [], cameras: [], speakers: [] };
  }
  const devices = await navigator.mediaDevices.enumerateDevices();
  return {
    microphones: devices.filter((device) => device.kind === "audioinput"),
    cameras: devices.filter((device) => device.kind === "videoinput"),
    speakers: devices.filter((device) => device.kind === "audiooutput")
  };
}

export function watchDeviceChanges(onChange: () => void): () => void {
  if (typeof navigator === "undefined" || !navigator.mediaDevices?.addEventListener) return () => undefined;
  navigator.mediaDevices.addEventListener("devicechange", onChange);
  return () => navigator.mediaDevices.removeEventListener("devicechange", onChange);
}

export function stopStream(stream: MediaStream | null | undefined) {
  stream?.getTracks().forEach((track) => track.stop());
}

/**
 * Live microphone input meter. Returns a stop function; calls onLevel with 0..1.
 */
export function meterMicrophone(stream: MediaStream, onLevel: (level: number) => void): () => void {
  const AudioContextCtor =
    typeof AudioContext !== "undefined"
      ? AudioContext
      : (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextCtor) return () => undefined;
  const context = new AudioContextCtor();
  const source = context.createMediaStreamSource(stream);
  const analyser = context.createAnalyser();
  analyser.fftSize = 512;
  source.connect(analyser);
  const data = new Uint8Array(analyser.frequencyBinCount);
  let raf = 0;
  const tick = () => {
    analyser.getByteTimeDomainData(data);
    let sum = 0;
    for (let i = 0; i < data.length; i += 1) {
      const value = (data[i] - 128) / 128;
      sum += value * value;
    }
    onLevel(Math.min(1, Math.sqrt(sum / data.length) * 3));
    raf = window.requestAnimationFrame(tick);
  };
  raf = window.requestAnimationFrame(tick);
  return () => {
    window.cancelAnimationFrame(raf);
    source.disconnect();
    void context.close();
  };
}

/**
 * Best-effort shortcut to OS privacy settings (works inside the Windows
 * WebView2/Tauri shell). Falls back to returning false so the caller can show
 * inline instructions instead.
 */
export function openDevicePermissionSettings(kind: "microphone" | "camera"): boolean {
  try {
    const target = kind === "camera" ? "ms-settings:privacy-webcam" : "ms-settings:privacy-microphone";
    window.location.href = target;
    return true;
  } catch {
    return false;
  }
}
