import { effectScope, nextTick, ref } from "vue";
import { afterEach, describe, it, expect, vi } from "vitest";
import { useIdentityRetry } from "../../src/composables/account/useIdentityRetry";
import type { IdentityStatus } from "../../src/types/account";
const pending: IdentityStatus = {
  status: "PENDING",
  nameMasked: "",
  idNumberMasked: "",
  verifiedAt: null,
  rejectionReason: "",
  development: false,
  provider: "aliyun",
};
afterEach(() => vi.useRealTimers());
describe("explicit identity retry recovery", () => {
  it("permits a pending retry only after the server lease expires, without submitting automatically", async () => {
    vi.useFakeTimers();
    const source = ref({ ...pending, retryAfter: 10 });
    const scope = effectScope();
    const state = scope.run(() => useIdentityRetry(() => source.value))!;
    state.requestRetry();
    expect(state.requested.value).toBe(false);
    await vi.advanceTimersByTimeAsync(10000);
    expect(state.remaining.value).toBe(0);
    expect(state.requested.value).toBe(false);
    state.requestRetry();
    expect(state.requested.value).toBe(true);
    source.value = { ...pending, retryAfter: 20 };
    await nextTick();
    expect(state.requested.value).toBe(false);
    expect(state.remaining.value).toBe(20);
    scope.stop();
    expect(vi.getTimerCount()).toBe(0);
  });
  it("uses a conservative bounded lease for an older server response without retryAfter", async () => {
    vi.useFakeTimers();
    const scope = effectScope();
    const state = scope.run(() => useIdentityRetry(() => pending))!;
    expect(state.remaining.value).toBe(120);
    await vi.advanceTimersByTimeAsync(120000);
    state.requestRetry();
    expect(state.requested.value).toBe(true);
    scope.stop();
  });
});
