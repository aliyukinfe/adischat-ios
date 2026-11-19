// AdisChat — AUTH-1: Google Sign-In & Authentication Verification Suite
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');

let total = 0;
let passed = 0;

function test(name, fn) {
  total++;
  try {
    fn();
    passed++;
    console.log(`  [PASS] ${name}`);
  } catch (err) {
    console.error(`  [FAIL] ${name}: ${err.message}`);
  }
}

console.log('=== AdisChat AUTH-1: Authentication & Google Sign-In Verification ===\n');

console.log('--- 1. Google Services & Firebase Configuration Tests ---');

test('google-services.json matches com.adischat.app and adischat-f4a77 project', () => {
  const gsPath = path.join(REPO_ROOT, 'android/app/google-services.json');
  assert.ok(fs.existsSync(gsPath), 'google-services.json missing');
  const gs = JSON.parse(fs.readFileSync(gsPath, 'utf8'));
  assert.equal(gs.project_info.project_id, 'adischat-f4a77');
  const client = gs.client.find(c => c.client_info.android_client_info.package_name === 'com.adischat.app');
  assert.ok(client, 'Client com.adischat.app not configured');

  const webOAuth = client.oauth_client.find(o => o.client_type === 3);
  assert.ok(webOAuth, 'Web OAuth client missing in google-services.json');
  assert.ok(webOAuth.client_id.endsWith('.apps.googleusercontent.com'), 'Invalid Web OAuth Client ID');
});

test('Release workflow enforces Google Play upload key fingerprint', () => {
  const wfPath = path.join(REPO_ROOT, '.github/workflows/android-release-candidate.yml');
  const wf = fs.readFileSync(wfPath, 'utf8');
  assert.ok(wf.includes('81:0A:9F:57:3D:65:60:DB:05:30:26:63:29:A9:1B:0F:D2:CB:B7:3E:2E:0C:D2:3A:9F:81:3B:CB:03:8A:B1:08'));
});

console.log('\n--- 2. AuthScreen & UI Integration Tests ---');

test('AuthScreen.kt integrates Google Sign-In across Welcome, SignUp, and Login pages', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/ui/auth/AuthScreen.kt'), 'utf8');
  assert.ok(code.includes('onGoogle      = { launchGoogle() }'), 'WelcomePage missing onGoogle');
  assert.ok(code.includes('AuthPage.SIGNUP -> SignUpPage('), 'SignUpPage missing');
  assert.ok(code.includes('AuthPage.LOGIN -> LoginPage('), 'LoginPage missing');
  assert.ok(code.includes('"Sign up with Google"'), 'Sign up with Google button missing');
  assert.ok(code.includes('"Sign in with Google"'), 'Sign in with Google button missing');
  assert.ok(code.includes('"Continue with Google"'), 'Continue with Google button missing');
});

test('AuthScreen.kt handles cancellation gracefully without false error banners', () => {
  const code = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/ui/auth/AuthScreen.kt'), 'utf8');
  assert.ok(code.includes('GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null'), 'Cancellation not silenced');
  assert.ok(code.includes('vm.resetState()'), 'resetState on cancel missing');
  assert.ok(code.includes('Developer Error 10'), 'Clear Developer Error 10 diagnostic message missing');
});

console.log('\n--- 3. Backend & Repository Compatibility Tests ---');

test('AuthRepository.kt cleanly signs in with Google and provisions user profile to Cloudflare/D1', () => {
  const repo = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/data/repository/AuthRepository.kt'), 'utf8');
  assert.ok(repo.includes('suspend fun signInWithGoogle(idToken: String): Result<AppUser>'));
  assert.ok(repo.includes('GoogleAuthProvider.getCredential(idToken, null)'));
  assert.ok(repo.includes('auth.signInWithCredential(credential).await()'));
  assert.ok(repo.includes('getCurrentAppUser() ?: syncUserToD1(fbUser)'));
  assert.ok(repo.includes('assertSameAccount(fbUser.uid, it)'));
});

test('SessionSecurity.kt completely signs out of Google and clears Supabase/Restaurants sessions', () => {
  const sec = fs.readFileSync(path.join(REPO_ROOT, 'android/app/src/main/java/com/adischat/app/security/SessionSecurity.kt'), 'utf8');
  assert.ok(sec.includes('GoogleSignIn.getClient(appContext, gso).signOut().await()'));
  assert.ok(sec.includes('FirebaseAuth.getInstance().signOut()'));
  assert.ok(sec.includes('ShopSessionMemory.finishSignOut()'));
  assert.ok(sec.includes('RestaurantsSessionMemory.finishSignOut()'));
});

console.log(`\nVerification complete: ${passed}/${total} tests passed.`);
if (passed !== total) process.exit(1);
