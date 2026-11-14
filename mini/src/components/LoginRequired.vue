<script setup lang="ts">
import { navigate } from "../utils/router";
defineProps<{ message?: string }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
</script>
<template>
  <view class="empty"
    ><text>{{ message || "登录，让美好与你相遇" }}</text
    ><button class="primary" @tap="navigate('account')">前往登录</button></view
  >
</template>
