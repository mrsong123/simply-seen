import { tradeApi } from "../api/trade";
import { payWithWechat } from "./wechat";
import { confirm } from "./useTask";
export async function payOrder(
  orderId: string,
  devMode: boolean,
): Promise<void> {
  const parameters = await tradeApi.prepay(orderId);
  if ("mode" in parameters) {
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
