<script setup lang="ts">
defineProps<{ needsIdentity: boolean; needsWechat: boolean }>();
</script>
<template>
  <div
    v-if="needsIdentity || needsWechat"
    class="notice security-gate"
    role="status"
  >
    <p v-if="needsIdentity">
      提现前请完成实名认证。开发模拟结果不能用于生产提现。
    </p>
    <p v-if="needsWechat">
      微信出款需绑定真实微信账户，请在小程序使用同一手机号登录并完成绑定。
    </p>
    <RouterLink to="/account/security" class="text-link"
      >前往账户安全与实名认证</RouterLink
    >
  </div>
</template>
