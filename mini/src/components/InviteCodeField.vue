<script setup lang="ts">
import { ref, watch } from "vue";
import { authStorage } from "../utils/storage";
defineOptions({ options: { styleIsolation: "apply-shared" } });
const invite = ref(authStorage.invite());
watch(
  invite,
  (value) => {
    const normalized = value.replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
    if (value !== normalized) invite.value = normalized;
    authStorage.setInvite(normalized);
  },
  { flush: "sync" },
);
</script>
<template>
  <view class="field">
    <text>邀请码（首次注册可选）</text>
    <input
      v-model="invite"
      maxlength="24"
      placeholder="分享链接已自动带入，也可手动填写"
    />
    <text class="caption"
      >邀请码仅用于新会员首次注册，不会修改已有账户的邀请归属。</text
    >
  </view>
</template>
