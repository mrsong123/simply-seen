<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "@/api/admin";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import AuditDetailDialog from "@/components/admin/AuditDetailDialog.vue";
const { data, loading, error, load } = useAdminResource<any[]>(() =>
  adminApi.getAudit(),
  "audit",
);
const rows = computed(() => data.value || []);
const selected = ref<any>(null);
</script>
<template>
  <AdminPageHeader
    page="audit"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.audit.columns!"
    title="操作审计"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="FileText"
    ><template #actions="{ row }"
      ><button class="text-link" @click="selected = row">详情</button></template
    ></AdminDataTable
  ><AuditDetailDialog
    v-if="selected"
    :record="selected"
    @close="selected = null"
  />
</template>
