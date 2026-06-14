import { initializeApp, getApps } from "firebase/app";
import {
  getAuth,
  onAuthStateChanged,
  signInWithCustomToken,
  signInWithEmailAndPassword,
  signOut,
  type User as FirebaseUser
} from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || "AIzaSyAfb0xe12Pnb5DA_qRFG03HjDgxxB8FXZU",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || "adischat-f4a77.firebaseapp.com",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "adischat-f4a77",
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || "adischat-f4a77.firebasestorage.app",
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || "75418741614",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || "1:75418741614:web:adischatdesktop"
};

const app = getApps().length ? getApps()[0] : initializeApp(firebaseConfig);
export const auth = getAuth(app);

export function observeAuth(callback: (user: FirebaseUser | null) => void) {
  return onAuthStateChanged(auth, callback);
}

export async function loginWithEmail(email: string, password: string) {
  return signInWithEmailAndPassword(auth, email.trim(), password);
}

/**
 * If the QR-approval token is a Firebase custom token, exchanging it for a real
 * Firebase session means every backend worker (main + community/wallet) accepts
 * the resulting ID token — no separate desktop-token validation needed. Returns
 * { ok: true } on success; on failure returns the Firebase error code so callers
 * can tell "not a custom token" from a transient/network failure, then fall back
 * to the opaque desktop session token.
 */
export async function trySignInWithCustomToken(token: string): Promise<{ ok: boolean; errorCode?: string }> {
  try {
    await signInWithCustomToken(auth, token);
    return { ok: true };
  } catch (error) {
    return { ok: false, errorCode: (error as { code?: string })?.code || "unknown" };
  }
}

/**
 * Safely decodes a token's non-secret claims for diagnostics. A Firebase custom
 * token is a 3-part JWT whose `aud` is the Identity Toolkit URL — that's how we
 * know the exchange above can succeed. Never logs the signature.
 */
export function describeToken(token: string): Record<string, unknown> {
  if (!token) return { present: false };
  const parts = token.split(".");
  if (parts.length !== 3) return { jwt: false, length: token.length };
  const decode = (segment: string) => {
    try {
      return JSON.parse(atob(segment.replace(/-/g, "+").replace(/_/g, "/")));
    } catch {
      return null;
    }
  };
  const header = decode(parts[0]) as { alg?: string } | null;
  const payload = decode(parts[1]) as { aud?: string; iss?: string; uid?: string; sub?: string } | null;
  const aud = payload?.aud || "";
  return {
    jwt: true,
    alg: header?.alg,
    aud,
    iss: payload?.iss,
    hasUid: Boolean(payload?.uid || payload?.sub),
    looksLikeFirebaseCustomToken: typeof aud === "string" && aud.includes("identitytoolkit")
  };
}

export function getDesktopSessionToken() {
  if (typeof window === "undefined") return "";
  try {
    const value = window.localStorage.getItem("desktop:session");
    return value ? (JSON.parse(value) as { token?: string }).token || "" : "";
  } catch {
    return "";
  }
}

export function saveDesktopSession(session: { token: string; userId?: string; expiresAt?: number }) {
  if (typeof window === "undefined") return;
  // TODO: Move to a Tauri keychain/secure-store plugin once the binding is added.
  window.localStorage.setItem("desktop:session", JSON.stringify(session));
}

export function clearDesktopSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem("desktop:session");
}

export async function logout() {
  clearDesktopSession();
  return signOut(auth);
}

export async function getAuthToken(forceRefresh = false) {
  const firebaseToken = await auth.currentUser?.getIdToken(forceRefresh);
  return firebaseToken || getDesktopSessionToken();
}