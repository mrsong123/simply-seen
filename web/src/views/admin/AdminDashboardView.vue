<script setup lang="ts">
import { computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import { money } from "@/utils/format";
import Icon from "@/components/common/Icon.vue";
import ResourceState from "@/components/common/ResourceState.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
const auth = useAuthStore();
const { data, loading, error, load } = useAdminResource<any>(() =>
  adminApi.getSummary(),
);
const summary = computed(() => data.value || {});
const role = computed(() =>
  String(auth.adminProfile?.role || auth.admin?.profile?.role || ""),
);
const canOperate = computed(() => ["ADMIN", "OPERATOR"].includes(role.value));
const canFinance = computed(() => ["ADMIN", "FINANCE"].includes(role.value));
const trend = computed(() => {
  const days = Array.from({ length: 7 }, (_, index) => ({
    day: new Date(Date.now() - (6 - index) * 86400000).toLocaleDateString(
      "zh-CN",
      { month: "numeric", day: "numeric" },
    ),
    total: 0,
  }));
  for (const order of summary.value.trend || []) {
    const age = Math.floor((Date.now() - order.created_at) / 86400000);
    if (age >= 0 && age < 7 && !["CREATED", "CLOSED"].includes(order.state))
      days[6 - age].total += order.cash_due;
  }
  return days;
});
const maxTrend = computed(() =>
  Math.max(100, ...trend.value.map((day) => day.total)),
);
const metrics = computed(() => [
  ["有效成交金额", "¥" + money(summary.value.gmv), "Wallet"],
  ["累计订单", summary.value.orders || 0, "ShoppingBag"],
  ["会员总数", summary.value.members || 0, "Users"],
  ["待发货订单", summary.value.pendingOrders || 0, "Truck"],
]);
</script>
<template>
  <AdminPageHeader page="overview" :busy="loading" @refresh="load" />
  <ResourceState :loading="loading" :error="error" @retry="load" />
  <template v-if="!loading && !error"><div class="metrics">
    <div v-for="(metric, index) in metrics" :key="index" class="metric-card">
      <div>
        <span>{{ metric[0] }}</span
        ><Icon :name="String(metric[2])" :size="19" />
      </div>
      <strong>{{ metric[1] }}</strong
      ><small>{{ index === 0 ? "实付扣除成功退款" : "实时业务数据" }}</small>
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
        <div v-for="day in trend" :key="day.day" class="bar-column">
          <small>¥{{ money(day.total) }}</small>
          <div class="bar-track">
            <div
              :style="{
                height: Math.max(2, (day.total / maxTrend) * 100) + '%',
              }"
            ></div>
          </div>
          <span>{{ day.day }}</span>
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
    /></RouterLink>
  </div>
</template></template>
