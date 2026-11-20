const TOKEN = "sj-token";
const INVITE = "sj-invite";
export function normalizeInvite(value: unknown): string {
  if (typeof value !== "string") return "";
  const normalized = value.trim().toUpperCase();
  return /^[A-Z0-9]{1,24}$/.test(normalized) ? normalized : "";
}
export const authStorage = {
  token: (): string => uni.getStorageSync(TOKEN) || "",
  saveToken: (token: string) => uni.setStorageSync(TOKEN, token),
  clear: () => uni.removeStorageSync(TOKEN),
  invite: (): string => {
    const value = uni.getStorageSync(INVITE);
    const normalized = normalizeInvite(value);
    if (value && !normalized) uni.removeStorageSync(INVITE);
    return normalized;
  },
  saveInvite: (code: string) => {
    const normalized = normalizeInvite(code);
    if (normalized) uni.setStorageSync(INVITE, normalized);
  },
  setInvite: (code: string) => {
    const normalized = normalizeInvite(code);
    if (normalized) uni.setStorageSync(INVITE, normalized);
    else uni.removeStorageSync(INVITE);
  },
};
