<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "@/components/common/Icon.vue";
import { useAuthStore } from "@/store/auth";
import { useCartStore } from "@/store/cart";
import { useUiStore } from "@/store/ui";
import { storeToRefs } from "pinia";
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  cart = useCartStore(),
  ui = useUiStore();
const { count: cartCount } = storeToRefs(cart);
const mobileNav = ref(false);
watch(
  () => route.fullPath,
  () => (mobileNav.value = false),
);
</script>
<template>
  <header class="store-header">
    <RouterLink to="/" class="brand"
      ><span class="brand-mark">素</span
      ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
    >
    <nav :class="{ open: mobileNav }">
      <RouterLink to="/catalog">全部好物</RouterLink
      ><RouterLink to="/points">积分礼遇</RouterLink
      ><RouterLink to="/share">分享美好</RouterLink
      ><RouterLink to="/legal/about">关于素见</RouterLink>
    </nav>
    <div class="header-actions">
      <button
        class="icon-button"
        aria-label="搜索商品"
        @click="router.push('/catalog')"
      >
        <Icon name="Search" /></button
      ><button
        class="icon-button"
        aria-label="我的账户"
        @click="auth.member ? router.push('/account') : ui.openLogin()"
      >
        <Icon name="User" /></button
      ><RouterLink class="icon-button bag-link" to="/cart" aria-label="购物袋"
        ><Icon name="ShoppingBag" /><i v-if="cartCount">{{
          cartCount
        }}</i></RouterLink
      ><button
        class="icon-button mobile-menu"
        aria-label="打开导航"
        @click="mobileNav = !mobileNav"
      >
        <Icon name="Menu" />
      </button>
    </div>
  </header>
</template>
