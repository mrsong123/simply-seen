<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="page-heading"
    ><text v-if="subtitle" class="eyebrow">{{ subtitle }}</text
    ><view class="title">{{ title }}</view
    ><slot
  /></view>
</template>
