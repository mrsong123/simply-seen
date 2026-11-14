<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import FinanceReviewDialog from "@/components/admin/FinanceReviewDialog.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore(),
  settings = useConfigStore();
const { data, loading, busy, load, run } = useAdminResource<any[]>(() =>
  adminApi.getWithdrawals(),
);
const rows = computed(() => data.value || []),
  isAdmin = computed(
    () =>
      String(auth.adminProfile?.role || auth.admin?.profile?.role || "") ===
      "ADMIN",
  );
const review = ref<any>(null),
  selected = ref<any>(null),
  confirm = ref<"execute" | "devTransfer" | "">("");
function openConfirmation(row: any, type: "execute" | "devTransfer") {
  selected.value = row;
  confirm.value = type;
}
function saved() {
  review.value = null;
  confirm.value = "";
  selected.value = null;
  load();
}
async function query(row: any) {
  await run(async () => {
    await adminApi.queryWithdrawal(row.id);
  });
}
async function confirmOperation() {
  if (confirm.value === "execute")
    return adminApi.executeWithdrawal(selected.value.id);
  return adminApi.confirmDevWithdrawal(selected.value.id);
}
</script>
<template>
  <AdminPageHeader
    page="withdrawals"
    :busy="loading || busy"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.withdrawals.columns!"
    title="提现与出款"
    :loading="loading"
    icon="Wallet"
    ><template #actions="{ row }"
      ><button
        v-if="row.state === 'REVIEWING'"
        class="text-link"
        @click="review = row"
      >
        财务复核</button
      ><button
        v-if="row.state === 'APPROVED' && isAdmin"
        class="text-link"
        @click="openConfirmation(row, 'execute')"
      >
        执行出款</button
      ><button
        v-if="['SUBMITTED', 'UNKNOWN', 'CONFIRMING'].includes(row.state)"
        class="text-link"
        :disabled="busy"
        @click="
          settings.config.devMode
            ? openConfirmation(row, 'devTransfer')
            : query(row)
        "
      >
        {{ settings.config.devMode ? "测试确认" : "渠道查单" }}
      </button></template
    ></AdminDataTable
  ><FinanceReviewDialog
    v-if="review"
    :record="review"
    kind="withdrawal"
    @close="review = null"
    @saved="saved"
  /><ConfirmActionDialog
    v-if="confirm"
    :title="confirm === 'execute' ? '确认执行出款？' : '确认开发测试出款？'"
    :description="
      confirm === 'execute'
        ? '系统将校验审核人与执行人不同，并以原出款单号提交微信。结果未知时只查单，不重复出款。'
        : '此操作只在开发环境中更新测试状态，不产生真实资金转账。'
    "
    :operation="confirmOperation"
    @close="confirm = ''"
    @saved="saved"
  />
</template>
