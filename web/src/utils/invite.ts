export function normalizeInvite(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim().toUpperCase();
  if (!normalized) return "";
  return /^[A-Z0-9]{1,24}$/.test(normalized) ? normalized : null;
}
export function readInvite(storage: Pick<Storage, "getItem" | "removeItem"> = localStorage): string {
  const saved = storage.getItem("sj-invite");
  if (!saved) return "";
  const normalized = normalizeInvite(saved);
  if (normalized === null) { storage.removeItem("sj-invite"); return ""; }
  return normalized;
}
