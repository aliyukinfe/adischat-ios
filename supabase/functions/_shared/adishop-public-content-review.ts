export type PublicContentKind = "product" | "image" | "variant" | "store";

const kinds = new Set<PublicContentKind>(["product", "image", "variant", "store"]);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function publicContentTarget(input: unknown): { kind: PublicContentKind; id: string } | null {
  if (!input || typeof input !== "object" || Array.isArray(input)) return null;
  const value = input as Record<string, unknown>;
  const kind = value.kind;
  const id = value.id;
  if (typeof kind !== "string" || !kinds.has(kind as PublicContentKind) ||
      typeof id !== "string" || !uuid.test(id)) return null;
  return { kind: kind as PublicContentKind, id };
}

export function publicContentDecision(input: unknown):
  { kind: PublicContentKind; id: string; revision: number; decision: "approved" | "rejected" } | null {
  const target = publicContentTarget(input);
  if (!target) return null;
  const value = input as Record<string, unknown>;
  if (typeof value.expectedRevision !== "number" ||
      !Number.isSafeInteger(value.expectedRevision) || value.expectedRevision < 1 ||
      (value.decision !== "approved" && value.decision !== "rejected")) return null;
  return { ...target, revision: value.expectedRevision, decision: value.decision };
}

export function publicContentPage(input: URLSearchParams):
  { kind: PublicContentKind | null; limit: number; offset: number } | null {
  const kind = input.get("kind");
  if (kind !== null && !kinds.has(kind as PublicContentKind)) return null;
  const limit = Number(input.get("limit") ?? "50");
  const offset = Number(input.get("offset") ?? "0");
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100 ||
      !Number.isSafeInteger(offset) || offset < 0) return null;
  return { kind: kind as PublicContentKind | null, limit, offset };
}
