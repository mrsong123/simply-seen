<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import ShipOrderDialog from "@/components/admin/ShipOrderDialog.vue";
import OrderDetailDialog from "@/components/admin/OrderDetailDialog.vue";
const auth = useAuthStore();
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getOrders(),
);
const rows = computed(() => data.value || []),
  canOperate = computed(() =>
    ["ADMIN", "OPERATOR"].includes(
      String(auth.adminProfile?.role || auth.admin?.profile?.role || ""),
    ),
  );
const ship = ref<any>(null),
  orderNo = ref("");
function shipped() {
  ship.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader
    page="orders"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.orders.columns!"
    title="订单与履约"
    :loading="loading"
    icon="ShoppingBag"
    ><template #actions="{ row }"
      ><button class="text-link" @click="orderNo = row.id">详情</button
      ><button
        v-if="row.state === 'PAID' && canOperate"
        class="text-link"
        @click="ship = row"
      >
        发货
      </button></template
    ></AdminDataTable
  ><ShipOrderDialog
    v-if="ship"
    :order="ship"
    @close="ship = null"
    @saved="shipped"
  /><OrderDetailDialog
    v-if="orderNo"
    :order-no="orderNo"
    @close="orderNo = ''"
  />
</template>
