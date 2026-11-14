<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
import AdminModal from "./AdminModal.vue";
const emit = defineEmits<{ close: []; saved: [] }>();
const now = new Date();
now.setDate(1);
now.setMonth(now.getMonth() - 1);
const form = ref({ period: now.toLocaleDateString("sv-SE").slice(0, 7) });
const { busy, execute } = useAdminMutation();
async function save() {
  if (await execute(() => adminApi.createSettlement(form.value))) emit("saved");
}
</script>
<template>
  <AdminModal title="生成月度优惠草稿" @close="emit('close')"
    ><p class="notice">
      仅处理已结束月份。前10%按合格用户人数向上取整；按有效净现金排名，在已批预算内依次分配，每人不超过500元。需另一名财务复核。
    </p>
    <form @submit.prevent="save">
      <label class="input-label"
        >结算月份<input v-model="form.period" type="month" required /></label
      ><button class="button dark full" :disabled="busy">生成候选草稿</button>
    </form></AdminModal
  >
</template>
