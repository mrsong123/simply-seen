<script setup lang="ts">
import { navigate, type RouteName } from "../utils/router";
defineProps<{ active: RouteName }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
const tabs: { name: RouteName; label: string; icon: string }[] = [
  { name: "home", label: "好物", icon: "◈" },
  { name: "points", label: "积分", icon: "◇" },
  { name: "share", label: "分享", icon: "↗" },
  { name: "cart", label: "购物袋", icon: "▢" },
  { name: "account", label: "我的", icon: "○" },
];
</script>
<template>
  <view class="nav"
    ><view
      v-for="tab in tabs"
      :key="tab.name"
      :class="{ active: active === tab.name }"
      @tap="navigate(tab.name)"
      ><text class="nav-icon">{{ tab.icon }}</text
      ><text>{{ tab.label }}</text></view
    ></view
  >
</template>
