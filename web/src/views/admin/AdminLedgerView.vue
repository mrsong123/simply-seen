<script setup lang="ts">
import { computed } from "vue";
import { adminApi } from "@/api/admin";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
const { data, loading, error, load } = useAdminResource<any[]>(() =>
  adminApi.getLedger(),
  "ledger",
);
const rows = computed(() => data.value || []);
</script>
<template>
  <AdminPageHeader
    page="ledger"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.ledger.columns!"
    title="资产账本"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="Wallet"
  />
</template>
