<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore();
const { data, loading, error, load } = useAdminResource<any[]>(() =>
  adminApi.getMembers(),
  "members",
);
const rows = computed(() => data.value || []),
  isAdmin = computed(
    () =>
      String(auth.adminProfile?.role || auth.admin?.profile?.role || "") ===
      "ADMIN",
  );
const selected = ref<any>(null);
function saved() {
  selected.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader
    page="members"
    :busy="loading"
    @refresh="load"
  /><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.members.columns!"
    title="会员管理"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="Users"
    ><template #actions="{ row }"
      ><button v-if="isAdmin" class="text-link" @click="selected = row">
        {{ row.blocked ? "恢复账号" : "停用账号" }}
      </button></template
    ></AdminDataTable
  ><ConfirmActionDialog
    v-if="selected"
    :title="selected.blocked ? '恢复会员账号？' : '停用会员账号？'"
    description="操作将保留资产、订单与账本；停用后会撤销会员登录会话。"
    :operation="
      () => adminApi.blockMember(selected.id, { blocked: !selected.blocked })
    "
    @close="selected = null"
    @saved="saved"
  />
</template>
