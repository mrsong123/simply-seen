<script setup lang="ts">
import Icon from "./Icon.vue";
import type { PagedResource } from "@/composables/createPagedResource";
defineProps<{ records: PagedResource; label?: string }>();
</script>
<template>
  <div class="table-footer page-navigation">
    <span>共 {{ records.total.value }} 条记录，第 {{ records.page.value }} / {{ records.totalPages.value }} 页</span>
    <div>
      <button class="icon-button" :aria-label="(label || '') + '上一页'" :disabled="records.loading.value || records.page.value <= 1" @click="records.setPage(records.page.value - 1)"><Icon name="ArrowLeft" :size="16" /></button>
      <button class="icon-button" :aria-label="(label || '') + '下一页'" :disabled="records.loading.value || records.page.value >= records.totalPages.value" @click="records.setPage(records.page.value + 1)"><Icon name="ArrowRight" :size="16" /></button>
    </div>
  </div>
</template>
