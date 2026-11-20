<script setup lang="ts">
withDefaults(
  defineProps<{ loading?: boolean; failed?: boolean; retryable?: boolean }>(),
  { loading: false, failed: false, retryable: true },
);
defineEmits<{ (event: "retry"): void }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view v-if="failed" class="box">
    <text class="sub">暂时无法读取当前数据，请检查网络后重试。</text>
    <button
      v-if="retryable"
      class="secondary"
      :disabled="loading"
      @tap="$emit('retry')"
    >
      重新读取
    </button>
  </view>
  <view v-else-if="loading" class="empty">正在读取，请稍候。</view>
</template>
