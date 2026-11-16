export const benefitKinds = [
  "original_product", "official_store", "seller_warranty", "manufacturer_warranty",
] as const;

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function benefitUuid(value: unknown): string | null {
  const id = String(value || "");
  return uuidPattern.test(id) ? id : null;
}

export function benefitMime(bytes: Uint8Array): "image/jpeg" | "image/png" | "application/pdf" | null {
  if (bytes.length > 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.length > 7 && [137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => bytes[index] === value)) return "image/png";
  if (bytes.length > 4 && [37, 80, 68, 70, 45].every((value, index) => bytes[index] === value)) return "application/pdf";
  return null;
}
