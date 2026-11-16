const WALLET_API_BASE = "https://adischat-wallet-api.adischat.workers.dev";

module.exports = async function handler(req, res) {
  const rawSlug = Array.isArray(req.query.slug) ? req.query.slug[0] : req.query.slug;
  const slug = String(rawSlug || "").replace(/^@/, "").trim();

  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.status(204).end();
    return;
  }

  if (req.method !== "GET") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  if (!slug) {
    res.status(400).json({ error: "Missing link slug" });
    return;
  }

  try {
    const type = req.query.type ? `?type=${encodeURIComponent(req.query.type)}` : "";
    const upstream = await fetch(`${WALLET_API_BASE}/api/links/${encodeURIComponent(slug)}${type}`);
    const text = await upstream.text();
    res.status(upstream.status);
    res.setHeader("Content-Type", upstream.headers.get("content-type") || "application/json");
    res.send(text);
  } catch (error) {
    res.status(502).json({
      error: "Could not resolve AdisChat link",
      message: error && error.message ? error.message : "Upstream unavailable"
    });
  }
};

