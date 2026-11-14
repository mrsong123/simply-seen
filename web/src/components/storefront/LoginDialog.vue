<script setup lang="ts">
import { safeRedirect } from "@/utils/navigation";
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import BaseModal from "@/components/common/BaseModal.vue";
import PhoneLoginForm from "@/components/account/PhoneLoginForm.vue";
import { useAction } from "@/composables/useAction";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { useConfigStore } from "@/store/config";
const auth = useAuthStore(),
  ui = useUiStore(),
  config = useConfigStore();
const route = useRoute(),
  router = useRouter();
const { busy, run } = useAction();
const loginName = ref(""),
  inviteCode = ref(localStorage.getItem("sj-invite") || "");
watch(
  () => route.query.invite,
  (code) => {
    if (code) {
      inviteCode.value = String(code);
      localStorage.setItem("sj-invite", inviteCode.value);
    }
  },
  { immediate: true },
);
watch(
  () => ui.loginVisible,
  (visible) => {
    if (!visible) loginName.value = "";
  },
);
async function loggedIn() {
  ui.closeLogin();
  ui.message("欢迎来到素见");
  if (route.name === "login")
    await router.replace(safeRedirect(route.query.redirect, "/account"));
}
async function loginDevelopment() {
  await run(async () => {
    await auth.loginMember({
      name: loginName.value,
      inviteCode: inviteCode.value,
    });
    await loggedIn();
  });
}
</script>
<template>
  <BaseModal v-model="ui.loginVisible">
    <span class="brand-mark">素</span>
    <h2>欢迎来到素见</h2>
    <p class="muted">手机号登录，开启你的质感日常。</p>
    <label class="input-label"
      >邀请码（首次注册可选）
      <input v-model="inviteCode" maxlength="24" autocomplete="off" />
    </label>
    <PhoneLoginForm
      v-if="config.config.phoneLoginEnabled !== false"
      :invite-code="inviteCode"
      @logged-in="loggedIn"
      @open-legal="ui.closeLogin"
    />
    <p v-else class="notice">手机号登录暂未开放，请在微信小程序内登录。</p>
    <details v-if="config.config.devMode" class="dev-login" open>
      <summary>开发测试身份登录（仅开发）</summary>
      <form @submit.prevent="loginDevelopment">
        <p class="notice">
          当前为开发环境，测试登录不代表真实手机号或实名认证。
        </p>
        <label class="input-label"
          >测试身份名称
          <input
            v-model="loginName"
            required
            minlength="2"
            maxlength="30"
            autocomplete="off"
          />
        </label>
        <button class="button outline full" :disabled="busy">
          登录开发账户
        </button>
      </form>
    </details>
  </BaseModal>
</template>
