<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import { useConfigStore } from "@/store/config";
import Icon from "@/components/common/Icon.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import SettlementEditor from "@/components/admin/SettlementEditor.vue";
import AwardDetailDialog from "@/components/admin/AwardDetailDialog.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore(), config = useConfigStore();
const { data, loading, error, load } = useAdminResource<any[]>(() =>
  adminApi.getSettlements(),
  "settlements",
);
const rows = computed(() => data.value || []);
const role = computed(() =>
    String(auth.adminProfile?.role || auth.admin?.profile?.role || ""),
  ),
  canOperate = computed(() => ["ADMIN", "OPERATOR"].includes(role.value)),
  canFinance = computed(() => ["ADMIN", "FINANCE"].includes(role.value));
const editor = ref(false),
  detail = ref<any>(null),
  approval = ref<any>(null);
function saved() {
  editor.value = false;
  approval.value = null;
  load();
}
</script>
<template>
  <AdminPageHeader page="settlements" :busy="loading" @refresh="load"
    ><button v-if="canOperate" :disabled="!config.config.devMode && !config.config.monthlyEnabled" class="button dark small" @click="editor = true">
      <Icon name="Plus" :size="16" /> 生成月结草稿
    </button></AdminPageHeader
  ><p v-if="!config.config.devMode && !config.config.monthlyEnabled" class="notice">月度优惠活动当前尚未开放，历史结算仍可查看。</p><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.settlements.columns!"
    title="月度优惠结算"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="Gift"
    ><template #actions="{ row }"
      ><button class="text-link" @click="detail = row">候选明细</button
      ><button
        v-if="row.state === 'DRAFT' && canFinance"
        :disabled="String(row.creator) === String(auth.adminProfile?.id)"
        :title="String(row.creator) === String(auth.adminProfile?.id) ? '需另一名财务复核' : '复核入账'"
        class="text-link"
        @click="approval = row"
      >
        复核入账
      </button></template
    ></AdminDataTable
  ><SettlementEditor
    v-if="editor"
    @close="editor = false"
    @saved="saved"
  /><AwardDetailDialog
    v-if="detail"
    :settlement="detail"
    @close="detail = null"
  /><ConfirmActionDialog
    v-if="approval"
    title="确认月度权益入账？"
    description="请先核对候选名单及预算。与经办人不同的财务人员才能批准入账。"
    :operation="() => adminApi.approveSettlement(approval.id)"
    @close="approval = null"
    @saved="saved"
  />
</template>
