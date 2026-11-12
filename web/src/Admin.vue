<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "./Icon.vue";
import {
  api,
  state,
  session,
  logout,
  money,
  date,
  label,
  message,
} from "./api";
const route = useRoute(),
  router = useRouter();
const page = computed(() => String(route.params.page || "overview"));
const user = ref<any>(state.admin?.profile),
  summary = ref<any>({}),
  rows = ref<any[]>([]),
  reconciliation = ref<any>({ batches: [], issues: [], outbox: [] }),
  categories = ref<any[]>([]),
  query = ref(""),
  status = ref(""),
  current = ref(1),
  modal = ref(""),
  form = ref<any>({}),
  selected = ref<any>(null),
  busy = ref(false),
  loaded = ref(false),
  login = ref({ username: "", password: "" }),
  mobile = ref(false),
  tab = ref("batches");
const definitions: any = {
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
const menu = computed(() =>
  Object.entries(definitions)
    .filter(([, v]: any) => v.roles.includes(user.value?.role))
    .map(([key, value]: any) => ({ key, ...value })),
);
const def = computed(() => definitions[page.value] || definitions.overview);
const canOperate = computed(() =>
  ["ADMIN", "OPERATOR"].includes(user.value?.role),
);
const canFinance = computed(() =>
  ["ADMIN", "FINANCE"].includes(user.value?.role),
);
const isAdmin = computed(() => user.value?.role === "ADMIN");
const data = computed(() =>
  page.value === "reconciliation"
    ? reconciliation.value[tab.value] || []
    : rows.value,
);
const filtered = computed(() =>
  data.value.filter(
    (r: any) =>
      (!query.value ||
        JSON.stringify(r).toLowerCase().includes(query.value.toLowerCase())) &&
      (!status.value || r.state === status.value || r.status === status.value),
  ),
);
const visible = computed(() =>
  filtered.value.slice((current.value - 1) * 10, current.value * 10),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const states = computed(() => [
  ...new Set(data.value.map((r: any) => r.state || r.status).filter(Boolean)),
]);
const columns = computed(() =>
  page.value !== "reconciliation"
    ? def.value.columns
    : tab.value === "batches"
      ? [
          ["period_day", "日期"],
          ["orders_total", "累计实付"],
          ["refunds_total", "累计退款"],
          ["transfers_total", "累计出款"],
          ["diff_count", "本批差异"],
          ["created_at", "时间"],
        ]
      : tab.value === "issues"
        ? [
            ["kind", "异常类型"],
            ["resource", "对象"],
            ["detail", "说明"],
            ["state", "状态"],
            ["created_at", "时间"],
          ]
        : [
            ["kind", "任务类型"],
            ["aggregate_id", "业务单号"],
            ["state", "状态"],
            ["attempts", "尝试次数"],
            ["last_error", "最近错误"],
          ],
);
const numericFields = [
  "cash_due",
  "amount",
  "original_amount",
  "available",
  "reserved",
  "debt",
  "gross",
  "tax",
  "net",
  "budget",
  "orders_total",
  "refunds_total",
  "transfers_total",
];
function cell(row: any, k: string) {
  if (k === "amount" || k === "balance") {
    if (row.asset?.startsWith("points")) return row[k] + " 点";
    return "¥" + money(row[k]);
  }
  if (numericFields.includes(k)) return "¥" + money(row[k]);
  if (k.endsWith("_at")) return date(row[k]);
  if (["state", "status", "role", "asset", "payment_state"].includes(k))
    return label(row[k]);
  if (k === "active") return row[k] ? "已启用" : "已停用";
  return row[k] ?? "—";
}
async function act(fn: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  try {
    await fn();
  } catch {
  } finally {
    busy.value = false;
  }
}
async function load() {
  if (!state.admin) return;
  loaded.value = false;
  current.value = 1;
  try {
    user.value = await api("/admin/me", {}, true);
    if (!def.value.roles.includes(user.value.role)) {
      router.replace("/admin");
      return;
    }
    if (page.value === "overview")
      summary.value = await api("/admin/summary", {}, true);
    else if (page.value === "reconciliation")
      reconciliation.value = await api("/admin/reconciliation", {}, true);
    else rows.value = await api("/admin/" + page.value, {}, true);
    categories.value = await api("/categories");
  } catch {
  } finally {
    loaded.value = true;
  }
}
watch(
  () => route.fullPath,
  () => {
    query.value = "";
    status.value = "";
    mobile.value = false;
    load();
  },
);
watch([query, status], () => (current.value = 1));
onMounted(load);
async function signIn() {
  await act(async () => {
    const s = await api("/auth/admin", { method: "POST", body: login.value });
    session(s, true);
    login.value.password = "";
    user.value = s.profile;
    await load();
  });
}
function open(type: string, row: any = {}) {
  selected.value = row;
  form.value = JSON.parse(JSON.stringify(row));
  modal.value = type;
  if (type === "product" && !row.id)
    form.value = {
      name: "",
      subtitle: "",
      description: "",
      image: "/art/cup.svg",
      color: "#e8e5df",
      category_id: categories.value[0]?.id || 1,
      status: "OFF_SALE",
      featured: false,
      points_percent: 20,
      commission_percent: 7,
      skus: [{ name: "标准款", code: "", price: 10000, cost: 5000, stock: 0 }],
    };
  if (type === "rule") {
    const active = rows.value.find((x) => x.state === "ACTIVE");
    form.value = active ? JSON.parse(active.config) : {};
  }
  if (type === "reviewWithdrawal")
    form.value = { approve: true, taxFen: 0, reason: "" };
  if (type === "reviewRefund") form.value = { approve: true, reason: "" };
  if (type === "ship") form.value = { carrier: "", trackingNo: "" };
  if (type === "account")
    form.value = { username: "", password: "", role: "OPERATOR" };
  if (type === "settlement")
    form.value = {
      period: new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1)
        .toLocaleDateString("sv-SE")
        .slice(0, 7),
    };
  if (type === "category") form.value = { name: "", sort_no: 0 };
  if (type === "password") form.value = { currentPassword: "", password: "" };
  if (type === "issue") form.value = { reason: "" };
  if (type === "resetPassword") form.value = { password: "" };
}
async function save(path: string, body: any = form.value) {
  await act(async () => {
    await api(path, { method: "POST", body }, true);
    modal.value = "";
    await load();
    message("操作已保存");
  });
}
async function viewOrder(row: any) {
  await act(async () => {
    selected.value = await api(
      "/admin/orders/" + (row.order_id || row.id),
      {},
      true,
    );
    modal.value = "order";
  });
}
async function reconcile() {
  await act(async () => {
    await api("/admin/reconciliation", { method: "POST" }, true);
    await load();
    message("对账批次已生成，请检查差异结果");
  });
}
function exportCsv() {
  const cols = columns.value;
  if (!cols?.length) return;
  const safe = (value: any) => {
    let str = String(value ?? "");
    if (/^[=+\-@\t\r]/.test(str)) str = "'" + str;
    return '"' + str.replaceAll('"', '""') + '"';
  };
  const text =
    "\ufeff" +
    [
      cols.map((c: any) => safe(c[1])).join(","),
      ...filtered.value.map((r: any) =>
        cols.map((c: any) => safe(cell(r, c[0]))).join(","),
      ),
    ].join("\r\n");
  const u = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8;" }),
  );
  const a = document.createElement("a");
  a.href = u;
  a.download = "素见-" + def.value.title + ".csv";
  a.click();
  URL.revokeObjectURL(u);
}
const ruleFields: any = [
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
const trend = computed(() => {
  const list = Array.from({ length: 7 }, (_, i) => {
    const dt = new Date(Date.now() - (6 - i) * 86400000);
    return {
      day: dt.toLocaleDateString("zh-CN", { month: "numeric", day: "numeric" }),
      total: 0,
    };
  });
  for (const o of summary.value.trend || []) {
    const age = Math.floor((Date.now() - o.created_at) / 86400000);
    if (age >= 0 && age < 7 && o.state !== "CREATED" && o.state !== "CLOSED")
      list[6 - age].total += o.cash_due;
  }
  return list;
});
const maxTrend = computed(() =>
  Math.max(100, ...trend.value.map((x) => x.total)),
);
</script>
<template>
  <div v-if="!state.admin" class="admin-login">
    <div class="login-story">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
      >
      <div>
        <div class="eyebrow">THOUGHTFUL BUSINESS</div>
        <h1>用心经营，<br />让美好发生。</h1>
        <p>每一份信任，都值得认真对待。</p>
      </div>
      <small>素见好物 · 运营工作台</small>
    </div>
    <div class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录运营工作台</h2>
        <p class="muted">使用您的独立管理账号安全登录。</p>
        <label class="input-label"
          >账号<input
            v-model="login.username"
            required
            autocomplete="username" /></label
        ><label class="input-label"
          >密码<input
            v-model="login.password"
            required
            type="password"
            autocomplete="current-password" /></label
        ><button class="button dark full" :disabled="busy">
          安全登录 <Icon name="ArrowRight" :size="17" /></button
        ><RouterLink to="/" class="back-link"
          ><Icon name="ArrowLeft" :size="16" /> 返回素见商城</RouterLink
        >
      </form>
    </div>
  </div>
  <div v-else class="admin-shell">
    <aside class="sidebar" :class="{ open: mobile }">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>BUSINESS STUDIO</small></span></RouterLink
      >
      <div class="sidebar-section">工作空间</div>
      <nav>
        <RouterLink
          v-for="m in menu"
          :key="m.key"
          :to="'/admin/' + (m.key === 'overview' ? '' : m.key)"
          :class="{ active: page === m.key }"
          ><Icon :name="m.icon" :size="19" /><span>{{ m.title }}</span
          ><i v-if="m.key === 'refunds' && summary.pendingRefunds">{{
            summary.pendingRefunds
          }}</i></RouterLink
        >
      </nav>
      <div class="sidebar-foot">
        <span class="status-dot"></span
        ><span>{{
          state.config.devMode ? "开发环境 · 无真实资金" : "生产业务环境"
        }}</span
        ><RouterLink to="/" aria-label="前往商城"
          ><Icon name="ArrowRight" :size="17"
        /></RouterLink>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <div>
          <button
            class="icon-button mobile-menu"
            aria-label="管理导航"
            @click="mobile = !mobile"
          >
            <Icon name="Menu" /></button
          ><span>工作台</span><Icon name="ChevronRight" :size="14" /><strong>{{
            def.title
          }}</strong>
        </div>
        <div>
          <span class="badge neutral">{{ label(user?.role) }}</span
          ><button class="profile-button" @click="open('password')">
            <span class="mini-avatar">{{
              user?.username?.slice(0, 1).toUpperCase()
            }}</span
            >{{ user?.username }}</button
          ><button
            class="icon-button"
            aria-label="退出管理后台"
            @click="
              act(async () => {
                await logout(true);
                router.replace('/admin');
              })
            "
          >
            <Icon name="LogOut" :size="18" />
          </button>
        </div>
      </header>
      <main class="admin-content">
        <div class="admin-title">
          <div>
            <div class="eyebrow">SIMPLY SEEN / BUSINESS</div>
            <h1>{{ def.title }}</h1>
            <p>{{ def.subtitle }}</p>
          </div>
          <div class="button-row">
            <button class="button small" :disabled="busy" @click="load">
              <Icon name="RefreshCw" :size="16" /> 刷新</button
            ><button
              v-if="page === 'products'"
              class="button small"
              @click="open('category')"
            >
              管理分类</button
            ><button
              v-if="
                page === 'products' ||
                page === 'accounts' ||
                (page === 'rules' && canOperate) ||
                (page === 'settlements' && canOperate)
              "
              class="button dark small"
              @click="
                open(
                  (
                    {
                      products: 'product',
                      accounts: 'account',
                      rules: 'rule',
                      settlements: 'settlement',
                    } as any
                  )[page],
                )
              "
            >
              <Icon name="Plus" :size="16" />
              {{
                (
                  {
                    products: "新增商品",
                    accounts: "新增账号",
                    rules: "新建规则草稿",
                    settlements: "生成月结草稿",
                  } as any
                )[page]
              }}</button
            ><button
              v-if="page === 'reconciliation'"
              class="button dark small"
              :disabled="busy"
              @click="reconcile"
            >
              <Icon name="Shield" :size="16" /> 执行对账
            </button>
          </div>
        </div>
        <template v-if="page === 'overview'"
          ><div class="metrics">
            <div
              v-for="(m, i) in [
                ['有效成交金额', '¥' + money(summary.gmv), 'Wallet'],
                ['累计订单', summary.orders || 0, 'ShoppingBag'],
                ['会员总数', summary.members || 0, 'Users'],
                ['待发货订单', summary.pendingOrders || 0, 'Truck'],
              ]"
              :key="i"
              class="metric-card"
            >
              <div>
                <span>{{ m[0] }}</span
                ><Icon :name="String(m[2])" :size="19" />
              </div>
              <strong>{{ m[1] }}</strong
              ><small>{{
                i === 0 ? "实付扣除成功退款" : "实时业务数据"
              }}</small>
            </div>
          </div>
          <div class="dashboard-grid">
            <div class="panel chart-panel">
              <div class="panel-title">
                <div>
                  <h3>最近七日成交</h3>
                  <p class="muted">已支付订单金额（元）</p>
                </div>
                <span class="badge neutral">近7天</span>
              </div>
              <div class="bar-chart">
                <div v-for="t in trend" :key="t.day" class="bar-column">
                  <small>¥{{ money(t.total) }}</small>
                  <div class="bar-track">
                    <div
                      :style="{
                        height: Math.max(2, (t.total / maxTrend) * 100) + '%',
                      }"
                    ></div>
                  </div>
                  <span>{{ t.day }}</span>
                </div>
              </div>
            </div>
            <div class="panel work-panel">
              <h3>待办事项</h3>
              <p class="muted">每一件，都认真回应。</p>
              <RouterLink v-if="canOperate" to="/admin/orders"
                ><span class="todo-icon"><Icon name="Truck" /></span>
                <div>待发货订单<small>及时履约，妥帖送达</small></div>
                <strong>{{ summary.pendingOrders || 0 }}</strong
                ><Icon name="ChevronRight" :size="16" /></RouterLink
              ><RouterLink v-if="canFinance" to="/admin/refunds"
                ><span class="todo-icon"><Icon name="RefreshCw" /></span>
                <div>待审核退款<small>核对原始订单与退款依据</small></div>
                <strong>{{ summary.pendingRefunds || 0 }}</strong
                ><Icon name="ChevronRight" :size="16" /></RouterLink
              ><RouterLink v-if="canFinance" to="/admin/withdrawals"
                ><span class="todo-icon"><Icon name="Wallet" /></span>
                <div>待复核提现<small>经办与执行分离</small></div>
                <strong>{{ summary.pendingWithdrawals || 0 }}</strong
                ><Icon name="ChevronRight" :size="16" /></RouterLink
              ><RouterLink v-if="canFinance" to="/admin/reconciliation"
                ><span class="todo-icon"><Icon name="Shield" /></span>
                <div>异常与差异<small>完整保留处理记录</small></div>
                <strong>{{ summary.issues || 0 }}</strong
                ><Icon name="ChevronRight" :size="16"
              /></RouterLink>
            </div>
          </div>
          <div class="admin-note">
            <Icon name="Leaf" :size="24" />
            <div>
              <h3>认真经营，也可以从容有序。</h3>
              <p>商品、订单、会员和财务，在同一个工作台清晰协作。</p>
            </div>
            <RouterLink to="/catalog" class="text-link"
              >查看商城 <Icon name="ArrowRight" :size="16"
            /></RouterLink></div
        ></template>
        <template v-else
          ><div
            v-if="page === 'reconciliation'"
            class="category-pills reconcile-tabs"
          >
            <button
              v-for="t in [
                ['batches', '对账批次'],
                ['issues', '差异工单'],
                ['outbox', '异步任务'],
              ]"
              :key="t[0]"
              :class="{ active: tab === t[0] }"
              @click="
                tab = t[0];
                current = 1;
              "
            >
              {{ t[1] }}
            </button>
          </div>
          <div class="data-panel">
            <div class="table-toolbar">
              <label class="search-field"
                ><Icon name="Search" :size="17" /><input
                  v-model="query"
                  placeholder="搜索当前记录"
                  aria-label="搜索管理记录"
              /></label>
              <div>
                <select
                  v-if="states.length"
                  v-model="status"
                  aria-label="按状态筛选"
                >
                  <option value="">全部状态</option>
                  <option v-for="s in states" :key="String(s)" :value="s">
                    {{ label(String(s)) }}
                  </option></select
                ><button class="button small" @click="exportCsv">
                  <Icon name="Download" :size="15" /> 导出记录
                </button>
              </div>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th v-for="col in columns" :key="col[0]">{{ col[1] }}</th>
                    <th class="right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in visible" :key="row.id">
                    <td v-for="col in columns" :key="col[0]">
                      <template v-if="col[0] === 'name' && page === 'products'"
                        ><div class="table-product">
                          <img
                            :src="row.image"
                            :alt="row.name"
                            :style="{ background: row.color }"
                          />
                          <div>
                            <strong>{{ row.name }}</strong
                            ><small>{{ row.skus?.length }} 个规格</small>
                          </div>
                        </div></template
                      ><span
                        v-else-if="
                          ['state', 'status', 'payment_state'].includes(col[0])
                        "
                        class="badge"
                        :class="{
                          neutral: [
                            'DRAFT',
                            'CLOSED',
                            'REFUNDED',
                            'ARCHIVED',
                          ].includes(row[col[0]]),
                          warning: [
                            'UNKNOWN',
                            'ABNORMAL',
                            'OPEN',
                            'REVIEWING',
                          ].includes(row[col[0]]),
                        }"
                        >{{ cell(row, col[0]) }}</span
                      ><span
                        v-else
                        :class="{
                          mono: col[0] === 'id' || col[0].endsWith('_id'),
                          'truncate-cell':
                            col[0] === 'detail' || col[0] === 'reason',
                        }"
                        :title="String(row[col[0]] ?? '')"
                        >{{ cell(row, col[0]) }}</span
                      >
                    </td>
                    <td class="table-actions">
                      <button
                        v-if="page === 'products'"
                        class="text-link"
                        @click="open('product', row)"
                      >
                        编辑</button
                      ><template v-if="page === 'orders'"
                        ><button class="text-link" @click="viewOrder(row)">
                          详情</button
                        ><button
                          v-if="row.state === 'PAID' && canOperate"
                          class="text-link"
                          @click="open('ship', row)"
                        >
                          发货
                        </button></template
                      ><template v-if="page === 'refunds'"
                        ><button class="text-link" @click="viewOrder(row)">
                          原订单</button
                        ><button
                          v-if="row.state === 'REQUESTED' && canFinance"
                          class="text-link"
                          @click="open('reviewRefund', row)"
                        >
                          审核
                        </button></template
                      ><button
                        v-if="page === 'members' && isAdmin"
                        class="text-link"
                        @click="open('block', row)"
                      >
                        {{ row.blocked ? "恢复账号" : "停用账号" }}</button
                      ><template v-if="page === 'withdrawals'"
                        ><button
                          v-if="row.state === 'REVIEWING'"
                          class="text-link"
                          @click="open('reviewWithdrawal', row)"
                        >
                          财务复核</button
                        ><button
                          v-if="row.state === 'APPROVED' && isAdmin"
                          class="text-link"
                          @click="open('execute', row)"
                        >
                          执行出款</button
                        ><button
                          v-if="
                            ['SUBMITTED', 'UNKNOWN', 'CONFIRMING'].includes(
                              row.state,
                            )
                          "
                          class="text-link"
                          @click="
                            state.config.devMode
                              ? open('devTransfer', row)
                              : save(
                                  '/admin/withdrawals/' + row.id + '/query',
                                  {},
                                )
                          "
                        >
                          {{ state.config.devMode ? "测试确认" : "渠道查单" }}
                        </button></template
                      ><template v-if="page === 'rules'"
                        ><button
                          class="text-link"
                          @click="open('ruleDetail', row)"
                        >
                          查看</button
                        ><button
                          v-if="row.state === 'DRAFT' && isAdmin"
                          class="text-link"
                          @click="open('approveRule', row)"
                        >
                          审批
                        </button></template
                      ><template v-if="page === 'settlements'"
                        ><button
                          class="text-link"
                          @click="open('awardDetail', row)"
                        >
                          候选明细</button
                        ><button
                          v-if="row.state === 'DRAFT' && canFinance"
                          class="text-link"
                          @click="open('approveMonthly', row)"
                        >
                          复核入账
                        </button></template
                      ><button
                        v-if="
                          page === 'reconciliation' &&
                          tab === 'issues' &&
                          row.state === 'OPEN'
                        "
                        class="text-link"
                        @click="open('issue', row)"
                      >
                        处理工单</button
                      ><template v-if="page === 'accounts'"
                        ><button
                          class="text-link"
                          @click="open('accountActive', row)"
                        >
                          {{ row.active ? "停用" : "启用" }}</button
                        ><button
                          class="text-link"
                          @click="open('resetPassword', row)"
                        >
                          重置密码
                        </button></template
                      ><button
                        v-if="page === 'audit'"
                        class="text-link"
                        @click="open('auditDetail', row)"
                      >
                        详情
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div
                v-if="loaded && !filtered.length"
                class="empty-state compact"
              >
                <Icon :name="def.icon" :size="32" />
                <h3>暂无{{ query ? "匹配" : "" }}记录</h3>
                <p>真实业务发生后，记录将在这里呈现。</p>
              </div>
            </div>
            <div class="table-footer">
              <span
                >当前加载 {{ data.length }} 条，匹配
                {{ filtered.length }} 条</span
              >
              <div>
                <button
                  class="icon-button"
                  :disabled="current <= 1"
                  aria-label="上一页"
                  @click="current--"
                >
                  <Icon name="ArrowLeft" :size="16" /></button
                ><span>{{ current }} / {{ pages }}</span
                ><button
                  class="icon-button"
                  :disabled="current >= pages"
                  aria-label="下一页"
                  @click="current++"
                >
                  <Icon name="ArrowRight" :size="16" />
                </button>
              </div>
            </div>
          </div>
          <p v-if="page === 'reconciliation'" class="caption left">
            开发环境仅核对本地账本；生产批次逐笔查询已成功的支付、退款与出款并核对金额。渠道无法确认会计入差异；历史金额与手续费还需结合渠道账单复核。
          </p></template
        >
      </main>
      <footer class="admin-footer">
        SIMPLY SEEN BUSINESS STUDIO <span>让每一份经营，有迹可循。</span>
      </footer>
    </div>
    <Teleport to="body"
      ><div v-if="modal" class="modal-backdrop" @click.self="modal = ''">
        <section
          class="modal admin-modal"
          :class="{
            wide: [
              'product',
              'order',
              'rule',
              'ruleDetail',
              'awardDetail',
            ].includes(modal),
          }"
          role="dialog"
          aria-modal="true"
        >
          <button
            class="modal-close icon-button"
            aria-label="关闭弹窗"
            @click="modal = ''"
          >
            <Icon name="X" />
          </button>
          <template v-if="modal === 'product'"
            ><h2>{{ form.id ? "编辑商品" : "新增好物" }}</h2>
            <form @submit.prevent="save('/admin/products')">
              <div class="form-grid">
                <label class="input-label"
                  >商品名称<input
                    v-model="form.name"
                    required
                    maxlength="120" /></label
                ><label class="input-label"
                  >商品分类<select v-model="form.category_id">
                    <option v-for="c in categories" :key="c.id" :value="c.id">
                      {{ c.name }}
                    </option>
                  </select></label
                ><label class="input-label"
                  >副标题<input
                    v-model="form.subtitle"
                    maxlength="240" /></label
                ><label class="input-label"
                  >商品状态<select v-model="form.status">
                    <option value="ON_SALE">销售中</option>
                    <option value="OFF_SALE">已下架</option>
                  </select></label
                ><label class="input-label"
                  >商品图片（HTTPS URL或本地素材）<input
                    v-model="form.image"
                    required /></label
                ><label class="input-label"
                  >背景颜色<input v-model="form.color" type="color" /></label
                ><label class="input-label"
                  >积分抵扣上限（%）<input
                    v-model.number="form.points_percent"
                    type="number"
                    min="0"
                    max="100"
                    required /></label
                ><label class="input-label"
                  >佣金总比例上限（%）<input
                    v-model.number="form.commission_percent"
                    type="number"
                    min="0"
                    max="10"
                    required
                /></label>
              </div>
              <label class="input-label"
                >商品详情<textarea
                  v-model="form.description"
                  rows="5"
                  required
                ></textarea></label
              ><label class="checkbox-label"
                ><input v-model="form.featured" type="checkbox" />
                首页精选</label
              >
              <h3>规格与库存</h3>
              <p class="caption left">
                金额单位为分。修改总库存不可低于正在预占的数量。
              </p>
              <div v-for="(s, i) in form.skus" :key="i" class="sku-form">
                <label>规格名称<input v-model="s.name" required /></label
                ><label>SKU编码<input v-model="s.code" required /></label
                ><label
                  >售价（分）<input
                    v-model.number="s.price"
                    type="number"
                    min="1"
                    required /></label
                ><label
                  >成本（分）<input
                    v-model.number="s.cost"
                    type="number"
                    min="0"
                    required /></label
                ><label
                  >总库存<input
                    v-model.number="s.stock"
                    type="number"
                    min="0"
                    required
                /></label>
              </div>
              <button
                type="button"
                class="text-link"
                @click="
                  form.skus.push({
                    name: '',
                    code: '',
                    price: 10000,
                    cost: 0,
                    stock: 0,
                  })
                "
              >
                <Icon name="Plus" :size="15" /> 增加规格
              </button>
              <div class="modal-footer">
                <button class="button dark" :disabled="busy">保存商品</button>
              </div>
            </form></template
          >
          <template v-if="modal === 'category'"
            ><h2>商品分类</h2>
            <div v-for="c in categories" :key="c.id" class="history-row">
              <span>{{ c.name }}</span
              ><button class="text-link" @click="form = { ...c }">编辑</button>
            </div>
            <form @submit.prevent="save('/admin/categories')">
              <label class="input-label"
                >{{ form.id ? "修改分类" : "新增分类"
                }}<input v-model="form.name" required maxlength="60" /></label
              ><label class="input-label"
                >排序<input
                  v-model.number="form.sort_no"
                  type="number" /></label
              ><button class="button dark" :disabled="busy">保存分类</button
              ><button
                type="button"
                class="button"
                @click="form = { name: '', sort_no: 0 }"
              >
                新建分类
              </button>
            </form></template
          >
          <template v-if="modal === 'ship'"
            ><h2>妥帖送达</h2>
            <p class="muted">订单 {{ selected.id }}</p>
            <form
              @submit.prevent="save('/admin/orders/' + selected.id + '/ship')"
            >
              <label class="input-label"
                >物流公司<input
                  v-model="form.carrier"
                  required
                  maxlength="80" /></label
              ><label class="input-label"
                >运单号<input
                  v-model="form.trackingNo"
                  required
                  pattern="[A-Za-z0-9-]{5,80}" /></label
              ><button class="button dark full" :disabled="busy">
                确认发货
              </button>
            </form></template
          >
          <template
            v-if="modal === 'reviewRefund' || modal === 'reviewWithdrawal'"
            ><h2>
              {{ modal === "reviewRefund" ? "审核退款申请" : "财务复核提现" }}
            </h2>
            <p>
              {{ selected.id }} · ¥{{
                money(selected.amount || selected.gross)
              }}
            </p>
            <form
              @submit.prevent="
                save(
                  '/admin/' +
                    (modal === 'reviewRefund' ? 'refunds' : 'withdrawals') +
                    '/' +
                    selected.id +
                    '/review',
                )
              "
            >
              <label class="input-label"
                >审核结果<select v-model="form.approve">
                  <option :value="true">审核通过</option>
                  <option :value="false">驳回申请</option>
                </select></label
              ><label
                v-if="modal === 'reviewWithdrawal' && form.approve"
                class="input-label"
                >经确认的实际税额（分）<input
                  v-model.number="form.taxFen"
                  type="number"
                  min="0"
                  :max="selected.gross - 1"
                  required /></label
              ><label class="input-label"
                >审核依据 / 驳回原因<textarea
                  v-model="form.reason"
                  :required="modal === 'reviewWithdrawal' || !form.approve"
                  maxlength="240"
                ></textarea>
              </label>
              <p v-if="modal === 'reviewWithdrawal'" class="notice">
                审核通过仅进入待执行；需另一名管理员授权后，才可提交渠道。
              </p>
              <button class="button dark full" :disabled="busy">
                提交审核
              </button>
            </form></template
          >
          <template v-if="modal === 'account'"
            ><h2>新增管理账号</h2>
            <form @submit.prevent="save('/admin/accounts')">
              <label class="input-label"
                >账号<input
                  v-model="form.username"
                  required
                  pattern="[a-zA-Z0-9_-]{3,40}"
                  autocomplete="off" /></label
              ><label class="input-label"
                >初始密码（至少12位）<input
                  v-model="form.password"
                  type="password"
                  minlength="12"
                  required
                  autocomplete="new-password" /></label
              ><label class="input-label"
                >角色<select v-model="form.role">
                  <option value="OPERATOR">运营：商品、履约、规则草稿</option>
                  <option value="FINANCE">财务：审核、账本、对账</option>
                  <option value="ADMIN">
                    管理员：权限、规则审批、出款执行
                  </option>
                </select></label
              ><button class="button dark full" :disabled="busy">
                创建账号
              </button>
            </form></template
          >
          <template v-if="modal === 'accountActive'"
            ><h2>{{ selected.active ? "停用" : "启用" }}管理账号？</h2>
            <p>停用会撤销该账号的所有登录会话，已有操作审计会保留。</p>
            <button
              class="button dark full"
              :disabled="busy"
              @click="
                save('/admin/accounts/' + selected.id + '/active', {
                  active: !selected.active,
                })
              "
            >
              确认操作
            </button></template
          ><template v-if="modal === 'resetPassword'"
            ><h2>重置其他账号密码</h2>
            <form
              @submit.prevent="
                save('/admin/accounts/' + selected.id + '/reset-password')
              "
            >
              <label class="input-label"
                >新密码（至少12位）<input
                  v-model="form.password"
                  type="password"
                  minlength="12"
                  required
                  autocomplete="new-password"
              /></label>
              <p class="notice">
                保存后撤销该账号的所有会话，请通过安全渠道交付新密码。
              </p>
              <button class="button dark full" :disabled="busy">
                重置密码
              </button>
            </form></template
          ><template v-if="modal === 'password'"
            ><h2>更新登录密码</h2>
            <form
              @submit.prevent="
                act(async () => {
                  await api(
                    '/admin/password',
                    { method: 'POST', body: form },
                    true,
                  );
                  await logout(true);
                  modal = '';
                  message('密码已更新，请重新登录');
                })
              "
            >
              <label class="input-label"
                >原密码<input
                  v-model="form.currentPassword"
                  type="password"
                  required
                  autocomplete="current-password" /></label
              ><label class="input-label"
                >新密码（至少12位）<input
                  v-model="form.password"
                  type="password"
                  minlength="12"
                  required
                  autocomplete="new-password" /></label
              ><button class="button dark full" :disabled="busy">
                更新密码并退出
              </button>
            </form></template
          >
          <template v-if="modal === 'rule'"
            ><h2>创建规则草稿</h2>
            <p class="notice">
              保存后由另一名管理员审批生效，历史订单保留原版本。
            </p>
            <form @submit.prevent="save('/admin/rules')">
              <div class="form-grid">
                <label
                  v-for="field in ruleFields"
                  :key="field[0]"
                  class="input-label"
                  >{{ field[1]
                  }}<input
                    v-model.number="form[field[0]]"
                    required
                    type="number"
                    min="0"
                /></label>
              </div>
              <label class="checkbox-label"
                ><input v-model="form.distributionEnabled" type="checkbox" />
                启用真实成交两级分销（须环境允许）</label
              ><button class="button dark" :disabled="busy">
                保存待复核草稿
              </button>
            </form></template
          >
          <template v-if="modal === 'ruleDetail'"
            ><h2>规则版本 {{ selected.id }}</h2>
            <div
              v-for="field in ruleFields"
              :key="field[0]"
              class="history-row"
            >
              <span>{{ field[1] }}</span
              ><strong>{{ JSON.parse(selected.config)[field[0]] }}</strong>
            </div>
            <div class="history-row">
              <span>分销计佣</span
              ><strong>{{
                JSON.parse(selected.config).distributionEnabled
                  ? "启用"
                  : "关闭"
              }}</strong>
            </div></template
          >
          <template v-if="modal === 'settlement'"
            ><h2>生成月度优惠草稿</h2>
            <p class="notice">
              仅处理已结束月份。前10%按合格用户人数向上取整；按有效净现金排名，在已批预算内依次分配，每人不超过500元。需另一名财务复核。
            </p>
            <form @submit.prevent="save('/admin/settlements')">
              <label class="input-label"
                >结算月份<input
                  v-model="form.period"
                  type="month"
                  required /></label
              ><button class="button dark full" :disabled="busy">
                生成候选草稿
              </button>
            </form></template
          >
          <template v-if="modal === 'awardDetail'"
            ><h2>{{ selected.period }} 候选明细</h2>
            <p>
              预算 ¥{{ money(selected.budget) }} · {{ label(selected.state) }}
            </p>
            <div v-for="a in selected.awards" :key="a.id" class="history-row">
              <span
                >第 {{ a.rank_no }} 名 · 会员 {{ a.member_id
                }}<small>有效业绩 ¥{{ money(a.sales) }}</small></span
              ><strong>¥{{ money(a.amount) }}</strong>
            </div>
            <p v-if="!selected.awards.length" class="muted">
              本月无合格候选或可用预算。
            </p></template
          >
          <template v-if="modal === 'order'"
            ><h2>订单详情</h2>
            <p class="mono">
              {{ selected.id }}
              <span class="badge">{{ label(selected.state) }}</span>
            </p>
            <div class="panel">
              <h3>收货信息</h3>
              <p>{{ selected.address.name }} · {{ selected.address.phone }}</p>
              <p>{{ selected.address.region }} {{ selected.address.detail }}</p>
              <p v-if="selected.carrier">
                {{ selected.carrier }} · {{ selected.tracking_no }}
              </p>
            </div>
            <div v-for="i in selected.items" :key="i.id" class="checkout-item">
              <img :src="i.image" :alt="i.name" />
              <div>
                <strong>{{ i.name }}</strong>
                <p>数量 {{ i.quantity }} · 已退 {{ i.refunded_quantity }}</p>
              </div>
              <span>¥{{ money(i.cash_amount) }}</span>
            </div>
            <div class="history-row">
              <span>现金实付 / 已退</span
              ><strong
                >¥{{ money(selected.cash_due) }} / ¥{{
                  money(selected.cash_refunded)
                }}</strong
              >
            </div>
            <div class="history-row">
              <span>积分使用 / 已退</span
              ><strong
                >{{ selected.points_used }} /
                {{ selected.points_refunded }}</strong
              >
            </div>
            <div v-for="r in selected.refunds" :key="r.id" class="history-row">
              <span>{{ r.id }} · {{ label(r.state) }}</span
              ><span>¥{{ money(r.amount) }}</span>
            </div></template
          >
          <template v-if="modal === 'issue'"
            ><h2>处理差异工单</h2>
            <p>{{ selected.detail }}</p>
            <form
              @submit.prevent="
                save('/admin/issues/' + selected.id + '/resolve')
              "
            >
              <label class="input-label"
                >实际处理依据<textarea
                  v-model="form.reason"
                  minlength="4"
                  required
                  placeholder="记录核对依据与处理结果，不会直接修改账本余额"
                ></textarea></label
              ><button class="button dark full" :disabled="busy">
                记录处理结果
              </button>
            </form></template
          >
          <template v-if="modal === 'auditDetail'"
            ><h2>操作审计详情</h2>
            <p>
              操作人 {{ selected.actor_id }} · {{ date(selected.created_at) }}
            </p>
            <p>{{ selected.action }} · {{ selected.resource }}</p>
            <pre>{{
              JSON.stringify(JSON.parse(selected.detail), null, 2)
            }}</pre>
          </template>
          <template
            v-if="
              [
                'execute',
                'devTransfer',
                'approveRule',
                'approveMonthly',
                'block',
              ].includes(modal)
            "
            ><h2>
              {{
                (
                  {
                    execute: "确认执行出款？",
                    devTransfer: "确认开发测试出款？",
                    approveRule: "批准此规则生效？",
                    approveMonthly: "确认月度权益入账？",
                    block: selected.blocked
                      ? "恢复会员账号？"
                      : "停用会员账号？",
                  } as any
                )[modal]
              }}
            </h2>
            <p>
              {{
                (
                  {
                    execute:
                      "系统将校验审核人与执行人不同，并以原出款单号提交微信。结果未知时只查单，不重复出款。",
                    devTransfer:
                      "此操作只在开发环境中更新测试状态，不产生真实资金转账。",
                    approveRule:
                      "经办人与审批人必须不同；新订单采用新版本，历史账务保持原规则。",
                    approveMonthly:
                      "请先核对候选名单及预算。与经办人不同的财务人员才能批准入账。",
                    block:
                      "操作将保留资产、订单与账本；停用后会撤销会员登录会话。",
                  } as any
                )[modal]
              }}
            </p>
            <button
              class="button dark full"
              :disabled="busy"
              @click="
                save(
                  (
                    {
                      execute: '/admin/withdrawals/' + selected.id + '/execute',
                      devTransfer:
                        '/admin/dev/withdrawals/' + selected.id + '/confirm',
                      approveRule: '/admin/rules/' + selected.id + '/approve',
                      approveMonthly:
                        '/admin/settlements/' + selected.id + '/approve',
                      block: '/admin/members/' + selected.id + '/block',
                    } as any
                  )[modal],
                  modal === 'block' ? { blocked: !selected.blocked } : {},
                )
              "
            >
              确认操作
            </button></template
          >
        </section>
      </div></Teleport
    >
  </div>
</template>
