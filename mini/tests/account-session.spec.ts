import { createPinia, setActivePinia } from "pinia";
import { beforeEach, afterEach, describe, it, vi, expect } from "vitest";
const fake = vi.hoisted(() => ({
  profile: vi.fn(),
  logout: vi.fn(),
  login: vi.fn(),
  devLogin: vi.fn(),
  updateName: vi.fn(),
  loginPhone: vi.fn(),
  bindPhone: vi.fn(),
  bindWechat: vi.fn(),
  wechatCode: vi.fn(),
}));
vi.mock("../src/api/member", () => ({ memberApi: fake }));
vi.mock("../src/api/account", () => ({ accountApi: fake }));
vi.mock("../src/utils/wechat", () => ({ wechatLoginCode: fake.wechatCode }));
const memory = new Map<string, string>();
const member = {
  id: 7,
  name: "会员",
  phoneBound: false,
  phoneMasked: "",
  wechatBound: false,
};
const login = { token: "phone-session", kind: "MEMBER", profile: member };
beforeEach(() => {
  setActivePinia(createPinia());
  vi.resetAllMocks();
  memory.clear();
  fake.loginPhone.mockResolvedValue(login);
  fake.logout.mockResolvedValue(undefined);
  vi.stubGlobal("uni", {
    getStorageSync: vi.fn((key: string) => memory.get(key) || ""),
    setStorageSync: vi.fn((key: string, value: string) =>
      memory.set(key, value),
    ),
    removeStorageSync: vi.fn((key: string) => memory.delete(key)),
    $on: vi.fn(),
    $emit: vi.fn(),
  });
});
afterEach(() => vi.unstubAllGlobals());
const proof = {
  phone: "13800138000",
  code: "357029",
  challengeId: "once",
  accepted: true,
};
describe("Phone and WeChat member sessions", () => {
  it("logs in using the stored first-registration invitation and persists only the token", async () => {
    memory.set("sj-invite", "INVITE");
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    await session.loginPhone(proof);
    expect(fake.loginPhone).toHaveBeenCalledWith({
      ...proof,
      inviteCode: "INVITE",
    });
    expect(session.profile?.id).toBe(7);
    expect([...memory.entries()]).toEqual([
      ["sj-invite", "INVITE"],
      ["sj-token", "phone-session"],
    ]);
    expect(JSON.stringify([...memory.values()])).not.toContain(proof.phone);
    expect(JSON.stringify([...memory.values()])).not.toContain(proof.code);
  });
  it("preserves the current member on an occupied-phone rejection and does not merge accounts", async () => {
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    await session.loginPhone(proof);
    fake.bindPhone.mockRejectedValue(new Error("手机号已被其他会员绑定"));
    await expect(session.bindPhone(proof)).rejects.toThrow("其他会员");
    expect(session.profile?.id).toBe(7);
    expect(memory.get("sj-token")).toBe("phone-session");
  });
  it("does not restore a signed-out profile from a late response", async () => {
    let finish!: (value: unknown) => void;
    fake.profile.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    await session.loginPhone(proof);
    const pending = session.refresh();
    await session.logout();
    finish(member);
    await pending;
    expect(session.profile).toBeNull();
    expect(memory.has("sj-token")).toBe(false);
  });
  it("does not let an older profile read erase an accepted phone binding", async () => {
    let finish!: (value: unknown) => void;
    fake.profile.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    fake.bindPhone.mockResolvedValue({
      ...member,
      phoneBound: true,
      phoneMasked: "138****8000",
    });
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    await session.loginPhone(proof);
    const pending = session.refresh();
    await session.bindPhone(proof);
    finish(member);
    await pending;
    expect(session.profile?.phoneBound).toBe(true);
  });
  it("does not finish an earlier login after the user has signed out", async () => {
    let finish!: (value: unknown) => void;
    fake.loginPhone.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    const pending = session.loginPhone(proof);
    const assertion = expect(pending).rejects.toThrow("登录状态已变化");
    await session.logout();
    finish(login);
    await assertion;
    expect(session.profile).toBeNull();
    expect(memory.has("sj-token")).toBe(false);
  });
  it("refuses to bind a native WeChat code if the member changed while authorization was open", async () => {
    let finish!: (code: string) => void;
    fake.wechatCode.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { useSessionStore } = await import("../src/store/session");
    const session = useSessionStore();
    await session.loginPhone(proof);
    const pending = session.bindWechat();
    const assertion = expect(pending).rejects.toThrow("重新绑定微信");
    await session.logout();
    finish("native-wechat-code");
    await assertion;
    expect(fake.bindWechat).not.toHaveBeenCalled();
  });
});
