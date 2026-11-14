<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/store/auth";
import Icon from "@/components/common/Icon.vue";
const auth = useAuthStore(),
  router = useRouter(),
  route = useRoute();
const busy = ref(false),
  credentials = ref({ username: "", password: "" });
async function signIn() {
  if (busy.value) return;
  busy.value = true;
  try {
    await auth.loginAdmin(credentials.value);
    credentials.value.password = "";
    const target = String(route.query.redirect || "/admin");
    await router.replace(
      target.startsWith("/admin") && !target.startsWith("/admin/login")
        ? target
        : "/admin",
    );
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="admin-login">
    <div class="login-story">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>SIMPLY SEEN</small></span></RouterLink
      >
      <div>
        <div class="eyebrow">THOUGHTFUL BUSINESS</div>
        <h1>用心经营，<br />让美好发生。</h1>
        <p>每一份信任，都值得认真对待。</p>
      </div>
      <small>素见好物 · 运营工作台</small>
    </div>
    <div class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录运营工作台</h2>
        <p class="muted">使用您的独立管理账号安全登录。</p>
        <label class="input-label"
          >账号<input
            v-model="credentials.username"
            required
            autocomplete="username" /></label
        ><label class="input-label"
          >密码<input
            v-model="credentials.password"
            required
            type="password"
            autocomplete="current-password" /></label
        ><button class="button dark full" :disabled="busy">
          安全登录 <Icon name="ArrowRight" :size="17" /></button
        ><RouterLink to="/" class="back-link"
          ><Icon name="ArrowLeft" :size="16" /> 返回素见商城</RouterLink
        >
      </form>
    </div>
  </div>
</template>
