<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
defineProps<{ title?: string; wide?: boolean }>();
defineEmits<{ close: [] }>();
</script>
<template>
  <Teleport to="body"
    ><div class="modal-backdrop" @click.self="$emit('close')">
      <section
        class="modal admin-modal"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
      >
        <button
          class="modal-close icon-button"
          aria-label="关闭弹窗"
          @click="$emit('close')"
        >
          <Icon name="X" />
        </button>
        <h2 v-if="title">{{ title }}</h2>
        <slot />
      </section></div
  ></Teleport>
</template>
