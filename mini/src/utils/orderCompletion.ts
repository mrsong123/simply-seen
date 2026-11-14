import { navigate, pathFor } from "./router";
export function openCreatedOrder(
  orderId: string,
  refreshCart: () => Promise<unknown>,
): void {
  // The server has committed the order. Navigation must not depend on a follow-up GET.
  uni.redirectTo({
    url: pathFor("order", { id: orderId }),
    fail: () => {
      uni.showToast({ title: "订单已创建，请在订单列表中查看", icon: "none" });
      navigate("orders");
    },
  });
  void refreshCart().catch(() => undefined);
}
