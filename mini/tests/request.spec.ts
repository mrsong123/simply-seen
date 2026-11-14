import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
type NativeRequest = {
  url: string;
  method: string;
  data: string;
  header: Record<string, string>;
  timeout: number;
  success: (result: unknown) => void;
  fail: (error: unknown) => void;
};
let current: NativeRequest | undefined;
let native: Record<string, ReturnType<typeof vi.fn>>;
async function transport() {
  return (await import("../src/utils/request")).request;
}
function success(data: unknown, statusCode = 200) {
  current!.success({ statusCode, header: {}, data });
}
beforeEach(() => {
  vi.resetModules();
  current = undefined;
  native = {
    request: vi.fn((options: NativeRequest) => {
      current = options;
    }),
    getStorageSync: vi.fn(() => "session-test"),
    removeStorageSync: vi.fn(),
    showToast: vi.fn(),
    $emit: vi.fn(),
  };
  vi.stubGlobal("uni", native);
});
afterEach(() => vi.unstubAllGlobals());
async function ready() {
  await vi.waitFor(() => expect(current).toBeDefined());
}
describe("Axios adapter on the WeChat native transport", () => {
  it("omits existing credentials from public phone authentication and keeps the active session on a failed anonymous login", async () => {
    const request = await transport();
    const pending = request.post(
      "/auth/phone",
      { phone: "13800138000", code: "000000" },
      { anonymous: true },
    );
    const assertion = expect(pending).rejects.toMatchObject({
      code: "OTP_INVALID",
    });
    await ready();
    expect(current!.header.Authorization).toBeUndefined();
    success({ code: "OTP_INVALID", message: "验证码不正确" }, 401);
    await assertion;
    expect(native.removeStorageSync).not.toHaveBeenCalled();
    expect(native.$emit).not.toHaveBeenCalled();
  });
  it("serializes JSON and injects the existing session and idempotency headers", async () => {
    const request = await transport();
    const pending = request.post<{ id: string }>(
      "/orders",
      { quoteId: "Q1", addressId: 7 },
      { idempotencyKey: "order-test-001" },
    );
    await ready();
    expect(current!.url).toBe("https://api.example.test/api/v1/orders");
    expect(current!.method).toBe("POST");
    expect(JSON.parse(current!.data)).toEqual({ quoteId: "Q1", addressId: 7 });
    expect(current!.header.Authorization).toBe("Bearer session-test");
    expect(current!.header["Idempotency-Key"]).toBe("order-test-001");
    success({ code: "OK", data: { id: "SJ1" } });
    await expect(pending).resolves.toEqual({ id: "SJ1" });
  });
  it("encodes GET search params without a body or query-string concatenation in page code", async () => {
    const request = await transport();
    const pending = request.get("/products", {
      params: { category: 2, search: "器物 茶" },
    });
    await ready();
    const url = new URL(current!.url);
    expect(url.searchParams.get("search")).toBe("器物 茶");
    expect(url.searchParams.get("category")).toBe("2");
    expect(current!.method).toBe("GET");
    success({ code: "OK", data: [] });
    await expect(pending).resolves.toEqual([]);
  });
  it("rejects 401, clears the stored token and notifies all session stores", async () => {
    const request = await transport();
    const pending = request.get("/me");
    const assertion = expect(pending).rejects.toMatchObject({
      code: "AUTH_REQUIRED",
      message: "请重新登录",
      notified: true,
    });
    await ready();
    success(
      { code: "AUTH_REQUIRED", message: "请重新登录", requestId: "req-auth" },
      401,
    );
    await assertion;
    expect(native.removeStorageSync).toHaveBeenCalledWith("sj-token");
    expect(native.$emit).toHaveBeenCalledWith("session-expired");
    expect(native.showToast).toHaveBeenCalledTimes(1);
  });
  it("preserves a state conflict as a rejection rather than treating it as business success", async () => {
    const request = await transport();
    const pending = request.post("/orders", {});
    const assertion = expect(pending).rejects.toMatchObject({
      code: "STOCK_NOT_ENOUGH",
      requestId: "req-stock",
    });
    await ready();
    success(
      { code: "STOCK_NOT_ENOUGH", message: "库存不足", requestId: "req-stock" },
      409,
    );
    await assertion;
    expect(native.removeStorageSync).not.toHaveBeenCalled();
  });
  it("does not send stale credentials when the session storage is empty", async () => {
    native.getStorageSync.mockReturnValue("");
    const request = await transport();
    const pending = request.get("/config");
    await ready();
    expect(current!.header.Authorization).toBeUndefined();
    success({ code: "OK", data: { devMode: false } });
    await pending;
  });
  it("rejects native network failures and shows a single actionable message", async () => {
    const request = await transport();
    const pending = request.get("/products");
    const assertion = expect(pending).rejects.toMatchObject({
      code: "ERR_NETWORK",
      notified: true,
    });
    await ready();
    current!.fail({ errMsg: "request:fail timeout" });
    await assertion;
    expect(native.showToast).toHaveBeenCalledTimes(1);
  });
  it("does not accept a non-OK envelope even when HTTP returns 200", async () => {
    const request = await transport();
    const pending = request.post("/orders", {});
    const assertion = expect(pending).rejects.toMatchObject({
      code: "PAYMENT_PENDING",
    });
    await ready();
    success({ code: "PAYMENT_PENDING", message: "支付确认中" });
    await assertion;
  });
  it("reports a wrong API endpoint returning HTML as an actionable configuration error", async () => {
    const request = await transport();
    const pending = request.get("/config");
    const assertion = expect(pending).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
      notified: true,
    });
    await ready();
    success("<html>Wrong endpoint</html>");
    await assertion;
    expect(native.showToast).toHaveBeenCalledTimes(1);
  });
});
