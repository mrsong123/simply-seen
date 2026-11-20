import { tradeApi } from "../api/trade";
import { payWithWechat } from "./wechat";
import { confirm } from "./useTask";
import { useSessionStore } from "../store/session";
import { navigate } from "./router";
export async function payOrder(
  orderId: string,
  devMode: boolean,
  channel?: string,
  paymentState?: string,
): Promise<void> {
  if (paymentState === "CLOSING")
    throw new Error("订单正在核对取消，请查询结果或稍后重试取消，不要再次付款");
  if (isBrowserPayment(channel))
    throw new Error(
      "此订单已选择网页支付，请使用订单中的网页链接完成，或取消后在小程序重新下单",
    );
  if (!devMode) {
    const session = useSessionStore();
    await session.refresh();
    if (!session.profile?.wechatBound) {
      if (
        await confirm(
          "当前会员尚未绑定微信。请在账户与安全中绑定当前微信后，返回此订单继续付款。",
          "先绑定微信",
        )
      )
        navigate("security");
      return;
    }
  }
  const parameters = await tradeApi.prepay(orderId);
  if ("mode" in parameters) {
    if (parameters.mode !== "DEVELOPMENT")
      throw new Error(
        "此订单已选择网页支付，请返回原网页完成，或取消后重新下单",
      );
    if (!devMode) throw new Error("生产环境不可使用测试支付");
    if (
      await confirm("仅更新开发测试状态，不涉及真实微信扣款。", "开发测试付款")
    )
      await tradeApi.devPay(orderId);
    return;
  }
  try {
    await payWithWechat(parameters);
  } catch {
    uni.showToast({ title: "支付未完成，请查询订单状态", icon: "none" });
  }
  // Native success/cancellation is not a payment fact; always ask the server.
  await tradeApi.status(orderId);
}

export function isBrowserPayment(channel?: string): boolean {
  return channel === "NATIVE" || channel === "H5";
}

export function browserOrderLink(base: string, orderId: string): string {
  if (!/^https:\/\/[^/?#\s]+(?:\/[^?#\s]*)?$/.test(base))
    throw new Error("网页商城地址未正确配置，请联系客服");
  return base.replace(/\/+$/, "") + "/orders/" + encodeURIComponent(orderId);
}
