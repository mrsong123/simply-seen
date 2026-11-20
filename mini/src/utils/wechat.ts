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
export async function requireWechatPrivacy(): Promise<void> {
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
}
export async function wechatLoginCode(): Promise<string> {
  await requireWechatPrivacy();
  return new Promise((resolve, reject) =>
    uni.login({
      provider: "weixin",
      success: (result) =>
        result.code
          ? resolve(result.code)
          : reject(new Error("微信未返回有效登录凭证，请重试")),
      fail: reject,
    }),
  );
}
export async function chooseShippingAddress(): Promise<Address> {
  await requireWechatPrivacy();
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
  if (
    !parameters.timeStamp ||
    !parameters.nonceStr ||
    !parameters.package?.startsWith("prepay_id=") ||
    parameters.signType !== "RSA" ||
    !parameters.paySign
  )
    return Promise.reject(new Error("微信支付参数不完整，请重新发起支付"));
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
  if (!mchId || !appId || !packageInfo)
    return Promise.reject(
      new Error("微信收款参数不完整，请刷新记录或联系客服"),
    );
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
