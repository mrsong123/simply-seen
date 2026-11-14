<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import Icon from "@/components/common/Icon.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import AccountEditor from "@/components/admin/AccountEditor.vue";
import PasswordDialog from "@/components/admin/PasswordDialog.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore();
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getAccounts(),
);
const rows = computed(() => data.value || []);
const editor = ref(false),
  selected = ref<any>(null),
  resetAccount = ref<any>(null);
const ownId = computed(() => auth.adminProfile?.id || auth.admin?.profile?.id);
function saved() {
  editor.value = false;
  selected.value = null;
  resetAccount.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader page="accounts" :busy="loading" @refresh="load"
    ><button class="button dark small" @click="editor = true">
      <Icon name="Plus" :size="16" /> 新增账号
    </button></AdminPageHeader
  ><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.accounts.columns!"
    title="账号与权限"
    :loading="loading"
    icon="Users"
    ><template #actions="{ row }"
      ><template v-if="String(row.id) !== String(ownId)"
        ><button class="text-link" @click="selected = row">
          {{ row.active ? "停用" : "启用" }}</button
        ><button class="text-link" @click="resetAccount = row">
          重置密码
        </button></template
      ><span v-else class="muted">当前账号</span></template
    ></AdminDataTable
  ><AccountEditor
    v-if="editor"
    @close="editor = false"
    @saved="saved"
  /><PasswordDialog
    v-if="resetAccount"
    :account-id="resetAccount.id"
    @close="resetAccount = null"
    @saved="saved"
  /><ConfirmActionDialog
    v-if="selected"
    :title="(selected.active ? '停用' : '启用') + '管理账号？'"
    description="停用会撤销该账号的所有登录会话，已有操作审计会保留。"
    :operation="
      () => adminApi.setAccountActive(selected.id, { active: !selected.active })
    "
    @close="selected = null"
    @saved="saved"
  />
</template>
