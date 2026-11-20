import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useWebPayment } from "../../src/composables/storefront/useWebPayment";
import { paymentRemaining, validateWebPayment, webPaymentChannel } from "../../src/utils/payment";
import type { OrderDetail } from "../../src/types/models";
import type { NativePayment } from "../../src/types/payment";
const fixture = (): NativePayment => ({ mode: "NATIVE", codeUrl: "weixin://wxpay/bizpayurl?pr=controlled", orderNo: "SJ1", expiresAt: Date.now() + 60000 });
const disposers: (() => void)[] = [];
beforeEach(() => { vi.useFakeTimers(); vi.setSystemTime(new Date("2026-11-14T12:00:00Z")); });
afterEach(() => { disposers.splice(0).forEach(dispose => dispose()); vi.useRealTimers(); });
function setup(options: Record<string, unknown> = {}) {
  const dependencies = {
    orderNo: () => "SJ1", prepay: vi.fn().mockResolvedValue(fixture()),
    query: vi.fn().mockResolvedValue({ id: "SJ1", state: "CREATED" } as OrderDetail),
    onOrder: vi.fn(), onDevelopment: vi.fn(), onConfirmed: vi.fn(), visible: () => true,
    ...options,
  };
  const controller = useWebPayment(dependencies);
  disposers.push(controller.close);
  return { dependencies, controller };
}
describe("real web payment initiation and polling", () => {
  it("selects native for desktop, optional H5 for mobile, and preserves locked channel", () => {
    expect(webPaymentChannel("h5", false)).toBe("NATIVE");
    expect(webPaymentChannel("h5", true)).toBe("H5");
    expect(webPaymentChannel("native", true)).toBe("NATIVE");
    expect(webPaymentChannel("h5", true, "NATIVE")).toBe("NATIVE");
    expect(webPaymentChannel("native", false, "MINI_PROGRAM")).toBe("MINI_PROGRAM");
  });
  it("rejects mismatched order evidence, expired codes and insecure links", () => {
    expect(() => validateWebPayment(fixture(), "another")).toThrow("当前订单不一致");
    expect(() => validateWebPayment({ ...fixture(), expiresAt: Date.now() }, "SJ1")).toThrow("已过期");
    expect(() => validateWebPayment({ ...fixture(), codeUrl: "javascript:alert(1)" }, "SJ1")).toThrow("二维码格式无效");
    expect(() => validateWebPayment({ mode: "H5", orderNo: "SJ1", h5Url: "http://insecure.example", expiresAt: Date.now() + 1000 }, "SJ1")).toThrow("HTTPS");
    expect(paymentRemaining(Date.now() + 61000)).toBe("1:01");
  });
  it("uses the actual prepay channel and only confirms after server payment evidence", async () => {
    const { controller, dependencies } = setup();
    await controller.begin("NATIVE");
    expect(dependencies.prepay).toHaveBeenCalledWith("NATIVE");
    expect(controller.payment.value?.mode).toBe("NATIVE");
    expect(dependencies.onConfirmed).not.toHaveBeenCalled();
    dependencies.query.mockResolvedValueOnce({ id: "SJ1", state: "PAID" } as OrderDetail);
    await vi.advanceTimersByTimeAsync(5000);
    expect(dependencies.onConfirmed).toHaveBeenCalledWith("PAID");
    expect(controller.payment.value).toBeNull();
    await vi.advanceTimersByTimeAsync(60000);
    expect(dependencies.query).toHaveBeenCalledTimes(1);
  });
  it("keeps development confirmation separate from real QR/H5", async () => {
    const { controller, dependencies } = setup({ prepay: vi.fn().mockResolvedValue({ mode: "DEVELOPMENT", orderNo: "SJ1" }) });
    await controller.begin("NATIVE");
    expect(dependencies.onDevelopment).toHaveBeenCalledOnce();
    expect(controller.payment.value).toBeNull();
    await vi.advanceTimersByTimeAsync(15000);
    expect(dependencies.query).not.toHaveBeenCalled();
  });
  it("bounds automatic queries and still permits an explicit manual query", async () => {
    const { controller, dependencies } = setup({ maxPolls: 1 });
    await controller.begin("NATIVE");
    await vi.advanceTimersByTimeAsync(10000);
    expect(controller.progress.value).toBe("PAUSED");
    expect(dependencies.query).toHaveBeenCalledTimes(1);
    await controller.check();
    expect(dependencies.query).toHaveBeenCalledTimes(2);
  });
  it("retains a retryable error and stops at expiry instead of declaring success", async () => {
    const { controller, dependencies } = setup({ query: vi.fn().mockRejectedValue(new Error("渠道暂不可用")), prepay: vi.fn().mockResolvedValue({ ...fixture(), expiresAt: Date.now() + 1000 }) });
    await controller.begin("NATIVE");
    await vi.advanceTimersByTimeAsync(5000);
    expect(controller.error.value).toBe("渠道暂不可用");
    expect(controller.progress.value).toBe("EXPIRED");
    expect(dependencies.onConfirmed).not.toHaveBeenCalled();
  });
  it("rejects payment evidence belonging to another order", async () => {
    const { controller, dependencies } = setup({ query: vi.fn().mockResolvedValue({ id: "SJ_ANOTHER", state: "PAID" }) });
    await controller.begin("NATIVE");
    await controller.check();
    expect(controller.error.value).toContain("当前订单不一致");
    expect(dependencies.onOrder).not.toHaveBeenCalled();
    expect(dependencies.onConfirmed).not.toHaveBeenCalled();
    expect(controller.payment.value?.orderNo).toBe("SJ1");
  });
  it("ignores delayed evidence from a closed payment or a different route", async () => {
    let resolve!: (value: OrderDetail) => void;
    const { controller, dependencies } = setup({ query: vi.fn().mockImplementation(() => new Promise<OrderDetail>(done => { resolve = done; })) });
    await controller.begin("NATIVE");
    const queried = controller.check();
    controller.close();
    resolve({ id: "SJ1", state: "PAID" } as OrderDetail);
    await queried;
    expect(dependencies.onConfirmed).not.toHaveBeenCalled();
    expect(dependencies.onOrder).not.toHaveBeenCalled();
  });
});
