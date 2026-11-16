const TYPE_FOLDERS = {
  avatar: "avatars",
  image: "images",
  video: "videos",
  voice: "voices",
  audio: "audio",
  document: "documents",
  file: "files",
  verification: "verification",
};

function safeType(value) {
  const normalized = (value || "file").toString().toLowerCase().replace(/[^a-z0-9_-]/g, "");
  return normalized || "file";
}

function extensionFromMime(mime) {
  const clean = (mime || "").split(";")[0].trim().toLowerCase();
  const map = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
    "image/gif": "gif",
    "video/mp4": "mp4",
    "audio/mpeg": "mp3",
    "audio/mp4": "m4a",
    "audio/aac": "aac",
    "audio/ogg": "ogg",
    "application/pdf": "pdf",
  };
  return map[clean] || "bin";
}

function mediaUrl(request, key, env) {
  const base = (env.MEDIA_PUBLIC_BASE_URL || "").replace(/\/$/, "");
  if (base) return `${base}/${key}`;
  const url = new URL(request.url);
  return `${url.origin}/api/media/${key}`;
}

async function updateAvatarRecord(env, uid, url) {
  const statements = [
    { sql: "UPDATE users SET photo_url = ?, updated_at = ? WHERE uid = ?", params: [url, Date.now(), uid] },
    { sql: "UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?", params: [url, Date.now(), uid] },
    { sql: "UPDATE profiles SET photo_url = ?, updated_at = ? WHERE user_id = ?", params: [url, Date.now(), uid] },
  ];

  for (const statement of statements) {
    try {
      await env.DB.prepare(statement.sql).bind(...statement.params).run();
      return;
    } catch {
      // Profile schemas differ across deployments; the returned R2 URL remains valid.
    }
  }
}

async function putObject(request, env, uid, typeOverride = null) {
  if (!env.MEDIA) return Response.json({ error: "R2 MEDIA binding is not configured" }, { status: 500 });

  const url = new URL(request.url);
  const type = safeType(typeOverride || url.searchParams.get("type"));
  const folder = TYPE_FOLDERS[type] || TYPE_FOLDERS.file;
  const contentType = request.headers.get("content-type") || "application/octet-stream";
  const bytes = await request.arrayBuffer();
  if (!bytes.byteLength) return Response.json({ error: "Upload body is empty" }, { status: 400 });

  const ext = extensionFromMime(contentType);
  const key = `${folder}/${uid}/${Date.now().toString(36)}-${crypto.randomUUID()}.${ext}`;
  await env.MEDIA.put(key, bytes, {
    httpMetadata: { contentType },
    customMetadata: { uid, type },
  });

  return { key, url: mediaUrl(request, key, env), contentType, size: bytes.byteLength };
}

async function readObject(request, env, key) {
  if (!env.MEDIA) return Response.json({ error: "R2 MEDIA binding is not configured" }, { status: 500 });

  const object = await env.MEDIA.get(key);
  if (!object) return Response.json({ error: "Media not found" }, { status: 404 });

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Cache-Control", "public, max-age=31536000, immutable");
  headers.set("ETag", object.httpEtag);
  return new Response(object.body, { headers });
}

export async function handleStorageRequest(request, env, user) {
  const url = new URL(request.url);

  if (request.method === "POST" && url.pathname === "/api/upload") {
    const uploaded = await putObject(request, env, user.uid);
    return Response.json({ url: uploaded.url, key: uploaded.key });
  }

  const avatarMatch = url.pathname.match(/^\/api\/users\/([^/]+)\/avatar$/);
  if (request.method === "POST" && avatarMatch) {
    const uid = decodeURIComponent(avatarMatch[1]);
    if (uid !== user.uid && !["admin", "owner"].includes((user.role || "").toLowerCase())) {
      return Response.json({ error: "Cannot update another user's avatar" }, { status: 403 });
    }
    const uploaded = await putObject(request, env, uid, "avatar");
    await updateAvatarRecord(env, uid, uploaded.url);
    return Response.json({ photoUrl: uploaded.url, key: uploaded.key });
  }

  const mediaMatch = url.pathname.match(/^\/(?:api\/)?media\/(.+)$/);
  if (["GET", "HEAD"].includes(request.method) && mediaMatch) {
    return readObject(request, env, decodeURIComponent(mediaMatch[1]));
  }

  return null;
}

