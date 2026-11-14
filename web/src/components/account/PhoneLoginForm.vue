<script setup lang="ts">
import { ref } from "vue";
import { phoneChallengeMustReset } from "@/utils/account";
import { useAuthStore } from "@/store/auth";
import { authApi } from "@/api/auth";
import PhoneCodeFields from "./PhoneCodeFields.vue";
const props = defineProps<{ inviteCode: string }>();
const emit = defineEmits<{ loggedIn: []; openLegal: [] }>();
const auth = useAuthStore();
const fields = ref<InstanceType<typeof PhoneCodeFields>>();
const accepted = ref(false),
  busy = ref(false),
  error = ref("");
async function login() {
  if (busy.value || !accepted.value) return;
  const credentials = fields.value?.credentials();
  if (!credentials) return;
  busy.value = true;
  error.value = "";
  try {
    await auth.loginPhone({
      ...credentials,
      inviteCode: props.inviteCode,
      accepted: accepted.value,
    });
    fields.value?.clear();
    emit("loggedIn");
  } catch (failure) {
    if (phoneChallengeMustReset(failure)) fields.value?.clear();
    error.value =
      failure instanceof Error ? failure.message : "登录失败，请重新获取验证码";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <form @submit.prevent="login">
    <PhoneCodeFields
      ref="fields"
      :send="authApi.sendPhoneCode"
      :disabled="busy"
      :consent-required="!accepted"
    />
    <p class="caption left">
      已有微信会员？请先在小程序微信登录并绑定手机号，再使用手机号登录，以保留同一账户的订单与收益。
    </p>
    <p class="caption left">
      未注册的手机号将自动创建会员，首次注册可关联邀请码。
    </p>
    <label class="checkbox-label"
      ><input v-model="accepted" type="checkbox" required :disabled="busy" />
      <span
        >我已阅读并同意
        <RouterLink to="/legal/terms" @click="emit('openLegal')"
          >《服务规则》</RouterLink
        >与
        <RouterLink to="/legal/privacy" @click="emit('openLegal')"
          >《隐私政策》</RouterLink
        >
      </span>
    </label>
    <p v-if="error" class="warning" role="alert">{{ error }}</p>
    <button class="button dark full" :disabled="busy || !accepted">
      {{ busy ? "正在登录…" : "手机号登录 / 注册" }}
    </button>
  </form>
</template>
