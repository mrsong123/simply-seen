import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { authApi } from "../../src/api/auth";
import { useAuthStore } from "../../src/store/auth";
const values = new Map<string, string>();
Object.defineProperty(globalThis, "localStorage", {
  value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  },
  configurable: true,
});
beforeEach(() => {
  values.clear();
  setActivePinia(createPinia());
});
afterEach(() => vi.restoreAllMocks());
describe("member profile response isolation", () => {
  it("does not overwrite a newly logged in member with the previous member's late response", async () => {
    const auth = useAuthStore();
    auth.saveSession("member", {
      token: "first-test-session",
      kind: "MEMBER",
      profile: { id: 1 },
    });
    let resolve!: (value: unknown) => void;
    vi.spyOn(authApi, "getMember").mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const loading = auth.fetchMemberProfile();
    auth.saveSession("member", {
      token: "second-test-session",
      kind: "MEMBER",
      profile: { id: 2 },
    });
    resolve({ id: 1, phoneBound: true });
    await expect(loading).resolves.toBeNull();
    expect(auth.memberProfile).toEqual({ id: 2 });
  });
  it("does not restore a member profile after logout while its request was pending", async () => {
    const auth = useAuthStore();
    auth.saveSession("member", {
      token: "first-test-session",
      kind: "MEMBER",
      profile: { id: 1 },
    });
    let resolve!: (value: unknown) => void;
    vi.spyOn(authApi, "getMember").mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const loading = auth.fetchMemberProfile();
    auth.clearSession("member");
    resolve({ id: 1, identityStatus: "VERIFIED" });
    await expect(loading).resolves.toBeNull();
    expect(auth.memberProfile).toBeNull();
    expect(auth.member).toBeNull();
  });
});
