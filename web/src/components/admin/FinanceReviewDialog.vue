<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import AdminModal from "./AdminModal.vue";
import { money } from "@/utils/format";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const props = defineProps<{ record: any; kind: "refund" | "withdrawal" }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const form = ref({ approve: true, taxFen: 0, reason: "" });
const { busy, execute } = useAdminMutation();
async function save() {
  const success = await execute(() =>
    props.kind === "refund"
      ? adminApi.reviewRefund(props.record.id, {
          approve: form.value.approve,
          reason: form.value.reason,
        })
      : adminApi.reviewWithdrawal(props.record.id, form.value),
  );
  if (success) emit("saved");
}
</script>
<template>
  <AdminModal
    :title="kind === 'refund' ? '审核退款申请' : '财务复核提现'"
    @close="emit('close')"
    ><p>{{ record.id }} · ¥{{ money(record.amount || record.gross) }}</p>
    <form @submit.prevent="save">
      <label class="input-label"
        >审核结果<select v-model="form.approve">
          <option :value="true">审核通过</option>
          <option :value="false">驳回申请</option>
        </select></label
      ><label v-if="kind === 'withdrawal' && form.approve" class="input-label"
        >经确认的实际税额（分）<input
          v-model.number="form.taxFen"
          type="number"
          min="0"
          :max="record.gross - 1"
          required /></label
      ><label class="input-label"
        >审核依据 / 驳回原因<textarea
          v-model="form.reason"
          :required="kind === 'withdrawal' || !form.approve"
          maxlength="240"
        ></textarea>
      </label>
      <p v-if="kind === 'withdrawal'" class="notice">
        审核通过仅进入待执行；需另一名管理员授权后，才可提交渠道。
      </p>
      <button class="button dark full" :disabled="busy">提交审核</button>
    </form></AdminModal
  >
</template>
