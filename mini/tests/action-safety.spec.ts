import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { yuanToFen } from "../src/utils/amount";
let token = "owner";
const memory = new Map<string, string>();
beforeEach(() => {
  token = "owner";
  memory.clear();
  vi.stubGlobal("uni", {
    getStorageSync: (key: string) =>
      key === "sj-token" ? token : memory.get(key) || "",
    setStorageSync: (key: string, value: string) => memory.set(key, value),
    removeStorageSync: (key: string) => memory.delete(key),
  });
});
afterEach(() => vi.unstubAllGlobals());
describe("Native member action safety", () => {
  it("validates and normalizes invite input without persisting malformed deep-link values", async () => {
    const { authStorage } = await import("../src/utils/storage");
    authStorage.saveInvite(" abc123 ");
    expect(authStorage.invite()).toBe("ABC123");
    authStorage.saveInvite("x".repeat(100));
    expect(authStorage.invite()).toBe("ABC123");
    authStorage.setInvite("");
    expect(authStorage.invite()).toBe("");
    memory.set("sj-invite", "../../invalid");
    expect(authStorage.invite()).toBe("");
    expect(memory.has("sj-invite")).toBe(false);
  });
  it("keeps an imported address in memory, consumes it once and never transfers it across member sessions", async () => {
    const { saveAddressDraft, takeAddressDraft } = await import(
      "../src/utils/addressDraft"
    );
    const address = {
      name: "开发测试",
      phone: "13800138000",
      region: "浙江省 杭州市",
      detail: "测试地址",
      is_default: true,
    };
    saveAddressDraft(address);
    expect(memory.size).toBe(0);
    expect(takeAddressDraft()).toEqual(address);
    expect(takeAddressDraft()).toBeUndefined();
    saveAddressDraft(address);
    token = "different-owner";
    expect(takeAddressDraft()).toBeUndefined();
  });
  it("parses currency as integer cents and refuses precision loss, zero and non-finite input", () => {
    expect(yuanToFen("0.29")).toBe(29);
    expect(yuanToFen("10.1")).toBe(1010);
    for (const value of [
      "0",
      "-1",
      "0.001",
      "1e3",
      "Infinity",
      "9007199254740991",
    ])
      expect(() => yuanToFen(value)).toThrow();
  });
});
