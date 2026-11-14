import type { Address, WechatPrepay } from "../types/models";
interface PrivacyApi {
  getPrivacySetting(options: {
    success: (result: { needAuthorization: boolean }) => void;
    fail: (reason: unknown) => void;
  }): void;
  requirePrivacyAuthorize(options: {
    success: () => void;
    fail: (reason: unknown) => void;
  }): void;
}
export async function wechatLoginCode(): Promise<string> {
  const privacy = wx as typeof wx & PrivacyApi;
  if (!privacy.getPrivacySetting || !privacy.requirePrivacyAuthorize)
    throw new Error("微信版本过旧，请更新后登录");
  await new Promise<void>((resolve, reject) =>
    privacy.getPrivacySetting({
      success: (result) => {
        if (!result.needAuthorization) {
          resolve();
          return;
        }
        privacy.requirePrivacyAuthorize({ success: resolve, fail: reject });
      },
      fail: reject,
    }),
  );
  return new Promise((resolve, reject) =>
    uni.login({
      provider: "weixin",
      success: (result) => resolve(result.code),
      fail: reject,
    }),
  );
}
export function chooseShippingAddress(): Promise<Address> {
  return new Promise((resolve, reject) =>
    uni.chooseAddress({
      success: (r) =>
        resolve({
          name: r.userName,
          phone: r.telNumber,
          region: r.provinceName + " " + r.cityName + " " + r.countyName,
          detail: r.detailInfo,
          is_default: true,
        }),
      fail: reject,
    }),
  );
}
export function payWithWechat(parameters: WechatPrepay): Promise<void> {
  return new Promise((resolve, reject) =>
    wx.requestPayment({
      ...parameters,
      success: () => resolve(),
      fail: reject,
    }),
  );
}
interface MerchantTransferApi {
  requestMerchantTransfer(options: {
    mchId: string;
    appId: string;
    package: string;
    success: () => void;
    fail: (reason: unknown) => void;
  }): void;
}
export function confirmMerchantTransfer(
  mchId: string,
  appId: string,
  packageInfo: string,
): Promise<void> {
  const platform = wx as typeof wx & Partial<MerchantTransferApi>;
  if (!platform.requestMerchantTransfer)
    return Promise.reject(new Error("请更新微信后确认收款"));
  return new Promise((resolve, reject) =>
    platform.requestMerchantTransfer!({
      mchId,
      appId,
      package: packageInfo,
      success: resolve,
      fail: reject,
    }),
  );
}
