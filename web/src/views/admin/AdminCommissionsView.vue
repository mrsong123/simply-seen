<script setup lang="ts">
import { computed } from "vue";
import { adminApi } from "@/api/admin";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getCommissions(),
);
const rows = computed(() => data.value || []);
</script>
<template>
  <AdminPageHeader
    page="commissions"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.commissions.columns!"
    title="成交佣金"
    :loading="loading"
    icon="Share2"
  />
</template>
