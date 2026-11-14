<script setup lang="ts">
import { ref } from "vue";
import { onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import CatalogView from "../../components/CatalogView.vue";
const catalog = ref<InstanceType<typeof CatalogView>>();
onPullDownRefresh(async () => {
  await catalog.value?.reload();
  uni.stopPullDownRefresh();
});
</script>
<template>
  <PageShell tab="points"><CatalogView ref="catalog" points-only /></PageShell>
</template>
