<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import AdminModal from "./AdminModal.vue";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const props = defineProps<{ order: any }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const form = ref({ carrier: "", trackingNo: "" });
const { busy, execute } = useAdminMutation();
async function save() {
  if (await execute(() => adminApi.shipOrder(props.order.id, form.value)))
    emit("saved");
}
</script>
<template>
  <AdminModal title="妥帖送达" @close="emit('close')"
    ><p class="muted">订单 {{ order.id }}</p>
    <form @submit.prevent="save">
      <label class="input-label"
        >物流公司<input v-model="form.carrier" required maxlength="80" /></label
      ><label class="input-label"
        >运单号<input
          v-model="form.trackingNo"
          required
          pattern="[A-Za-z0-9-]{5,80}" /></label
      ><button class="button dark full" :disabled="busy">确认发货</button>
    </form></AdminModal
  >
</template>
