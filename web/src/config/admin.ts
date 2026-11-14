export type AdminColumn = [string, string];
export interface AdminDefinition {
  title: string;
  subtitle: string;
  icon: string;
  roles: string[];
  columns?: AdminColumn[];
}
export const adminDefinitions: Record<string, AdminDefinition> = {
  overview: {
    title: "经营概览",
    subtitle: "看见经营的每一步，让每一笔交易有据可循。",
    icon: "LayoutDashboard",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
  },
  products: {
    title: "商品管理",
    subtitle: "认真对待每一件商品的价格、规格与库存。",
    icon: "Package",
    roles: ["ADMIN", "OPERATOR"],
    columns: [
      ["name", "商品"],
      ["category_id", "分类"],
      ["status", "状态"],
      ["points_percent", "积分上限 %"],
      ["commission_percent", "佣金上限 %"],
    ],
  },
  orders: {
    title: "订单与履约",
    subtitle: "从支付到送达，妥帖处理每一份期待。",
    icon: "ShoppingBag",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
    columns: [
      ["id", "订单号"],
      ["member_name", "会员"],
      ["cash_due", "实付"],
      ["state", "订单状态"],
      ["payment_state", "支付"],
      ["created_at", "下单时间"],
    ],
  },
  refunds: {
    title: "售后退款",
    subtitle: "以原始交易为依据，让每次售后清晰可追踪。",
    icon: "RefreshCw",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
    columns: [
      ["id", "退款单号"],
      ["order_id", "来源订单"],
      ["amount", "退款金额"],
      ["reason", "申请原因"],
      ["state", "状态"],
    ],
  },
  members: {
    title: "会员管理",
    subtitle: "珍惜每一份信任，保护会员资料与资产。",
    icon: "Users",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
    columns: [
      ["name", "会员"],
      ["invite_code", "邀请码"],
      ["points", "积分"],
      ["available", "可用佣金"],
      ["reserved", "冻结"],
      ["debt", "待追偿"],
      ["created_at", "加入时间"],
    ],
  },
  commissions: {
    title: "成交佣金",
    subtitle: "最多两级，按有效成交与毛利预算计佣。",
    icon: "Share2",
    roles: ["ADMIN", "FINANCE"],
    columns: [
      ["order_id", "来源订单"],
      ["beneficiary_id", "受益会员"],
      ["level_no", "归因级别"],
      ["original_amount", "原始佣金"],
      ["amount", "当前佣金"],
      ["state", "状态"],
    ],
  },
  ledger: {
    title: "资产账本",
    subtitle: "只追加的资产流水，逐笔可查、余额可重建。",
    icon: "Wallet",
    roles: ["ADMIN", "FINANCE"],
    columns: [
      ["member_id", "会员"],
      ["asset", "资产"],
      ["amount", "变动"],
      ["balance", "变动后余额"],
      ["note", "说明"],
      ["created_at", "时间"],
    ],
  },
  withdrawals: {
    title: "提现与出款",
    subtitle: "审核、执行相互分离，到账以渠道确认结果为准。",
    icon: "Wallet",
    roles: ["ADMIN", "FINANCE"],
    columns: [
      ["id", "提现单号"],
      ["member_id", "会员"],
      ["gross", "税前"],
      ["tax", "税款"],
      ["net", "税后"],
      ["state", "状态"],
    ],
  },
  rules: {
    title: "活动与分销规则",
    subtitle: "规则版本冻结，双人审批生效，历史交易不受后续调整影响。",
    icon: "SlidersHorizontal",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
    columns: [
      ["id", "版本号"],
      ["state", "状态"],
      ["creator", "经办人"],
      ["reviewer", "复核人"],
      ["created_at", "创建时间"],
    ],
  },
  settlements: {
    title: "月度优惠结算",
    subtitle: "历史月份生成草稿，按已批预算复核入账。",
    icon: "Gift",
    roles: ["ADMIN", "OPERATOR", "FINANCE"],
    columns: [
      ["period", "活动月份"],
      ["budget", "预算上限"],
      ["state", "状态"],
      ["creator", "经办人"],
      ["reviewer", "复核人"],
    ],
  },
  reconciliation: {
    title: "对账与异常",
    subtitle: "核对账本与渠道事实，任何差异都需保留处理依据。",
    icon: "Shield",
    roles: ["ADMIN", "FINANCE"],
  },
  accounts: {
    title: "账号与权限",
    subtitle: "独立账号、角色授权，敏感操作均有审计记录。",
    icon: "Users",
    roles: ["ADMIN"],
    columns: [
      ["username", "账号"],
      ["role", "角色"],
      ["active", "启用状态"],
    ],
  },
  audit: {
    title: "操作审计",
    subtitle: "记录经办人、对象、操作和时间，追溯每一次管理行为。",
    icon: "FileText",
    roles: ["ADMIN", "FINANCE"],
    columns: [
      ["actor_id", "操作人"],
      ["action", "操作"],
      ["resource", "业务对象"],
      ["detail", "操作依据"],
      ["created_at", "时间"],
    ],
  },
};
export const ruleFields: AdminColumn[] = [
  ["level1Bps", "一级佣金（基点，500=5%）"],
  ["level2Bps", "二级佣金（基点）"],
  ["marginSharePercent", "可分配毛利上限（%）"],
  ["settlementDays", "收货后结算等待天数"],
  ["pointsPer100Yuan", "每满100元赠积分"],
  ["freightFen", "基础运费（分）"],
  ["freeShippingFen", "包邮门槛（分）"],
  ["commissionBudgetFen", "每月佣金预算（分）"],
  ["monthlyPoolBps", "月活动池比例（基点）"],
  ["monthlyBudgetFen", "月活动预算（分）"],
  ["monthlyCapFen", "单人月奖励上限（分）"],
];
