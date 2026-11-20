import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  chooseShippingAddress,
  confirmMerchantTransfer,
  payWithWechat,
  wechatLoginCode,
} from "../src/utils/wechat";
const payment = {
  timeStamp: "1730000000",
  nonceStr: "nonce",
  package: "prepay_id=trusted",
  signType: "RSA" as const,
  paySign: "signed",
};
let platform: Record<string, ReturnType<typeof vi.fn>>;
beforeEach(() => {
  platform = {
    getPrivacySetting: vi.fn((options) =>
      options.success({ needAuthorization: false }),
    ),
    requirePrivacyAuthorize: vi.fn((options) => options.success()),
    requestPayment: vi.fn((options) => options.success()),
    requestMerchantTransfer: vi.fn((options) => options.success()),
  };
  vi.stubGlobal("wx", platform);
  vi.stubGlobal("uni", {
    login: vi.fn((options) => options.success({ code: "native-code" })),
    chooseAddress: vi.fn((options) =>
      options.success({
        userName: "导入测试",
        telNumber: "13800138000",
        provinceName: "浙江省",
        cityName: "杭州市",
        countyName: "西湖区",
        detailInfo: "开发测试地址",
      }),
    ),
  });
});
afterEach(() => vi.unstubAllGlobals());
describe("Native WeChat actions", () => {
  it("requests required privacy authorization before importing the native shipping address", async () => {
    platform.getPrivacySetting.mockImplementation((options) =>
      options.success({ needAuthorization: true }),
    );
    const result = await chooseShippingAddress();
    expect(platform.requirePrivacyAuthorize).toHaveBeenCalledOnce();
    expect(uni.chooseAddress).toHaveBeenCalledOnce();
    expect(result).toMatchObject({
      name: "导入测试",
      phone: "13800138000",
      region: "浙江省 杭州市 西湖区",
      is_default: true,
    });
  });
  it("does not read an address when native privacy consent is denied", async () => {
    platform.getPrivacySetting.mockImplementation((options) =>
      options.success({ needAuthorization: true }),
    );
    platform.requirePrivacyAuthorize.mockImplementation((options) =>
      options.fail({ errMsg: "privacy denied" }),
    );
    await expect(chooseShippingAddress()).rejects.toBeDefined();
    expect(uni.chooseAddress).not.toHaveBeenCalled();
  });
  it("rejects a native login success without a code", async () => {
    vi.mocked(uni.login).mockImplementation((options) => {
      options?.success?.({ code: "" } as never);
      return undefined as never;
    });
    await expect(wechatLoginCode()).rejects.toThrow("有效登录凭证");
  });
  it("passes verified payment parameters to the real native payment API and propagates cancellation", async () => {
    platform.requestPayment.mockImplementation((options) =>
      options.fail({ errMsg: "requestPayment:fail cancel" }),
    );
    await expect(payWithWechat(payment)).rejects.toMatchObject({
      errMsg: "requestPayment:fail cancel",
    });
    expect(platform.requestPayment).toHaveBeenCalledWith(
      expect.objectContaining(payment),
    );
  });
  it("does not open native payment when channel parameters are incomplete", async () => {
    await expect(payWithWechat({ ...payment, package: "" })).rejects.toThrow(
      "参数不完整",
    );
    expect(platform.requestPayment).not.toHaveBeenCalled();
  });
  it("passes merchant, application and opaque package to confirmation and preserves its failure", async () => {
    platform.requestMerchantTransfer.mockImplementation((options) =>
      options.fail({ errMsg: "requestMerchantTransfer:fail cancel" }),
    );
    await expect(
      confirmMerchantTransfer(
        "1900001109",
        "wx1234567890123456",
        "opaque-package",
      ),
    ).rejects.toBeDefined();
    expect(platform.requestMerchantTransfer).toHaveBeenCalledWith(
      expect.objectContaining({
        mchId: "1900001109",
        appId: "wx1234567890123456",
        package: "opaque-package",
      }),
    );
  });
  it("has actionable failures for unsupported clients and missing transfer parameters", async () => {
    delete platform.requestMerchantTransfer;
    await expect(
      confirmMerchantTransfer(
        "1900001109",
        "wx1234567890123456",
        "opaque-package",
      ),
    ).rejects.toThrow("更新微信");
    await expect(confirmMerchantTransfer("", "", "")).rejects.toThrow(
      "收款参数不完整",
    );
  });
});
