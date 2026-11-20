<script setup lang="ts">
defineProps<{
  page: number;
  total: number;
  totalPages: number;
  loading: boolean;
  failed: boolean;
}>();
defineEmits<{ more: []; retry: [] }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="record-pager">
    <text v-if="loading" class="caption">正在读取，请稍候</text>
    <template v-else-if="failed">
      <text class="sub">读取未完成，已加载的数据仍保留。</text>
      <button class="secondary" @tap="$emit('retry')">重新读取</button>
    </template>
    <template v-else-if="page">
      <text class="caption"
        >已加载第{{ page }} / {{ totalPages }}页 · 共{{ total }}条</text
      >
      <button v-if="page < totalPages" class="secondary" @tap="$emit('more')">
        加载更多
      </button>
      <text v-else-if="total" class="caption">全部记录已加载</text>
    </template>
    <button v-else class="secondary" @tap="$emit('retry')">读取记录</button>
  </view>
</template>
<style scoped>
.record-pager {
  padding: 24rpx 0;
  text-align: center;
}
.record-pager .caption {
  display: block;
}
</style>
