<script setup lang="ts">
import { computed } from "vue";
import Icon from "@/components/common/Icon.vue";
import { adminDefinitions } from "@/config/admin";
const props = defineProps<{ page: string; busy?: boolean }>();
const definition = computed(() => adminDefinitions[props.page]);
defineEmits<{ refresh: [] }>();
</script>
<template>
  <div class="admin-title">
    <div>
      <div class="eyebrow">SIMPLY SEEN / BUSINESS</div>
      <h1>{{ definition.title }}</h1>
      <p>{{ definition.subtitle }}</p>
    </div>
    <div class="button-row">
      <button class="button small" :disabled="busy" @click="$emit('refresh')">
        <Icon name="RefreshCw" :size="16" /> 刷新</button
      ><slot />
    </div>
  </div>
</template>
