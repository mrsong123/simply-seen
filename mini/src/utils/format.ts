export const money = (value?: number) => ((value || 0) / 100).toFixed(2);
export const time = (value?: number) =>
  value
    ? new Date(value).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" })
    : "—";
export function image(url?: string) {
  if (!url) return "";
  return url.startsWith("https://")
    ? url
    : import.meta.env.VITE_ASSET_BASE_URL + url.replace(".svg", ".png");
}
export const labels: Record<string, string> = {
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
export const label = (value: string) => labels[value] || value;
