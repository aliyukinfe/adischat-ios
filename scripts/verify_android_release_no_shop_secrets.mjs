import {
  copyFileSync,
  existsSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { basename, join, resolve } from "node:path";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(fileURLToPath(import.meta.url), "../..");

const defaultTargets = [
  join(repoRoot, "android/app/build/outputs/apk/release/app-release.apk"),
  join(repoRoot, "android/app/build/outputs/bundle/release/app-release.aab"),
];

const args = process.argv.slice(2);
let targets = args.length ? args.map((a) => resolve(repoRoot, a)) : defaultTargets.filter(existsSync);

if (!args.length && targets.length === 0) {
  const sourceRoot = join(repoRoot, "android/app/src/main");
  if (existsSync(sourceRoot)) {
    console.log("No compiled release bundle found; scanning release source tree: " + sourceRoot);
    targets = [sourceRoot];
  }
}

const secretEnvNames = [
  "ADISHOP_SUPABASE_SECRET_KEY",
  "ADISHOP_ASSERTION_SECRET",
  "SUPABASE_SERVICE_ROLE_KEY",
  "ADISHOP_SUPABASE_SERVICE_ROLE_KEY",
  "SUPABASE_DB_PASSWORD",
  "CLOUDFLARE_API_TOKEN",
  "GITHUB_TOKEN",
  "GH_TOKEN",
];

const literalSecretValues = secretEnvNames
  .map((name) => [name, process.env[name] || ""])
  .filter(([, value]) => value.length >= 16);

const credentialPatterns = [
  {
    name: "Supabase sb_secret key",
    pattern: /sb_secret_[A-Za-z0-9_-]+/g,
  },
  {
    name: "Postgres connection string",
    pattern: /postgres(?:ql)?:\/\/[^"'\s<>]+/gi,
  },
  {
    name: "Supabase personal access token",
    pattern: /supabase_pat_[A-Za-z0-9_-]+/g,
  },
  {
    name: "PEM private key block",
    pattern: /-----BEGIN (?:RSA |EC |OPENSSH |PRIVATE )?PRIVATE KEY-----[\s\S]{32,}?-----END (?:RSA |EC |OPENSSH |PRIVATE )?PRIVATE KEY-----/g,
  },
  {
    name: "OAuth client secret",
    pattern: /client_secret["'\s:=]+[A-Za-z0-9_.~+/-]{20,}/gi,
  },
  {
    name: "Refresh token",
    pattern: /refresh_token["'\s:=]+[A-Za-z0-9_.~+/-]{30,}/gi,
  },
  {
    name: "GitHub token",
    pattern: /gh[pousr]_[A-Za-z0-9_]{30,}/g,
  },
];

const jwtPattern = /eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{8,}/g;

function shaPrefix(value) {
  return createHash("sha256").update(value).digest("hex").slice(0, 12);
}

function walk(path) {
  const stat = statSync(path);
  if (!stat.isDirectory()) return [path];

  const out = [];
  for (const entry of readdirSync(path)) {
    out.push(...walk(join(path, entry)));
  }
  return out;
}

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return Buffer.from(padded, "base64").toString("utf8");
}

function isSupabaseServiceRoleJwt(jwt) {
  try {
    const payload = JSON.parse(decodeBase64Url(jwt.split(".")[1] || ""));
    return payload && payload.role === "service_role";
  } catch {
    return false;
  }
}

function scanText(file, text, findings) {
  for (const [name, value] of literalSecretValues) {
    if (text.includes(value)) {
      findings.push({
        file,
        reason: `${name} literal value`,
        hash: shaPrefix(value),
      });
    }
  }

  for (const jwt of text.match(jwtPattern) || []) {
    if (isSupabaseServiceRoleJwt(jwt)) {
      findings.push({
        file,
        reason: "Supabase service-role JWT",
        hash: shaPrefix(jwt),
      });
    }
  }

  for (const { name, pattern } of credentialPatterns) {
    pattern.lastIndex = 0;
    for (const match of text.matchAll(pattern)) {
      findings.push({
        file,
        reason: name,
        hash: shaPrefix(match[0]),
      });
    }
  }
}

function scanFile(path, findings) {
  const data = readFileSync(path);
  scanText(path, data.toString("latin1"), findings);
  if (data.includes(Buffer.from("service_role"))) {
    console.warn(`Generic role text found, not a credential by itself: ${path}`);
  }
}

function expandArchive(path, temp) {
  const archivePath = join(temp, basename(path).replace(/[^A-Za-z0-9_.-]/g, "_") + ".zip");
  copyFileSync(path, archivePath);
  execFileSync("powershell", [
    "-NoProfile",
    "-Command",
    `Expand-Archive -LiteralPath '${archivePath.replace(/'/g, "''")}' -DestinationPath '${temp.replace(/'/g, "''")}' -Force`,
  ]);
}

const findings = [];

for (const target of targets) {
  const path = resolve(target);
  if (!existsSync(path)) {
    throw new Error(`Release scan target not found: ${path}`);
  }

  for (const file of walk(path)) scanFile(file, findings);

  if (statSync(path).isFile() && /\.(apk|aab|zip|jar)$/i.test(path)) {
    const temp = mkdtempSync(join(tmpdir(), "adishop-secret-scan-"));
    try {
      expandArchive(path, temp);
      for (const file of walk(temp)) scanFile(file, findings);
    } finally {
      rmSync(temp, { recursive: true, force: true });
    }
  }
}

if (findings.length) {
  for (const finding of findings) {
    console.error(`${finding.reason} in ${finding.file} hash=${finding.hash}`);
  }
  throw new Error(`Android release credential scan failed with ${findings.length} finding(s).`);
}

console.log("Android release credential scan passed.");
