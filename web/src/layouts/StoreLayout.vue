<script setup lang="ts">
import { onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/store/auth";
import { useCartStore } from "@/store/cart";
import { useConfigStore } from "@/store/config";
import StoreHeader from "@/components/storefront/StoreHeader.vue";
import StoreFooter from "@/components/storefront/StoreFooter.vue";
import LoginDialog from "@/components/storefront/LoginDialog.vue";
const auth = useAuthStore(),
  cart = useCartStore(),
  config = useConfigStore(),
  route = useRoute(),
  router = useRouter();
onMounted(() => {
  config.load(true).catch(() => {});
  if (auth.member) {
    auth.fetchMemberProfile().catch(() => {});
    cart.load().catch(() => {});
  }
});
watch(
  () => auth.member,
  (session) => {
    if (session) {
      auth.fetchMemberProfile().catch(() => {});
      cart.load().catch(() => {});
    } else {
      cart.clear();
      if (route.meta.requiresMember)
        router.replace({ name: "login", query: { redirect: route.fullPath } });
    }
  },
);
</script>
<template>
  <div class="store-shell">
    <div class="announcement">
      为生活做减法，为美好留余地。<span>精选好物 · 用心相伴</span>
    </div>
    <StoreHeader />
    <main><RouterView /></main>
    <StoreFooter /><LoginDialog />
  </div>
</template>
