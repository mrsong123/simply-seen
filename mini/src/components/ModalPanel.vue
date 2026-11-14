<script setup lang="ts">
defineProps<{ open: boolean; title: string }>();
const emit = defineEmits<{ (event: "close"): void }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view v-if="open" class="mask" @tap.self="emit('close')"
    ><scroll-view scroll-y class="dialog"
      ><text class="close" @tap="emit('close')">×</text
      ><view class="title">{{ title }}</view
      ><slot /></scroll-view
  ></view>
</template>
