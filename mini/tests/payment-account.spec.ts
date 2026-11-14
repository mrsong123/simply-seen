import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
const fake = vi.hoisted(() => ({
  refresh: vi.fn(),
  confirm: vi.fn(),
  navigate: vi.fn(),
  prepay: vi.fn(),
  devPay: vi.fn(),
  status: vi.fn(),
  nativePay: vi.fn(),
  profile: { wechatBound: false },
}));
vi.mock("../src/store/session", () => ({ useSessionStore: () => fake }));
vi.mock("../src/api/trade", () => ({ tradeApi: fake }));
vi.mock("../src/utils/wechat", () => ({ payWithWechat: fake.nativePay }));
vi.mock("../src/utils/useTask", () => ({ confirm: fake.confirm }));
vi.mock("../src/utils/router", () => ({ navigate: fake.navigate }));
beforeEach(() => {
  vi.resetAllMocks();
  fake.profile.wechatBound = false;
  fake.refresh.mockResolvedValue(undefined);
  fake.confirm.mockResolvedValue(true);
  vi.stubGlobal("uni", { showToast: vi.fn() });
});
afterEach(() => vi.unstubAllGlobals());
describe("WeChat payment eligibility after phone login", () => {
  it("guides an unbound member to account security before creating channel prepay", async () => {
    const { payOrder } = await import("../src/utils/orderPayment");
    await payOrder("SJ1", false);
    expect(fake.refresh).toHaveBeenCalledOnce();
    expect(fake.navigate).toHaveBeenCalledWith("security");
    expect(fake.prepay).not.toHaveBeenCalled();
    expect(fake.nativePay).not.toHaveBeenCalled();
  });
  it("leaves the unpaid order untouched when the member declines binding", async () => {
    fake.confirm.mockResolvedValue(false);
    const { payOrder } = await import("../src/utils/orderPayment");
    await payOrder("SJ1", false);
    expect(fake.navigate).not.toHaveBeenCalled();
    expect(fake.prepay).not.toHaveBeenCalled();
  });
  it("never accepts development prepay as a production payment", async () => {
    fake.profile.wechatBound = true;
    fake.prepay.mockResolvedValue({ mode: "DEVELOPMENT", orderNo: "SJ1" });
    const { payOrder } = await import("../src/utils/orderPayment");
    await expect(payOrder("SJ1", false)).rejects.toThrow(
      "生产环境不可使用测试支付",
    );
    expect(fake.devPay).not.toHaveBeenCalled();
    expect(fake.nativePay).not.toHaveBeenCalled();
  });
});
