<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import Icon from "@/components/common/Icon.vue";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import SettlementEditor from "@/components/admin/SettlementEditor.vue";
import AwardDetailDialog from "@/components/admin/AwardDetailDialog.vue";
import ConfirmActionDialog from "@/components/admin/ConfirmActionDialog.vue";
const auth = useAuthStore();
const { data, loading, load } = useAdminResource<any[]>(() =>
  adminApi.getSettlements(),
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
    ><button v-if="canOperate" class="button dark small" @click="editor = true">
      <Icon name="Plus" :size="16" /> 生成月结草稿
    </button></AdminPageHeader
  ><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.settlements.columns!"
    title="月度优惠结算"
    :loading="loading"
    icon="Gift"
    ><template #actions="{ row }"
      ><button class="text-link" @click="detail = row">候选明细</button
      ><button
        v-if="row.state === 'DRAFT' && canFinance"
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
