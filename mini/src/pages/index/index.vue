<script setup lang="ts">
import { ref } from "vue";
import {
  onLoad,
  onPullDownRefresh,
  onShareAppMessage,
} from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import CatalogView from "../../components/CatalogView.vue";
import { authStorage } from "../../utils/storage";
import { useSessionStore } from "../../store/session";
const catalog = ref<InstanceType<typeof CatalogView>>();
const session = useSessionStore();
onLoad((options) => authStorage.saveInvite(options?.invite || ""));
onPullDownRefresh(async () => {
  await catalog.value?.reload();
  uni.stopPullDownRefresh();
});
onShareAppMessage(() => ({
  title: "素见好物 · 少一些，更好一些",
  path:
    "/pages/index/index?invite=" +
    (session.profile?.promoter ? session.profile.invite_code : ""),
}));
</script>
<template>
  <PageShell tab="home"><CatalogView ref="catalog" /></PageShell>
</template>
