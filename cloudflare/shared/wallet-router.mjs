const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8" };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
const now = () => Date.now();
const id = (prefix) => `${prefix}_${Date.now().toString(36)}_${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;

async function bodyJson(request) {
  try { return await request.json(); } catch { return {}; }
}

function clean(value, max = 1000) {
  return String(value ?? "").trim().slice(0, max);
}

function cents(amount) {
  return Math.round(Number(amount || 0) * 100);
}

function amount(centsValue) {
  return Number(centsValue || 0) / 100;
}

function walletJson(row, user) {
  const balanceCents = Number(row?.balance_cents || 0);
  return {
    uid: user.uid,
    userId: user.uid,
    walletId: row?.wallet_id || "",
    username: row?.username || "",
    phone: row?.phone || "",
    email: row?.email || user.email || "",
    name: row?.display_name || user.name || "",
    balance: amount(balanceCents),
    currency: row?.currency || "ETB",
    active: Number(row?.active || 0) === 1,
    frozen: Number(row?.frozen || 0) === 1,
    walletPinSet: Boolean(row?.pin_hash),
    activatedAt: row?.activated_at || null,
  };
}

function txJson(row) {
  return {
    id: row.id || "",
    wallet_id: row.wallet_id || "",
    counterparty_wallet_id: row.counterparty_wallet_id || "",
    counterparty_uid: row.counterparty_uid || "",
    counterparty_name: row.counterparty_name || "",
    counterparty_username: row.counterparty_username || "",
    type: row.type || "",
    status: row.status || "",
    amount_cents: Number(row.amount_cents || 0),
    amount: amount(row.amount_cents),
    currency: row.currency || "ETB",
    reference: row.reference || "",
    note: row.note || "",
    created_at: Number(row.created_at || 0),
  };
}

async function getWallet(env, uid) {
  return env.DB.prepare("SELECT * FROM wallets WHERE uid=?").bind(uid).first();
}

async function ensureWallet(env, user, body = {}) {
  let row = await getWallet(env, user.uid);
  if (row) return row;
  const t = now();
  const username = clean(body.username || user.email?.split("@")[0] || user.name || `user_${user.uid.slice(0, 6)}`, 60).toLowerCase().replace(/[^a-z0-9_]/g, "_");
  const walletId = `adis_${user.uid.replace(/[^a-zA-Z0-9]/g, "").slice(0, 10)}_${Math.floor(1000 + Math.random() * 9000)}`;
  await env.DB.prepare(`INSERT INTO wallets(uid, wallet_id, username, phone, email, display_name, balance_cents, currency, active, frozen, pin_hash, activated_at, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, 0, 'ETB', 0, 0, NULL, NULL, ?, ?)`)
    .bind(user.uid, walletId, username || `user_${user.uid.slice(0, 6)}`, clean(body.phone, 64), clean(body.email || user.email, 160), clean(body.name || user.name, 120), t, t).run();
  return getWallet(env, user.uid);
}

async function recordTx(env, wallet, type, amountCents, note = "", counterparty = {}) {
  const t = now();
  const reference = id("ref");
  const txId = id("wtx");
  await env.DB.prepare(`INSERT INTO wallet_transactions(id, wallet_id, uid, counterparty_wallet_id, counterparty_uid, type, status, amount_cents, currency, reference, note, metadata, created_at)
    VALUES (?, ?, ?, ?, ?, ?, 'completed', ?, ?, ?, ?, '{}', ?)`)
    .bind(txId, wallet.wallet_id, wallet.uid, counterparty.wallet_id || "", counterparty.uid || "", type, amountCents, wallet.currency || "ETB", reference, note, t).run();
  return { id: txId, reference };
}

export async function handleWalletRequest(request, env, user) {
  const url = new URL(request.url);
  const path = url.pathname;
  if (!path.startsWith("/api/wallet") && !path.startsWith("/api/merchant/pay-code")) return null;

  if (request.method === "GET" && path === "/api/wallet/me") {
    const wallet = await ensureWallet(env, user);
    return json(walletJson(wallet, user));
  }

  if (request.method === "POST" && path === "/api/wallet/activate") {
    const body = await bodyJson(request);
    const wallet = await ensureWallet(env, user, body);
    const t = now();
    await env.DB.prepare("UPDATE wallets SET username=?, phone=?, email=?, display_name=?, active=1, pin_hash=COALESCE(pin_hash, ?), activated_at=COALESCE(activated_at, ?), updated_at=? WHERE uid=?")
      .bind(clean(body.username || wallet.username, 60), clean(body.phone || wallet.phone, 64), clean(body.email || wallet.email || user.email, 160), clean(body.name || wallet.display_name || user.name, 120), body.pin ? `pin:${body.pin}` : null, t, t, user.uid).run();
    return json(walletJson(await getWallet(env, user.uid), user));
  }

  if (request.method === "POST" && path === "/api/wallet/change-pin") {
    const body = await bodyJson(request);
    if (!clean(body.newPin || body.new_pin, 32)) return json({ error: "New PIN is required" }, 400);
    await ensureWallet(env, user);
    await env.DB.prepare("UPDATE wallets SET pin_hash=?, updated_at=? WHERE uid=?").bind(`pin:${clean(body.newPin || body.new_pin, 32)}`, now(), user.uid).run();
    return json(walletJson(await getWallet(env, user.uid), user));
  }

  if (request.method === "GET" && path === "/api/wallet/transactions") {
    const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit") || 50)));
    const rows = await env.DB.prepare(`SELECT t.*, w.display_name AS counterparty_name, w.username AS counterparty_username
      FROM wallet_transactions t
      LEFT JOIN wallets w ON w.uid = t.counterparty_uid
      WHERE t.uid=? ORDER BY t.created_at DESC LIMIT ?`).bind(user.uid, limit).all();
    return json((rows.results || []).map(txJson));
  }

  if (request.method === "POST" && path === "/api/wallet/deposit") {
    const body = await bodyJson(request);
    const delta = cents(body.amount);
    if (delta <= 0) return json({ error: "Amount must be greater than zero" }, 400);
    const wallet = await ensureWallet(env, user);
    await env.DB.prepare("UPDATE wallets SET balance_cents=balance_cents+?, active=1, updated_at=? WHERE uid=?").bind(delta, now(), user.uid).run();
    const tx = await recordTx(env, wallet, "deposit", delta, clean(body.note || "Deposit", 300));
    return json({ ok: true, reference: tx.reference });
  }

  if (request.method === "GET" && ["/api/wallet/receive", "/api/wallet/username-qr"].includes(path)) {
    const wallet = await ensureWallet(env, user);
    return json({ ...walletJson(wallet, user), qrPayload: `adischat://wallet/${encodeURIComponent(wallet.wallet_id)}` });
  }

  if (request.method === "GET" && path === "/api/wallet/pay-code") {
    const wallet = await ensureWallet(env, user);
    const code = String(Math.floor(100000 + Math.random() * 900000));
    const expiresAt = now() + 5 * 60 * 1000;
    return json({ code, formattedCode: code, walletId: wallet.wallet_id, username: wallet.username, name: wallet.display_name, expiresAt, qrPayload: `adischat://pay/${code}`, barcodePayload: code });
  }

  return null;
}
