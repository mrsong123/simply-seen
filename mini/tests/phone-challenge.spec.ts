import { effectScope, type EffectScope } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  usePhoneChallenge,
  validPhone,
} from "../src/composables/usePhoneChallenge";
let scope: EffectScope;
const response = {
  challengeId: "challenge-one",
  expiresIn: 300,
  retryAfter: 60,
  development: true,
  devCode: "357029",
};
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-14T09:00:00Z"));
  scope = effectScope();
});
afterEach(() => {
  scope.stop();
  vi.useRealTimers();
});
function challenge(deliver = vi.fn().mockResolvedValue({ ...response })) {
  return { state: scope.run(() => usePhoneChallenge(deliver))!, deliver };
}
describe("SMS challenges on a native page", () => {
  it("drops a consumed proof while preserving the phone and server resend cooldown", async () => {
    const { state } = challenge();
    state.phone.value = "13800138000";
    await state.send();
    state.code.value = "357029";
    state.discardProof();
    expect(state.phone.value).toBe("13800138000");
    expect(state.code.value).toBe("");
    expect(state.challenge.value).toBeNull();
    expect(state.retrySeconds.value).toBe(60);
    expect(state.canSend.value).toBe(false);
  });
  it("requires a valid mainland phone and enforces the server resend cooldown", async () => {
    const { state, deliver } = challenge();
    expect(validPhone("13800138000")).toBe(true);
    expect(validPhone("123")).toBe(false);
    state.phone.value = "123";
    await expect(state.send()).rejects.toThrow("有效手机号");
    expect(deliver).not.toHaveBeenCalled();
    state.phone.value = "13800138000";
    await state.send();
    expect(state.retrySeconds.value).toBe(60);
    await expect(state.send()).rejects.toThrow("倒计时");
    await vi.advanceTimersByTimeAsync(60000);
    expect(state.canSend.value).toBe(true);
    await state.send();
    expect(deliver).toHaveBeenCalledTimes(2);
  });
  it("returns a proof only for the current phone, challenge and six-digit code", async () => {
    const { state } = challenge();
    state.phone.value = "13800138000";
    await state.send();
    state.code.value = "123";
    expect(() => state.proof()).toThrow("6位");
    state.code.value = "357029";
    expect(state.proof()).toEqual({
      phone: "13800138000",
      code: "357029",
      challengeId: "challenge-one",
    });
    state.phone.value = "13900139000";
    expect(state.code.value).toBe("");
    expect(state.challenge.value).toBeNull();
    expect(() => state.proof()).toThrow("当前手机号");
  });
  it("rejects expired proof even before the next UI timer tick", async () => {
    const { state } = challenge();
    state.phone.value = "13800138000";
    await state.send();
    state.code.value = "357029";
    vi.setSystemTime(Date.now() + 300001);
    expect(() => state.proof()).toThrow("有效验证码");
  });
  it("does not attach a late response to a different phone", async () => {
    let finish!: (value: typeof response) => void;
    const { state } = challenge(
      vi.fn(
        () =>
          new Promise((resolve) => {
            finish = resolve;
          }),
      ),
    );
    state.phone.value = "13800138000";
    const pending = state.send();
    state.phone.value = "13900139000";
    finish({ ...response });
    await pending;
    expect(state.challenge.value).toBeNull();
    expect(state.phone.value).toBe("13900139000");
  });
  it("clears the development code and form when the page scope is disposed", async () => {
    const { state } = challenge();
    state.phone.value = "13800138000";
    await state.send();
    state.code.value = "357029";
    scope.stop();
    expect(state.phone.value).toBe("");
    expect(state.code.value).toBe("");
    expect(state.challenge.value).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });
});
