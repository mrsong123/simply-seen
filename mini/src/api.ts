const BASE = import.meta.env.VITE_API_BASE_URL;
export function api(
  path: string,
  method = "GET",
  data?: any,
  idempotency?: string,
): Promise<any> {
  return new Promise((resolve, reject) => {
    const header: any = { "Content-Type": "application/json" };
    const token = uni.getStorageSync("sj-token");
    if (token) header.Authorization = "Bearer " + token;
    if (idempotency) header["Idempotency-Key"] = idempotency;
    uni.request({
      url: BASE + path,
      method: method as any,
      data,
      header,
      success(r: any) {
        if (r.statusCode >= 200 && r.statusCode < 300 && r.data.code === "OK")
          resolve(r.data.data);
        else {
          if (r.statusCode === 401) uni.removeStorageSync("sj-token");
          const message = r.data?.message || "请求失败";
          uni.showToast({ title: message, icon: "none" });
          reject(new Error(message));
        }
      },
      fail() {
        uni.showToast({ title: "网络连接失败，请稍后重试", icon: "none" });
        reject(new Error("NETWORK"));
      },
    });
  });
}
export const money = (n: number) => ((n || 0) / 100).toFixed(2);
export const time = (n: number) =>
  n ? new Date(n).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }) : "—";
export const key = () =>
  Date.now().toString(36) +
  "-" +
  Math.random().toString(36).slice(2) +
  "-" +
  Math.random().toString(36).slice(2);
export function image(url: string) {
  return url?.startsWith("https://")
    ? url
    : (import.meta.env.VITE_ASSET_BASE_URL || "") +
        url?.replace(".svg", ".png");
}
export const labels: any = {
  CREATED: "待支付",
  PAID: "待发货",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  REFUNDED: "已退款",
  CLOSED: "已关闭",
  REQUESTED: "待审核",
  REVIEWED: "已审核",
  PROCESSING: "退款处理中",
  SUCCESS: "已成功",
  REVIEWING: "待财务复核",
  APPROVED: "已审批",
  SUBMITTED: "渠道处理中",
  UNKNOWN: "核对中",
  CONFIRMING: "待确认收款",
  FAILED: "失败已退回",
  REJECTED: "已驳回",
  PENDING: "待结算",
  SETTLED: "已结算",
  REVERSED: "已冲回",
  ABNORMAL: "需人工复核",
  points: "可用积分",
  points_held: "冻结积分",
  available: "可用佣金",
  reserved: "提现冻结",
  debt: "待追偿",
};
