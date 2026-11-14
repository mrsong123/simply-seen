<script setup lang="ts">
import TabNavigation from "./TabNavigation.vue";
import { navigate, type RouteName } from "../utils/router";
defineProps<{ tab?: RouteName }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="app"
    ><view class="topbar"
      ><view class="brand" @tap="navigate('home')"
        ><text class="mark">素</text
        ><view
          ><text>素见好物</text><text class="english">SIMPLY SEEN</text></view
        ></view
      ><text class="toplink" @tap="navigate('orders')">我的订单</text></view
    ><slot /><TabNavigation v-if="tab" :active="tab"
  /></view>
</template>
