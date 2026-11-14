import { beforeEach, afterEach, describe, it, vi, expect } from "vitest";
let native: Record<string, ReturnType<typeof vi.fn>>;
beforeEach(() => {
  vi.resetModules();
  native = {
    redirectTo: vi.fn(),
    navigateTo: vi.fn(),
    getStorageSync: vi.fn(() => "test-session"),
    showToast: vi.fn(),
  };
  vi.stubGlobal("uni", native);
});
afterEach(() => vi.unstubAllGlobals());
describe("Successful order creation completion", () => {
  it("opens the created order even when the supplementary cart reload fails", async () => {
    const { openCreatedOrder } = await import("../src/utils/orderCompletion");
    const refresh = vi.fn().mockRejectedValue(new Error("network"));
    openCreatedOrder("SJ42", refresh);
    expect(native.redirectTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/pages/orders/detail?id=SJ42" }),
    );
    expect(refresh).toHaveBeenCalledOnce();
    await Promise.resolve();
  });
  it("does not wait for a pending supplementary request before navigating", async () => {
    const { openCreatedOrder } = await import("../src/utils/orderCompletion");
    const refresh = vi.fn(() => new Promise(() => undefined));
    expect(openCreatedOrder("SJ43", refresh)).toBeUndefined();
    expect(native.redirectTo).toHaveBeenCalledOnce();
  });
  it("keeps access to the order list if native detail navigation fails", async () => {
    native.redirectTo.mockImplementation((options) => options.fail());
    const { openCreatedOrder } = await import("../src/utils/orderCompletion");
    openCreatedOrder("SJ44", async () => undefined);
    expect(native.navigateTo).toHaveBeenCalledWith({
      url: "/pages/orders/index",
    });
    expect(native.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining("订单已创建") }),
    );
  });
});
