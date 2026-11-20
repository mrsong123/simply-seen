import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
const memory = new Map<string, string>();
let native: Record<string, ReturnType<typeof vi.fn>>;
beforeEach(() => {
  vi.resetModules();
  memory.clear();
  native = {
    getStorageSync: vi.fn((key: string) => memory.get(key) || ""),
    setStorageSync: vi.fn((key: string, value: string) =>
      memory.set(key, value),
    ),
    removeStorageSync: vi.fn((key: string) => memory.delete(key)),
    navigateTo: vi.fn(),
    redirectTo: vi.fn(),
    reLaunch: vi.fn(),
    navigateBack: vi.fn(),
  };
  vi.stubGlobal("uni", native);
  vi.stubGlobal("getCurrentPages", () => []);
});
afterEach(() => vi.unstubAllGlobals());
describe("Native page routing and auth guard", () => {
  it("protects direct security and identity pages and restores their intended destination", async () => {
    const router = await import("../src/utils/router");
    expect(router.requireSession("identity")).toBe(false);
    expect(memory.get("sj-login-return")).toBe("/pages/account/identity");
    memory.set("sj-token", "test");
    router.navigate("security");
    expect(native.navigateTo).toHaveBeenLastCalledWith({
      url: "/pages/account/security",
    });
  });
  it("redirects anonymous checkout navigation through login and restores the intended route", async () => {
    const router = await import("../src/utils/router");
    router.navigate("checkout");
    expect(native.navigateTo).toHaveBeenCalledWith({
      url: "/pages/account/index",
    });
    expect(memory.get("sj-login-return")).toBe("/pages/checkout/index");
    memory.set("sj-token", "test");
    router.afterLogin();
    expect(native.redirectTo).toHaveBeenCalledWith({
      url: "/pages/checkout/index",
    });
    expect(memory.has("sj-login-return")).toBe(false);
  });
  it("uses registered native page URLs and safely encodes query values", async () => {
    memory.set("sj-token", "test");
    const router = await import("../src/utils/router");
    router.navigate("order", { id: "SJ 1&x" });
    expect(native.navigateTo).toHaveBeenCalledWith({
      url: "/pages/orders/detail?id=SJ%201%26x",
    });
  });
  it("relaunches top-level tabs so tab switches do not exhaust the native page stack", async () => {
    const router = await import("../src/utils/router");
    router.navigate("home");
    expect(native.reLaunch).toHaveBeenCalledWith({ url: "/pages/index/index" });
    expect(native.navigateTo).not.toHaveBeenCalled();
  });
  it("does not restore an external redirect value", async () => {
    const router = await import("../src/utils/router");
    memory.set("sj-login-return", "https://external.invalid");
    router.afterLogin();
    expect(native.redirectTo).not.toHaveBeenCalled();
  });
  it("guards direct protected page loads and records their order identity", async () => {
    const router = await import("../src/utils/router");
    expect(router.requireSession("order", { id: "SJ42" })).toBe(false);
    expect(memory.get("sj-login-return")).toBe("/pages/orders/detail?id=SJ42");
    expect(native.redirectTo).toHaveBeenCalledWith({
      url: "/pages/account/index",
    });
  });
});

it("only restores registered local pages, not arbitrary /pages/ paths", async () => {
  const router = await import("../src/utils/router");
  memory.set("sj-login-return", "/pages/unknown/unwired");
  router.afterLogin();
  expect(native.redirectTo).not.toHaveBeenCalled();
  memory.set("sj-token", "test");
  router.navigate("awards");
  expect(native.navigateTo).toHaveBeenCalledWith({
    url: "/pages/account/awards",
  });
});
