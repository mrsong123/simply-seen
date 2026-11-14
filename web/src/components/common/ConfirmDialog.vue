<script setup lang="ts">
import BaseModal from "./BaseModal.vue";
defineProps<{
  modelValue: boolean;
  title: string;
  description?: string;
  busy?: boolean;
  confirmLabel?: string;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirm: [];
}>();
</script>
<template>
  <BaseModal
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    ><h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <button class="button dark full" :disabled="busy" @click="emit('confirm')">
      {{ confirmLabel || "确认操作" }}
    </button></BaseModal
  >
</template>
