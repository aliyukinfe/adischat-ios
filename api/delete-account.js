module.exports = async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.status(204).end();
    return;
  }

  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : (req.body || {});
  const identifier = String(body.identifier || "").trim();
  const reason = String(body.reason || "").trim();

  if (!identifier || identifier.length > 160 || reason.length > 2000) {
    res.status(400).json({ error: "Invalid request" });
    return;
  }

  const submittedAt = new Date().toISOString();
  const supportEmail = "contact@adischat.com";
  const resendKey = process.env.RESEND_API_KEY;

  if (resendKey) {
    const from = process.env.FROM_EMAIL || "AdisChat <noreply@adischat.com>";
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${resendKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        from,
        to: supportEmail,
        subject: "AdisChat account deletion request",
        text: [
          "A public account deletion request was submitted.",
          "",
          `Account: ${identifier}`,
          `Reason: ${reason || "(not provided)"}`,
          `Submitted at: ${submittedAt}`
        ].join("\n")
      })
    });

    if (!response.ok) {
      res.status(502).json({ error: "Could not notify support" });
      return;
    }
  } else {
    console.log("AdisChat account deletion request", { identifier, reasonProvided: Boolean(reason), submittedAt });
  }

  res.status(202).json({ ok: true, message: "Your account deletion request has been received." });
};
