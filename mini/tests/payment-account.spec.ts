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

it("does not invoke mini payment for an order whose web channel is already selected", async () => {
  const { payOrder, browserOrderLink } = await import(
    "../src/utils/orderPayment"
  );
  await expect(payOrder("SJ1", false, "NATIVE")).rejects.toThrow("网页支付");
  expect(fake.refresh).not.toHaveBeenCalled();
  expect(fake.prepay).not.toHaveBeenCalled();
  expect(browserOrderLink("https://store.example.test/", "SJ 1")).toBe(
    "https://store.example.test/orders/SJ%201",
  );
  expect(() => browserOrderLink("javascript:alert(1)", "SJ1")).toThrow(
    "未正确配置",
  );
});
it("does not mistake a real web prepay payload for a development payment", async () => {
  fake.profile.wechatBound = true;
  fake.prepay.mockResolvedValue({
    mode: "H5",
    h5Url: "https://pay.example.test",
    orderNo: "SJ1",
  });
  const { payOrder } = await import("../src/utils/orderPayment");
  await expect(payOrder("SJ1", true)).rejects.toThrow("网页支付");
  expect(fake.devPay).not.toHaveBeenCalled();
});
it("keeps querying the server after native payment cancellation", async () => {
  fake.profile.wechatBound = true;
  fake.prepay.mockResolvedValue({
    timeStamp: "1",
    nonceStr: "n",
    package: "prepay_id=p",
    signType: "RSA",
    paySign: "s",
  });
  fake.nativePay.mockRejectedValue({ errMsg: "requestPayment:fail cancel" });
  const { payOrder } = await import("../src/utils/orderPayment");
  await payOrder("SJ1", false);
  expect(fake.status).toHaveBeenCalledWith("SJ1");
  expect(fake.devPay).not.toHaveBeenCalled();
});
it("prevents new payment while channel cancellation is being reconciled", async () => {
  const { payOrder } = await import("../src/utils/orderPayment");
  await expect(
    payOrder("SJ1", false, "MINI_PROGRAM", "CLOSING"),
  ).rejects.toThrow("正在核对取消");
  expect(fake.prepay).not.toHaveBeenCalled();
});
