import { reactive } from "vue";
export const state = reactive({
  toast: "",
  error: "",
  pending: 0,
  member: JSON.parse(localStorage.getItem("sj-member") || "null"),
  admin: JSON.parse(localStorage.getItem("sj-admin") || "null"),
  config: {} as any,
});
export function message(text: string, error = false) {
  state.toast = text;
  state.error = error ? text : "";
  setTimeout(() => {
    if (state.toast === text) {
      state.toast = "";
      state.error = "";
    }
  }, 4500);
}
export function session(data: any, admin = false) {
  const key = admin ? "sj-admin" : "sj-member";
  localStorage.setItem(key, JSON.stringify(data));
  if (admin) state.admin = data;
  else state.member = data;
}
export async function logout(admin = false) {
  try {
    await api("/auth/logout", { method: "POST" }, admin);
  } finally {
    localStorage.removeItem(admin ? "sj-admin" : "sj-member");
    if (admin) state.admin = null;
    else state.member = null;
  }
}
export async function api(
  path: string,
  options: any = {},
  admin = false,
): Promise<any> {
  state.pending++;
  try {
    const headers: any = {
      "Content-Type": "application/json",
      ...options.headers,
    };
    const token = (admin ? state.admin : state.member)?.token;
    if (token) headers.Authorization = "Bearer " + token;
    if (options.key) headers["Idempotency-Key"] = options.key;
    const response = await fetch(
      (import.meta.env.VITE_API_BASE_URL || "") + path,
      {
        ...options,
        headers,
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
      },
    );
    let result;
    try {
      result = await response.json();
    } catch {
      throw new Error("服务响应异常，请检查接口配置");
    }
    if (!response.ok || result.code !== "OK") {
      if (response.status === 401) {
        localStorage.removeItem(admin ? "sj-admin" : "sj-member");
        if (admin) state.admin = null;
        else state.member = null;
      }
      throw new Error(result.message || "操作失败");
    }
    return result.data;
  } catch (e: any) {
    message(e.message || "网络连接失败", true);
    throw e;
  } finally {
    state.pending--;
  }
}
export const key = () => crypto.randomUUID();
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
