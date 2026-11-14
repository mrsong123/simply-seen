import { describe, it, expect } from "vitest";
import {
  PhoneChallengeState,
  identityInputError,
  withdrawalRequirements,
} from "../../src/utils/account";
const sent = {
  challengeId: "challenge-one",
  expiresIn: 300,
  retryAfter: 60,
  development: true,
  devCode: "913572",
};
describe("phone challenge identity and expiry", () => {
  it("requires a challenge for the current number and rejects a code for another number", () => {
    const state = new PhoneChallengeState();
    expect(() => state.credentials("13800138000", "913572", 1000)).toThrow(
      "请先",
    );
    state.accept("13800138000", sent, 1000);
    expect(() => state.credentials("13900139000", "913572", 1001)).toThrow(
      "当前手机号",
    );
    expect(state.credentials("13800138000", "913572", 1001)).toEqual({
      phone: "13800138000",
      code: "913572",
      challengeId: "challenge-one",
    });
  });
  it("expires a challenge at its deadline instead of letting a paused tab submit stale OTP", () => {
    const state = new PhoneChallengeState();
    state.accept("13800138000", sent, 1000);
    expect(() => state.credentials("13800138000", "913572", 301000)).toThrow(
      "过期",
    );
  });
  it("preserves a number's resend cooldown after its form challenge is cleared", () => {
    const state = new PhoneChallengeState();
    state.accept("13800138000", sent, 1000);
    state.clear();
    expect(state.remaining("13800138000", 2500)).toBe(59);
    expect(state.remaining("13900139000", 2500)).toBe(0);
    expect(state.remaining("13800138000", 61000)).toBe(0);
    expect(state.developmentCode()).toBe("");
  });
  it("does not display a provider's test code unless the response explicitly marks development", () => {
    const state = new PhoneChallengeState();
    state.accept("13800138000", { ...sent, development: false }, 1000);
    expect(state.developmentCode()).toBe("");
    state.accept("13800138000", { ...sent, devCode: "non-code" }, 1000);
    expect(state.developmentCode()).toBe("");
  });
  it("rejects malformed service challenges, invalid phone numbers and malformed codes", () => {
    const state = new PhoneChallengeState();
    expect(() =>
      state.accept("13800138000", { ...sent, expiresIn: 0 }, 1000),
    ).toThrow("返回异常");
    state.accept("13800138000", sent, 1000);
    expect(() => state.credentials("12800138000", "913572", 1001)).toThrow(
      "有效",
    );
    expect(() => state.credentials("13800138000", "91357x", 1001)).toThrow(
      "6位",
    );
  });
});
describe("resident identity input checks", () => {
  it("accepts a published checksum example including lowercase X and trims input", () => {
    expect(identityInputError(" 张三 ", " 11010519491231002x ")).toBe("");
  });
  it("rejects impossible dates and incorrect check digits before a chargeable provider call", () => {
    expect(identityInputError("张三", "11010519490231002X")).toContain(
      "出生日期",
    );
    expect(identityInputError("张三", "110105194912310020")).toContain(
      "校验位",
    );
    expect(
      identityInputError("张三", "11010520991231002X", new Date(2026, 10, 14)),
    ).toContain("出生日期");
  });
  it("requires a complete name and mainland 18-digit identity format", () => {
    expect(identityInputError("张", "11010519491231002X")).toContain(
      "真实姓名",
    );
    expect(identityInputError("张三", "110105491231002")).toContain("18位");
  });
});
describe("withdrawal readiness", () => {
  it("cannot disable real identity or real WeChat requirements in production", () => {
    expect(
      withdrawalRequirements(
        {
          identityStatus: "VERIFIED",
          identityDevelopment: true,
          wechatBound: false,
        },
        { devMode: false, identityRequiredForWithdrawal: false },
      ),
    ).toEqual({ needsIdentity: true, needsWechat: true });
  });
  it("allows actual verified and bound members while failing closed when status is missing", () => {
    expect(
      withdrawalRequirements(
        {
          identityStatus: "VERIFIED",
          identityDevelopment: false,
          wechatBound: true,
        },
        { devMode: false },
      ),
    ).toEqual({ needsIdentity: false, needsWechat: false });
    expect(withdrawalRequirements({}, {})).toEqual({
      needsIdentity: true,
      needsWechat: true,
    });
  });
  it("keeps development identity clearly distinct while allowing the explicit local test exception", () => {
    expect(
      withdrawalRequirements(
        { identityStatus: "VERIFIED", identityDevelopment: true },
        { devMode: true },
      ),
    ).toEqual({ needsIdentity: false, needsWechat: false });
    expect(
      withdrawalRequirements(
        {},
        { devMode: true, identityRequiredForWithdrawal: false },
      ),
    ).toEqual({ needsIdentity: false, needsWechat: false });
    expect(
      withdrawalRequirements({ identityStatus: "PENDING" }, { devMode: true }),
    ).toEqual({ needsIdentity: true, needsWechat: false });
  });
});
