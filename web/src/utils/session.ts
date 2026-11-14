export type SessionKind = "member" | "admin";
export interface AuthSession {
  token: string;
  kind: string;
  profile: Record<string, any>;
}
const storageKey = (kind: SessionKind) => `sj-${kind}`;
export function readSession(kind: SessionKind): AuthSession | null {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey(kind)) || "null");
    return value?.token ? value : null;
  } catch {
    localStorage.removeItem(storageKey(kind));
    return null;
  }
}
export function persistSession(kind: SessionKind, value: AuthSession | null) {
  if (value) localStorage.setItem(storageKey(kind), JSON.stringify(value));
  else localStorage.removeItem(storageKey(kind));
}
