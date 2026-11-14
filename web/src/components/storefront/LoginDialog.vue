<script setup lang="ts">
import { safeRedirect } from "@/utils/navigation";
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import BaseModal from "@/components/common/BaseModal.vue";
import Icon from "@/components/common/Icon.vue";
import { useAction } from "@/composables/useAction";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { useConfigStore } from "@/store/config";
const auth = useAuthStore(),
  ui = useUiStore(),
  config = useConfigStore(),
  route = useRoute(),
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
async function login() {
  await run(async () => {
    await auth.loginMember({
      name: loginName.value,
      inviteCode: inviteCode.value,
    });
    ui.closeLogin();
    ui.message("欢迎来到素见");
    if (route.name === "login")
      await router.replace(safeRedirect(route.query.redirect, "/account"));
  });
}
</script>
<template>
  <BaseModal v-model="ui.loginVisible"
    ><span class="brand-mark">素</span>
    <h2>欢迎来到素见</h2>
    <p class="muted">登录，开启你的质感日常。</p>
    <form v-if="config.config.devMode" @submit.prevent="login">
      <p class="notice">当前为开发环境，测试登录不代表微信实名身份。</p>
      <label class="input-label"
        >测试身份名称<input
          v-model="loginName"
          required
          minlength="2"
          maxlength="30"
          autocomplete="nickname" /></label
      ><label class="input-label"
        >邀请码（首次注册可选）<input
          v-model="inviteCode"
          maxlength="24" /></label
      ><button class="button dark full" :disabled="busy">
        登录开发账户 <Icon name="ArrowRight" :size="17" />
      </button>
    </form>
    <template v-else
      ><p>请在微信小程序中授权登录，网页支持公开商品浏览。</p>
      <p class="caption">微信内搜索「素见好物」小程序</p></template
    >
    <p class="caption">
      登录即表示同意
      <RouterLink to="/legal/terms" @click="ui.closeLogin()"
        >服务规则</RouterLink
      >
      与
      <RouterLink to="/legal/privacy" @click="ui.closeLogin()"
        >隐私政策</RouterLink
      >
    </p></BaseModal
  >
</template>
