<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
defineProps<{ modelValue: boolean; wide?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
</script>
<template>
  <Teleport to="body"
    ><div
      v-if="modelValue"
      class="modal-backdrop"
      @click.self="emit('update:modelValue', false)"
    >
      <section class="modal" :class="{ wide }" role="dialog" aria-modal="true">
        <button
          class="modal-close icon-button"
          aria-label="关闭弹窗"
          @click="emit('update:modelValue', false)"
        >
          <Icon name="X" /></button
        ><slot />
      </section></div
  ></Teleport>
</template>
