<script setup lang="ts">
import { onMounted } from "vue";
import { useUiStore } from "@/store/ui";
import Icon from "@/components/common/Icon.vue";
const ui = useUiStore();
onMounted(() => ui.openLogin());
</script>
<template>
  <section class="empty-state">
    <Icon name="User" :size="40" />
    <h3>登录，让美好与你相遇</h3>
    <p>查看订单、管理积分，收藏属于你的日常。</p>
    <button class="button dark" @click="ui.openLogin()">登录素见</button>
  </section>
</template>
