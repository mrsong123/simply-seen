import type { PaymentChannel, PaymentResult, WebPayment } from "@/types/payment";

export function webPaymentChannel(mode: unknown, mobile: boolean, locked?: string): PaymentChannel {
  if (["MINI_PROGRAM", "NATIVE", "H5"].includes(String(locked)))
    return locked as PaymentChannel;
  return mode === "h5" && mobile ? "H5" : "NATIVE";
}

export function validateWebPayment(result: PaymentResult, orderNo: string): WebPayment | null {
  if (result.orderNo !== orderNo) throw new Error("支付凭证与当前订单不一致，请重新加载订单");
  if (result.mode === "DEVELOPMENT") return null;
  if (!Number.isFinite(result.expiresAt) || result.expiresAt <= Date.now())
    throw new Error("支付凭证已过期，请查询订单状态后重新发起");
  if (result.mode === "NATIVE") {
    const address = new URL(result.codeUrl);
    if (address.protocol !== "weixin:" || address.hostname !== "wxpay")
      throw new Error("付款二维码格式无效，请稍后重试");
  } else if (result.mode === "H5") {
    if (new URL(result.h5Url).protocol !== "https:")
      throw new Error("支付链接必须使用HTTPS，请联系客服核对渠道配置");
  } else throw new Error("当前支付方式需要在微信小程序完成");
  return result;
}

export function paymentRemaining(expiresAt: number, now = Date.now()): string {
  const seconds = Math.max(0, Math.ceil((expiresAt - now) / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}
