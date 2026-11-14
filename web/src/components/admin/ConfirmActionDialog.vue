<script setup lang="ts">
import AdminModal from "./AdminModal.vue";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const props = defineProps<{
  title: string;
  description: string;
  operation: () => Promise<unknown>;
}>();
const emit = defineEmits<{ close: []; saved: [] }>();
const { busy, execute } = useAdminMutation();
async function confirm() {
  if (await execute(props.operation)) emit("saved");
}
</script>
<template>
  <AdminModal :title="title" @close="emit('close')"
    ><p>{{ description }}</p>
    <button class="button dark full" :disabled="busy" @click="confirm">
      确认操作
    </button></AdminModal
  >
</template>
