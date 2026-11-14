export const money = (v: number | undefined) =>
  new Intl.NumberFormat("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format((v || 0) / 100);
export const date = (v: number | undefined) =>
  v
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Shanghai",
      }).format(new Date(v))
    : "—";
export const labels: Record<string, string> = {
  CREATED: "待付款",
  PAID: "待发货",
  SHIPPED: "待收货",
  COMPLETED: "已完成",
  CLOSED: "已关闭",
  REFUNDED: "已退款",
  SUCCESS: "已成功",
  INIT: "待支付",
  PREPAY: "支付确认中",
  UNKNOWN: "结果核对中",
  PENDING: "待结算",
  SETTLED: "已结算",
  REVERSED: "已冲回",
  REQUESTED: "待审核",
  REVIEWED: "已审核",
  PROCESSING: "处理中",
  ABNORMAL: "需人工复核",
  REVIEWING: "待财务复核",
  APPROVED: "已审批",
  SUBMITTED: "已提交渠道",
  CONFIRMING: "待确认收款",
  FAILED: "失败已退回",
  REJECTED: "已驳回",
  DRAFT: "草稿待复核",
  ACTIVE: "生效中",
  ARCHIVED: "历史版本",
  INVALIDATED: "待重新结算",
  ON_SALE: "销售中",
  OFF_SALE: "已下架",
  ADMIN: "管理员",
  OPERATOR: "运营",
  FINANCE: "财务",
  OPEN: "待处理",
  RESOLVED: "已处理",
  points: "可用积分",
  points_held: "冻结积分",
  available: "可用佣金",
  reserved: "提现冻结",
  debt: "待追偿",
};
export const label = (s: string) => labels[s] || s;
