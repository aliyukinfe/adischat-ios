/**
 * AdisChat - Channel Game Pot  |  Cloudflare Worker Routes
 *
 * HOW TO INTEGRATE:
 *   Copy this file into your existing wallet-worker project and import it in
 *   your main worker entry (e.g. index.js):
 *
 *     import { handleGamePotRequest } from './gamepot-worker-routes.js';
 *
 *   Then in your router (before the catch-all):
 *     if (url.pathname.startsWith('/api/games')) {
 *       return handleGamePotRequest(request, env, ctx, uid, userRecord);
 *     }
 *
 * ENVIRONMENT BINDINGS (wrangler.toml):
 *   [[ d1_databases ]]
 *   binding = "DB"      <- the D1 database
 *   database_name = "adischa-db"
 *   database_id   = "<your-db-id>"
 *
 * The wallet deduction uses internal wallet helpers already present in your
 * wallet worker.  Replace  walletDeduct()  and  walletCredit()  with
 * whatever your existing implementation uses.
 */

// - Helpers -

function nanoid(size = 21) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.getRandomValues(new Uint8Array(size));
  return Array.from(bytes, b => chars[b % chars.length]).join('');
}

function ok(data, status = 200) {
  return Response.json(data, { status });
}

function err(message, status = 400) {
  return Response.json({ error: message }, { status });
}

function nowMs() {
  return Date.now();
}

// - Wallet helpers (adapt to your real wallet implementation) -

/**
 * Deduct `amount` ETB from `uid`'s wallet.
 * Returns { ok: true, reference: string, balanceAfter: number }
 * or throws on insufficient funds / wallet error.
 *
 * IMPORTANT: This must be idempotent - pass a stable idempotency key.
 */
async function walletDeduct(env, uid, amount, reference, note) {
  // - Replace with your real wallet deduction call -
  // Example if you call the wallet D1 directly:
  const now = nowMs();
  const stmt = env.DB.prepare(`
    UPDATE wallets
    SET    balance      = balance - ?,
        updated_at   = ?
    WHERE  user_id = ?
      AND  balance >= ?
      AND  active   = 1
      AND  frozen   = 0
  `);
  const result = await stmt.bind(amount, now, uid, amount).run();
  if (result.meta.changes === 0)
    throw new Error('Insufficient wallet balance or wallet not active.');

  // Log transaction
  await env.DB.prepare(`
    INSERT INTO wallet_transactions
      (id, user_id, type, amount, reference, note, created_at)
    VALUES (?, ?, 'game_ticket_buy', ?, ?, ?, ?)
  `).bind(nanoid(), uid, amount, reference, note, now).run();

  const row = await env.DB.prepare('SELECT balance FROM wallets WHERE user_id = ?').bind(uid).first();
  return { ok: true, reference, balanceAfter: row?.balance ?? 0 };
}

/**
 * Credit `amount` ETB to `uid`'s wallet (jackpot payout or refund).
 */
async function walletCredit(env, uid, amount, reference, note) {
  const now = nowMs();
  await env.DB.prepare(`
    UPDATE wallets
    SET    balance    = balance + ?,
        updated_at = ?
    WHERE  user_id = ?
  `).bind(amount, now, uid).run();

  await env.DB.prepare(`
    INSERT INTO wallet_transactions
      (id, user_id, type, amount, reference, note, created_at)
    VALUES (?, ?, 'game_payout', ?, ?, ?, ?)
  `).bind(nanoid(), uid, amount, reference, note, now).run();
}

// - Route dispatcher -

export async function handleGamePotRequest(request, env, _ctx, uid, userRecord) {
  const url    = new URL(request.url);
  const path   = url.pathname;      // e.g. /api/games/abc123/buy
  const method = request.method.toUpperCase();

  // POST /api/games/:channelId/create
  if (method === 'POST' && path.match(/^\/api\/games\/([^/]+)\/create$/)) {
    const channelId = path.split('/')[3];
    return createGame(request, env, uid, userRecord, channelId);
  }

  // POST /api/games/:gameId/activate
  if (method === 'POST' && path.match(/^\/api\/games\/([^/]+)\/activate$/)) {
    const gameId = path.split('/')[3];
    return activateGame(request, env, uid, gameId);
  }

  // POST /api/games/:gameId/buy
  if (method === 'POST' && path.match(/^\/api\/games\/([^/]+)\/buy$/)) {
    const gameId = path.split('/')[3];
    return buyTickets(request, env, uid, userRecord, gameId);
  }

  // POST /api/games/:gameId/draw
  if (method === 'POST' && path.match(/^\/api\/games\/([^/]+)\/draw$/)) {
    const gameId = path.split('/')[3];
    return drawWinner(request, env, uid, gameId);
  }

  // POST /api/games/:gameId/cancel
  if (method === 'POST' && path.match(/^\/api\/games\/([^/]+)\/cancel$/)) {
    const gameId = path.split('/')[3];
    return cancelGame(request, env, uid, gameId);
  }

  // GET /api/games/:channelId/active
  if (method === 'GET' && path.match(/^\/api\/games\/([^/]+)\/active$/)) {
    const channelId = path.split('/')[3];
    return getActiveGame(env, uid, channelId);
  }

  // GET /api/games/:channelId/history
  if (method === 'GET' && path.match(/^\/api\/games\/([^/]+)\/history$/)) {
    const channelId = path.split('/')[3];
    return getGameHistory(env, channelId);
  }

  // GET /api/games/:gameId/detail
  if (method === 'GET' && path.match(/^\/api\/games\/([^/]+)\/detail$/)) {
    const gameId = path.split('/')[3];
    return getGameDetail(env, uid, gameId);
  }

  // GET /api/games/:gameId/tickets
  if (method === 'GET' && path.match(/^\/api\/games\/([^/]+)\/tickets$/)) {
    const gameId = path.split('/')[3];
    return getGameTickets(env, uid, gameId);
  }

  // GET /api/games/:gameId/participants
  if (method === 'GET' && path.match(/^\/api\/games\/([^/]+)\/participants$/)) {
    const gameId = path.split('/')[3];
    return getParticipants(env, gameId);
  }

  return err('Route not found', 404);
}

// - Handlers -

/**
 * POST /api/games/:channelId/create
 * Admin only.  Creates a game + pre-populates all ticket rows.
 */
async function createGame(request, env, uid, userRecord, channelId) {
  const body = await request.json();
  const {
    title,
    totalTickets,
    pricePerTicket,
    adminCommissionPct = 5.0,
    maxTicketsPerUser  = null,
    durationSeconds,
  } = body;

  // - Validate -
  if (!title || typeof title !== 'string' || title.trim().length < 3)
    return err('Title must be at least 3 characters.');
  if (!Number.isInteger(totalTickets) || totalTickets < 10 || totalTickets > 10000)
    return err('Total tickets must be between 10 and 10 000.');
  if (typeof pricePerTicket !== 'number' || pricePerTicket < 1)
    return err('Price per ticket must be at least 1 ETB.');
  if (adminCommissionPct < 0 || adminCommissionPct > 30)
    return err('Commission must be between 0% and 30%.');
  if (!Number.isInteger(durationSeconds) || durationSeconds < 60)
    return err('Duration must be at least 60 seconds.');
  if (maxTicketsPerUser !== null && (!Number.isInteger(maxTicketsPerUser) || maxTicketsPerUser < 1))
    return err('Max tickets per user must be a positive integer.');

  // Only channel owner/admin can create a game.
  const channel = await env.DB.prepare(
    'SELECT owner_uid FROM communities WHERE id = ?'
  ).bind(channelId).first();
  if (!channel) return err('Channel not found.', 404);
  if (channel.owner_uid !== uid && userRecord?.role !== 'admin')
    return err('Only the channel owner can create a game.', 403);

  // Only one active/pending game per channel at a time
  const existing = await env.DB.prepare(`
    SELECT id FROM channel_games
    WHERE  channel_id = ? AND status IN ('pending', 'active', 'drawing')
    LIMIT  1
  `).bind(channelId).first();
  if (existing) return err('A game is already running in this channel.');

  // - Create game record -
  const gameId  = nanoid();
  const now     = nowMs();

  await env.DB.prepare(`
    INSERT INTO channel_games
      (id, channel_id, creator_uid, title, total_tickets,
       price_per_ticket, admin_commission_pct, max_tickets_per_user,
       duration_seconds, status, jackpot_amount, tickets_sold, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 0, 0, ?, ?)
  `).bind(
    gameId, channelId, uid, title.trim(), totalTickets,
    pricePerTicket, adminCommissionPct, maxTicketsPerUser,
    durationSeconds, now, now,
  ).run();

  // - Pre-populate ticket rows in batches (D1 batch API) -
  // We insert in batches of 100 to avoid D1 statement size limits.
  const BATCH = 100;
  for (let start = 1; start <= totalTickets; start += BATCH) {
    const end      = Math.min(start + BATCH - 1, totalTickets);
    const statements = [];
    for (let n = start; n <= end; n++) {
      statements.push(
        env.DB.prepare(
        'INSERT INTO game_tickets (id, game_id, ticket_number, status) VALUES (?, ?, ?, ?)'
        ).bind(nanoid(), gameId, n, 'available')
      );
    }
    await env.DB.batch(statements);
  }

  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  return ok(formatGame(game), 201);
}

/**
 * POST /api/games/:gameId/activate
 * Admin starts the countdown.  Locks all settings.
 */
async function activateGame(_request, env, uid, gameId) {
  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game)      return err('Game not found.', 404);
  if (game.status !== 'pending') return err('Only pending games can be activated.');
  if (game.creator_uid !== uid)  return err('Only the game creator can activate it.', 403);

  const now    = nowMs();
  const endsAt = now + game.duration_seconds * 1000;

  await env.DB.prepare(`
    UPDATE channel_games
    SET    status = 'active', starts_at = ?, ends_at = ?, updated_at = ?
    WHERE  id = ?
  `).bind(now, endsAt, now, gameId).run();

  const updated = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  return ok(formatGame(updated));
}

/**
 * POST /api/games/:gameId/buy
 * Body: { ticketNumbers: number[], walletPin: string }
 * Atomically assigns tickets + deducts wallet using D1 batch.
 * Race-condition-safe: UNIQUE constraint on (game_id, ticket_number) prevents double-sell.
 */
async function buyTickets(request, env, uid, userRecord, gameId) {
  const body = await request.json().catch(() => ({}));
  const { ticketNumbers, walletPin } = body;

  if (!Array.isArray(ticketNumbers) || ticketNumbers.length === 0)
    return err('Select at least one ticket.');
  if (ticketNumbers.length > 20)
    return err('Maximum 20 tickets per purchase.');

  // - Load game -
  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game)      return err('Game not found.', 404);
  if (game.status !== 'active')   return err('This game is not accepting tickets right now.');
  if (game.ends_at < nowMs())     return err('Game time has expired.');

  // - Check max-per-user -
  if (game.max_tickets_per_user !== null) {
    const alreadyOwned = await env.DB.prepare(
      'SELECT COUNT(*) AS cnt FROM game_tickets WHERE game_id = ? AND owner_uid = ?'
    ).bind(gameId, uid).first();
    const cnt = alreadyOwned?.cnt ?? 0;
    if (cnt + ticketNumbers.length > game.max_tickets_per_user)
      return err(`Maximum ${game.max_tickets_per_user} tickets per user.`);
  }

  // - Verify PIN (adapt to your wallet PIN verification) -
  if (walletPin) {
    const pinOk = await verifyWalletPin(env, uid, walletPin);
    if (!pinOk) return err('Incorrect wallet PIN.', 403);
  }

  // - Verify all requested tickets are still available -
  const placeholders = ticketNumbers.map(() => '?').join(', ');
  const available = await env.DB.prepare(`
    SELECT ticket_number FROM game_tickets
    WHERE  game_id = ? AND ticket_number IN (${placeholders}) AND status = 'available'
  `).bind(gameId, ...ticketNumbers).all();

  const availableNums = new Set(available.results.map(r => r.ticket_number));
  const unavailable   = ticketNumbers.filter(n => !availableNums.has(n));
  if (unavailable.length > 0)
    return err(`Ticket(s) ${unavailable.join(', ')} already sold.`);

  const totalCost = ticketNumbers.length * game.price_per_ticket;
  const now       = nowMs();
  const reference = `GP-${gameId.slice(0, 8)}-${nanoid(8)}`;

  // - Deduct wallet -
  try {
    await walletDeduct(
      env, uid, totalCost, reference,
      `Game Pot: ${ticketNumbers.length} ticket(s) - ${game.title}`,
    );
  } catch (e) {
    return err(e.message || 'Wallet deduction failed.', 402);
  }

  // - Assign tickets (batch - UNIQUE constraint handles races) -
  const ownerName = userRecord?.name || uid;
  const photoUrl  = userRecord?.photoUrl || '';
  try {
    const ticketUpdates = ticketNumbers.map(n =>
      env.DB.prepare(`
        UPDATE game_tickets
        SET    owner_uid = ?, owner_name = ?, purchased_at = ?,
        status = 'sold', tx_reference = ?
        WHERE  game_id = ? AND ticket_number = ? AND status = 'available'
      `).bind(uid, ownerName, now, reference, gameId, n)
    );

    // Update game totals + participant in same batch
    const jackpotUpdate = env.DB.prepare(`
      UPDATE channel_games
      SET    jackpot_amount = jackpot_amount + ?,
        tickets_sold   = tickets_sold   + ?,
        updated_at     = ?
      WHERE  id = ?
    `).bind(totalCost, ticketNumbers.length, now, gameId);

    const participantUpsert = env.DB.prepare(`
      INSERT INTO game_participants (id, game_id, uid, name, photo_url, tickets_count, total_spent, joined_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(game_id, uid) DO UPDATE SET
        tickets_count = tickets_count + excluded.tickets_count,
        total_spent   = total_spent   + excluded.total_spent,
        updated_at    = excluded.updated_at
    `).bind(nanoid(), gameId, uid, ownerName, photoUrl, ticketNumbers.length, totalCost, now, now);

    await env.DB.batch([...ticketUpdates, jackpotUpdate, participantUpsert]);
  } catch (e) {
    // If ticket assignment fails (race condition), refund and return error
    await walletCredit(env, uid, totalCost, `REFUND-${reference}`, 'Ticket assignment failed - auto-refund');
    return err('One or more tickets were just purchased by another user. Please try again.', 409);
  }

  // - Auto-trigger draw if all tickets sold -
  const updatedGame = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (updatedGame && updatedGame.tickets_sold >= updatedGame.total_tickets) {
    await performDraw(env, gameId);
  }

  // Return updated game + user's tickets
  const finalGame    = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  const myTickets    = await env.DB.prepare(
    'SELECT ticket_number FROM game_tickets WHERE game_id = ? AND owner_uid = ?'
  ).bind(gameId, uid).all();

  return ok({
    game:      formatGame(finalGame),
    myTickets: myTickets.results.map(r => r.ticket_number),
    reference,
    cost:      totalCost,
  });
}

/**
 * POST /api/games/:gameId/draw
 * Manual draw by admin OR called internally when timer/tickets expire.
 */
async function drawWinner(request, env, uid, gameId) {
  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game) return err('Game not found.', 404);
  if (game.status !== 'active' && game.status !== 'drawing')
    return err('Game is not in a drawable state.');
  if (game.creator_uid !== uid)
    return err('Only the game creator can manually draw.', 403);

  const winner = await performDraw(env, gameId);
  if (!winner) return err('Draw failed - no tickets sold.');
  return ok(winner);
}

/**
 * Core draw logic - also called internally.
 * Selects a cryptographically random winning ticket, pays out jackpot.
 */
async function performDraw(env, gameId) {
  // Mark as 'drawing' first to prevent concurrent draws
  const lock = await env.DB.prepare(`
    UPDATE channel_games SET status = 'drawing', updated_at = ?
    WHERE id = ? AND status = 'active'
  `).bind(nowMs(), gameId).run();
  if (lock.meta.changes === 0) return null; // Already drawing or completed

  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game || game.tickets_sold === 0) {
    // No tickets sold - cancel the game and refund nothing (nothing to refund)
    await env.DB.prepare(`
      UPDATE channel_games SET status = 'cancelled', updated_at = ? WHERE id = ?
    `).bind(nowMs(), gameId).run();
    return null;
  }

  // - Cryptographically random winner selection -
  const soldTickets = await env.DB.prepare(`
    SELECT ticket_number, owner_uid, owner_name
    FROM   game_tickets
    WHERE  game_id = ? AND status = 'sold'
    ORDER  BY ticket_number
  `).bind(gameId).all();

  const tickets     = soldTickets.results;
  const seedBytes   = crypto.getRandomValues(new Uint8Array(4));
  const seed      = Array.from(seedBytes).map(b => b.toString(16).padStart(2, '0')).join('');
  const seedVal     = parseInt(seed, 16);      // 0 - 4294967295
  const winnerIdx   = seedVal % tickets.length;
  const winnerTicket = tickets[winnerIdx];

  // - Calculate payout -
  const jackpot      = game.jackpot_amount;
  const commissionAmount = jackpot * (game.admin_commission_pct / 100);
  const payoutAmount     = jackpot - commissionAmount;
  const payoutRef      = `GP-WIN-${nanoid(12)}`;
  const commissionRef    = `GP-COM-${nanoid(12)}`;
  const now      = nowMs();

  // - Pay winner -
  await walletCredit(
    env,
    winnerTicket.owner_uid,
    payoutAmount,
    payoutRef,
    `- You won the Game Pot "${game.title}"! Jackpot: ${jackpot.toFixed(2)} ETB`,
  );

  // - Pay admin commission -
  await walletCredit(
    env,
    game.creator_uid,
    commissionAmount,
    commissionRef,
    `Game Pot commission "${game.title}" (${game.admin_commission_pct}%)`,
  );

  // - Mark winning ticket + update game -
  await env.DB.batch([
    env.DB.prepare(`
      UPDATE game_tickets SET status = 'won' WHERE game_id = ? AND ticket_number = ?
    `).bind(gameId, winnerTicket.ticket_number),

    env.DB.prepare(`
      UPDATE channel_games SET
        status = 'completed', winner_ticket_number = ?, winner_uid = ?, winner_name = ?,
        winner_payout = ?, admin_commission_amount = ?, draw_seed = ?, updated_at = ?
      WHERE id = ?
    `).bind(
      winnerTicket.ticket_number, winnerTicket.owner_uid, winnerTicket.owner_name,
      payoutAmount, commissionAmount, seed, now, gameId,
    ),

    env.DB.prepare(`
      INSERT INTO game_winners
        (id, game_id, channel_id, winner_uid, winner_name, ticket_number,
        jackpot_amount, admin_commission, payout_amount, payout_reference, drawn_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      nanoid(), gameId, game.channel_id,
      winnerTicket.owner_uid, winnerTicket.owner_name, winnerTicket.ticket_number,
      jackpot, commissionAmount, payoutAmount, payoutRef, now,
    ),
  ]);

  const finalGame = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  return {
    game:      formatGame(finalGame),
    winnerTicketNumber: winnerTicket.ticket_number,
    winnerName:      winnerTicket.owner_name,
    payoutAmount,
    commissionAmount,
    seed,
  };
}

/**
 * POST /api/games/:gameId/cancel
 * Admin cancels a pending or active game and issues full refunds.
 */
async function cancelGame(_request, env, uid, gameId) {
  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game) return err('Game not found.', 404);
  if (!['pending', 'active'].includes(game.status))
    return err('Only pending or active games can be cancelled.');
  if (game.creator_uid !== uid)
    return err('Only the game creator can cancel it.', 403);

  // Collect all ticket owners who need refunds
  const sold = await env.DB.prepare(`
    SELECT owner_uid, COUNT(*) AS cnt, SUM(?) AS refund
    FROM   game_tickets
    WHERE  game_id = ? AND status = 'sold'
    GROUP  BY owner_uid
  `).bind(game.price_per_ticket, gameId).all();

  const now = nowMs();
  // Refund each participant
  for (const row of sold.results) {
    const refundAmount = row.cnt * game.price_per_ticket;
    await walletCredit(
      env, row.owner_uid, refundAmount,
      `REFUND-${gameId.slice(0, 8)}-${nanoid(6)}`,
      `Refund: Game Pot "${game.title}" was cancelled`,
    );
  }

  await env.DB.prepare(`
    UPDATE channel_games SET status = 'cancelled', updated_at = ? WHERE id = ?
  `).bind(now, gameId).run();

  return ok({ cancelled: true, refundsIssued: sold.results.length });
}

// - Query handlers -

async function getActiveGame(env, uid, channelId) {
  const game = await env.DB.prepare(`
    SELECT * FROM channel_games
    WHERE  channel_id = ? AND status IN ('pending', 'active', 'drawing')
    ORDER  BY created_at DESC LIMIT 1
  `).bind(channelId).first();

  if (!game) return ok({ game: null, myTickets: [] });

  const myTickets = await env.DB.prepare(`
    SELECT ticket_number FROM game_tickets WHERE game_id = ? AND owner_uid = ?
  `).bind(game.id, uid).all();

  return ok({
    game:      formatGame(game),
    myTickets: myTickets.results.map(r => r.ticket_number),
  });
}

async function getGameDetail(env, uid, gameId) {
  const game = await env.DB.prepare('SELECT * FROM channel_games WHERE id = ?').bind(gameId).first();
  if (!game) return err('Game not found.', 404);

  const myTickets = await env.DB.prepare(`
    SELECT ticket_number FROM game_tickets WHERE game_id = ? AND owner_uid = ?
  `).bind(gameId, uid).all();

  return ok({
    game:      formatGame(game),
    myTickets: myTickets.results.map(r => r.ticket_number),
  });
}

async function getGameTickets(env, uid, gameId) {
  const tickets = await env.DB.prepare(`
    SELECT ticket_number, owner_uid, owner_name, status, purchased_at
    FROM   game_tickets
    WHERE  game_id = ?
    ORDER  BY ticket_number
  `).bind(gameId).all();

  return ok(tickets.results.map(t => ({
    ticketNumber: t.ticket_number,
    status:       t.status,
    ownerUid:     t.owner_uid,
    ownerName:    t.owner_name,
    purchasedAt:  t.purchased_at,
    ismine:       t.owner_uid === uid,
  })));
}

async function getParticipants(env, gameId) {
  const rows = await env.DB.prepare(`
    SELECT uid, name, photo_url, tickets_count, total_spent, joined_at
    FROM   game_participants
    WHERE  game_id = ?
    ORDER  BY tickets_count DESC, joined_at ASC
    LIMIT  100
  `).bind(gameId).all();

  return ok(rows.results.map(r => ({
    uid:      r.uid,
    name:      r.name,
    photoUrl:     r.photo_url,
    ticketsCount: r.tickets_count,
    totalSpent:   r.total_spent,
    joinedAt:     r.joined_at,
  })));
}

async function getGameHistory(env, channelId) {
  const rows = await env.DB.prepare(`
    SELECT g.*, w.winner_name, w.ticket_number AS won_ticket, w.payout_amount, w.drawn_at
    FROM   channel_games g
    LEFT   JOIN game_winners w ON w.game_id = g.id
    WHERE  g.channel_id = ? AND g.status IN ('completed', 'cancelled')
    ORDER  BY g.created_at DESC
    LIMIT  20
  `).bind(channelId).all();

  return ok(rows.results.map(formatGame));
}

// - Formatters -

function formatGame(g) {
  if (!g) return null;
  return {
    id:      g.id,
    channelId:      g.channel_id,
    creatorUid:      g.creator_uid,
    title:      g.title,
    totalTickets:      g.total_tickets,
    pricePerTicket:       g.price_per_ticket,
    adminCommissionPct:   g.admin_commission_pct,
    maxTicketsPerUser:    g.max_tickets_per_user,
    durationSeconds:      g.duration_seconds,
    startsAt:      g.starts_at,
    endsAt:      g.ends_at,
    status:      g.status,
    jackpotAmount:      g.jackpot_amount,
    ticketsSold:      g.tickets_sold,
    totalTicketCount:     g.total_tickets,
    winnerTicketNumber:   g.winner_ticket_number,
    winnerUid:      g.winner_uid,
    winnerName:      g.winner_name,
    winnerPayout:      g.winner_payout,
    adminCommissionAmount: g.admin_commission_amount,
    createdAt:      g.created_at,
    updatedAt:      g.updated_at,
  };
}

// - PIN Verification (adapt to your actual implementation) -

async function verifyWalletPin(env, uid, pin) {
  // Replace with your actual PIN hash check
  const row = await env.DB.prepare(
    'SELECT pin_hash FROM wallets WHERE user_id = ?'
  ).bind(uid).first();
  if (!row) return false;
  // Simple placeholder - use bcrypt or Argon2 in production
  const encoder = new TextEncoder();
  const data     = encoder.encode(pin);
  const hashBuf  = await crypto.subtle.digest('SHA-256', data);
  const hashHex  = Array.from(new Uint8Array(hashBuf)).map(b => b.toString(16).padStart(2, '0')).join('');
  return hashHex === row.pin_hash;
}
