<script setup lang="ts">
import Icon from "./Icon.vue";
withDefaults(defineProps<{ loading?: boolean; error?: string | boolean }>(), { loading: false, error: false });
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="loading" class="empty-state compact" role="status">
    <Icon name="Clock" :size="30" /><h3>正在加载，请稍候</h3>
  </div>
  <div v-else-if="error" class="empty-state compact" role="alert">
    <Icon name="RefreshCw" :size="30" /><h3>暂时无法加载数据</h3>
    <p>{{ typeof error === 'string' ? error : '请检查网络连接，或稍后重试。' }}</p>
    <button class="button outline" @click="$emit('retry')">重新加载</button>
  </div>
</template>
