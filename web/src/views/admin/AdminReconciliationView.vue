<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "@/api/admin";
import type { AdminColumn } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import Icon from "@/components/common/Icon.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import IssueResolutionDialog from "@/components/admin/IssueResolutionDialog.vue";
const tab = ref<"batches" | "issues" | "outbox">("batches");
const kind = computed(() => tab.value === "batches" ? "reconcile-batches" as const : tab.value);
const { data, loading, error, busy, load, run } = useAdminResource<any[]>(() => Promise.resolve([]), kind);
const selected = ref<any>(null);
const tabs = [
  ["batches", "对账批次"],
  ["issues", "差异工单"],
  ["outbox", "异步任务"],
] as const;
const fieldSets: Record<string, AdminColumn[]> = {
  batches: [
    ["period_day", "日期"],
    ["orders_total", "累计实付"],
    ["refunds_total", "累计退款"],
    ["transfers_total", "累计出款"],
    ["diff_count", "本批差异"],
    ["created_at", "时间"],
  ],
  issues: [
    ["kind", "异常类型"],
    ["resource", "对象"],
    ["detail", "说明"],
    ["state", "状态"],
    ["created_at", "时间"],
  ],
  outbox: [
    ["kind", "任务类型"],
    ["aggregate_id", "业务单号"],
    ["state", "状态"],
    ["attempts", "尝试次数"],
    ["last_error", "最近错误"],
  ],
};
const rows = computed(() => data.value || []),
  columns = computed(() => fieldSets[tab.value]);
async function reconcile() {
  await run(async () => {
    await adminApi.reconcile();
  }, "对账批次已生成，请检查差异结果");
}
function saved() {
  selected.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader page="reconciliation" :busy="loading || busy" @refresh="load"
    ><button class="button dark small" :disabled="busy" @click="reconcile">
      <Icon name="Shield" :size="16" /> 执行对账
    </button></AdminPageHeader
  >
  <div class="category-pills reconcile-tabs">
    <button
      v-for="item in tabs"
      :key="item[0]"
      :class="{ active: tab === item[0] }"
      @click="tab = item[0]"
    >
      {{ item[1] }}
    </button>
  </div>
  <AdminDataTable
    :key="tab"
    :rows="rows"
    :columns="columns"
    title="对账与异常"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="Shield"
    ><template #actions="{ row }"
      ><button
        v-if="tab === 'issues' && row.state === 'OPEN'"
        class="text-link"
        @click="selected = row"
      >
        处理工单
      </button></template
    ></AdminDataTable
  >
  <p class="caption left">
    开发环境仅核对本地账本；生产批次逐笔查询已成功的支付、退款与出款并核对金额。渠道无法确认会计入差异；历史金额与手续费还需结合渠道账单复核。
  </p>
  <IssueResolutionDialog
    v-if="selected"
    :issue="selected"
    @close="selected = null"
    @saved="saved"
  />
</template>
