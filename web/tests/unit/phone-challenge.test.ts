import { effectScope, nextTick } from "vue";
import { afterEach, describe, it, expect, vi } from "vitest";
import { usePhoneChallenge } from "../../src/composables/account/usePhoneChallenge";
const challenge = {
  challengeId: "challenge",
  expiresIn: 300,
  retryAfter: 60,
  development: true,
  devCode: "913572",
};
afterEach(() => vi.useRealTimers());
describe("SMS form asynchronous state", () => {
  it("rejects the old challenge when the phone changes and does not lose the original cooldown", async () => {
    vi.useFakeTimers();
    const scope = effectScope();
    const send = vi.fn(async () => challenge);
    const sms = scope.run(() => usePhoneChallenge(send))!;
    sms.phone = "13800138000";
    await nextTick();
    await sms.sendCode();
    expect(sms.developmentCode).toBe("913572");
    expect(sms.remaining).toBe(60);
    await sms.sendCode();
    expect(send).toHaveBeenCalledTimes(1);
    sms.phone = "13900139000";
    await nextTick();
    sms.code = "913572";
    expect(sms.credentials()).toBeNull();
    expect(sms.developmentCode).toBe("");
    sms.phone = "13800138000";
    await nextTick();
    expect(sms.remaining).toBe(60);
    await vi.advanceTimersByTimeAsync(60000);
    expect(sms.remaining).toBe(0);
    scope.stop();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("does not attach a late response to a different phone", async () => {
    vi.useFakeTimers();
    let resolve!: (value: typeof challenge) => void;
    const scope = effectScope();
    const sms = scope.run(() =>
      usePhoneChallenge(
        () =>
          new Promise((done) => {
            resolve = done;
          }),
      ),
    )!;
    sms.phone = "13800138000";
    await nextTick();
    const sending = sms.sendCode();
    sms.phone = "13900139000";
    await nextTick();
    resolve(challenge);
    await sending;
    sms.code = "913572";
    expect(sms.credentials()).toBeNull();
    expect(sms.developmentCode).toBe("");
    scope.stop();
  });
  it("surfaces sending failures without inventing a challenge or a successful SMS", async () => {
    const scope = effectScope();
    const sms = scope.run(() =>
      usePhoneChallenge(async () => {
        throw new Error("短信模板未审核");
      }),
    )!;
    sms.phone = "13800138000";
    await nextTick();
    await sms.sendCode();
    expect(sms.error).toBe("短信模板未审核");
    expect(sms.developmentCode).toBe("");
    expect(sms.sending).toBe(false);
    expect(sms.credentials()).toBeNull();
    scope.stop();
  });
});
