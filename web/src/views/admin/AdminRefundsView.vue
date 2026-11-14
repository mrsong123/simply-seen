<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import FinanceReviewDialog from "@/components/admin/FinanceReviewDialog.vue";
import OrderDetailDialog from "@/components/admin/OrderDetailDialog.vue";
const auth = useAuthStore();
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getRefunds(),
);
const rows = computed(() => data.value || []),
  canFinance = computed(() =>
    ["ADMIN", "FINANCE"].includes(
      String(auth.adminProfile?.role || auth.admin?.profile?.role || ""),
    ),
  );
const review = ref<any>(null),
  orderNo = ref("");
function reviewed() {
  review.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader
    page="refunds"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.refunds.columns!"
    title="售后退款"
    :loading="loading"
    icon="RefreshCw"
    ><template #actions="{ row }"
      ><button class="text-link" @click="orderNo = row.order_id">原订单</button
      ><button
        v-if="row.state === 'REQUESTED' && canFinance"
        class="text-link"
        @click="review = row"
      >
        审核
      </button></template
    ></AdminDataTable
  ><FinanceReviewDialog
    v-if="review"
    :record="review"
    kind="refund"
    @close="review = null"
    @saved="reviewed"
  /><OrderDetailDialog
    v-if="orderNo"
    :order-no="orderNo"
    @close="orderNo = ''"
  />
</template>
