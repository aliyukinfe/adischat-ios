// Local-only reviewed media adapter. Hosted Storage behavior remains unverified.
import { adminClient } from "../functions/_shared/adishop.ts";
import { serveReviewedMedia, type ReviewedMediaLocator } from "../functions/_shared/adishop-reviewed-media.ts";

Deno.serve({ hostname: "127.0.0.1", port: 55456 }, (request) => {
  const origin = Deno.env.get("SUPABASE_URL") || "";
  const enabled = Deno.env.get("ADISHOP_QA_MEDIA_ENABLED") === "true" &&
    /^http:\/\/127\.0\.0\.1:\d+$/.test(origin);
  const client = enabled ? adminClient() : null;
  return serveReviewedMedia(request, {
    enabled,
    supabaseOrigin: origin,
    lookup: async (kind, id) => {
      if (!client) return null;
      const { data, error } = await client.rpc("adishop_qa_media_locator", { p_kind: kind, p_id: id });
      if (error) throw new Error("locator-unavailable");
      return data as ReviewedMediaLocator | null;
    },
    download: async (bucket, path) => {
      if (!client) return null;
      const { data, error } = await client.storage.from(bucket).download(path);
      if (error) throw new Error("storage-unavailable");
      return data;
    },
  });
});
