<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
import AdminModal from "./AdminModal.vue";
const props = defineProps<{ issue: any }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const reason = ref("");
const { busy, execute } = useAdminMutation();
async function save() {
  if (
    await execute(() =>
      adminApi.resolveIssue(props.issue.id, { reason: reason.value }),
    )
  )
    emit("saved");
}
</script>
<template>
  <AdminModal title="处理差异工单" @close="emit('close')"
    ><p>{{ issue.detail }}</p>
    <form @submit.prevent="save">
      <label class="input-label"
        >实际处理依据<textarea
          v-model="reason"
          minlength="4"
          required
          placeholder="记录核对依据与处理结果，不会直接修改账本余额"
        ></textarea></label
      ><button class="button dark full" :disabled="busy">记录处理结果</button>
    </form></AdminModal
  >
</template>
