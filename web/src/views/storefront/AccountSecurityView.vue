<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { useUiStore } from "@/store/ui";
import { accountApi } from "@/api/account";
import type { IdentityStatus } from "@/types/account";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import PhoneBindingPanel from "@/components/account/PhoneBindingPanel.vue";
import IdentityVerificationPanel from "@/components/account/IdentityVerificationPanel.vue";
const auth = useAuthStore(),
  config = useConfigStore(),
  ui = useUiStore();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const identity = ref<IdentityStatus | null>(null),
  loading = ref(false),
  loadError = ref(false);
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    const [, result] = await Promise.all([
      auth.fetchMemberProfile(),
      accountApi.getIdentity(),
    ]);
    identity.value = result;
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}
async function updated(result: IdentityStatus) {
  identity.value = result;
  await auth.fetchMemberProfile().catch(() => {});
}
onMounted(load);
</script>
<template>
  <AccountHeader title="账户安全与实名认证" />
  <section class="section narrow security-page">
    <RouterLink to="/account" class="back-link">返回我的账户</RouterLink>
    <p class="security-intro">
      用经过验证的登录方式保护账户，让每一笔交易与收益清晰归属。
    </p>
    <PhoneBindingPanel
      v-if="profile"
      :phone-bound="Boolean(profile.phoneBound)"
      :phone-masked="profile.phoneMasked"
      :enabled="config.config.phoneLoginEnabled !== false"
      @saved="ui.message('手机号已绑定当前账户')"
    />
    <div v-if="profile" class="panel security-panel">
      <div class="security-title">
        <h3>微信账户</h3>
        <span class="badge">{{
          profile.wechatBound ? "已绑定" : "未绑定"
        }}</span>
      </div>
      <p v-if="profile.wechatBound" class="muted">
        当前会员已关联微信身份，可在对应微信小程序查看同一账户的订单与收益。
      </p>
      <p v-else class="muted">
        请在微信小程序使用相同手机号登录，再在“账户安全”中绑定微信。小程序支付及佣金出款需绑定实际微信身份；网页扫码与H5支付可直接发起。若微信已属于另一会员，请联系客服核对，系统不会自动合并资产。
      </p>
    </div>
    <p v-if="loading" role="status" class="notice">正在读取实名认证状态…</p>
    <div v-else-if="loadError" class="panel">
      <p class="warning" role="alert">暂时无法读取认证状态，请重新加载。</p>
      <button class="button outline" @click="load">重新加载认证状态</button>
    </div>
    <IdentityVerificationPanel
      v-else-if="identity"
      :identity="identity"
      :enabled="config.config.identityVerificationEnabled !== false"
      :development-mode="Boolean(config.config.devMode)"
      @updated="updated"
      @refresh="load"
    />
    <RouterLink to="/withdraw" class="text-link">查看佣金提现</RouterLink>
  </section>
</template>
