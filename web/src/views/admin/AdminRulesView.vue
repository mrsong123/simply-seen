<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import Icon from "@/components/common/Icon.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import RuleEditor from "@/components/admin/RuleEditor.vue";
import RuleDetailDialog from "@/components/admin/RuleDetailDialog.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore();
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getRules(),
);
const rows = computed(() => data.value || []),
  isAdmin = computed(
    () =>
      String(auth.adminProfile?.role || auth.admin?.profile?.role || "") ===
      "ADMIN",
  ),
  canOperate = computed(() =>
    ["ADMIN", "OPERATOR"].includes(
      String(auth.adminProfile?.role || auth.admin?.profile?.role || ""),
    ),
  );
const editor = ref(false),
  detail = ref<any>(null),
  approval = ref<any>(null);
const activeConfig = computed(() => {
  const active = rows.value.find((row) => row.state === "ACTIVE");
  return active ? JSON.parse(active.config) : undefined;
});
function saved() {
  editor.value = false;
  approval.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader page="rules" :busy="loading" @refresh="load"
    ><button v-if="canOperate" class="button dark small" @click="editor = true">
      <Icon name="Plus" :size="16" /> 新建规则草稿
    </button></AdminPageHeader
  ><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.rules.columns!"
    title="活动与分销规则"
    :loading="loading"
    icon="SlidersHorizontal"
    ><template #actions="{ row }"
      ><button class="text-link" @click="detail = row">查看</button
      ><button
        v-if="row.state === 'DRAFT' && isAdmin"
        class="text-link"
        @click="approval = row"
      >
        审批
      </button></template
    ></AdminDataTable
  ><RuleEditor
    v-if="editor"
    :rule="activeConfig"
    @close="editor = false"
    @saved="saved"
  /><RuleDetailDialog
    v-if="detail"
    :rule="detail"
    @close="detail = null"
  /><ConfirmActionDialog
    v-if="approval"
    title="批准此规则生效？"
    description="经办人与审批人必须不同；新订单采用新版本，历史账务保持原规则。"
    :operation="() => adminApi.approveRule(approval.id)"
    @close="approval = null"
    @saved="saved"
  />
</template>
