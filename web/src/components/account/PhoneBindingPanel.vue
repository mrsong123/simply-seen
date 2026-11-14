<script setup lang="ts">
import { ref } from "vue";
import { phoneChallengeMustReset } from "@/utils/account";
import { accountApi } from "@/api/account";
import { useAuthStore } from "@/store/auth";
import PhoneCodeFields from "./PhoneCodeFields.vue";
defineProps<{ phoneBound: boolean; phoneMasked?: string; enabled: boolean }>();
const emit = defineEmits<{ saved: [] }>();
const fields = ref<InstanceType<typeof PhoneCodeFields>>();
const busy = ref(false),
  error = ref("");
const auth = useAuthStore();
async function bind() {
  if (busy.value) return;
  const credentials = fields.value?.credentials();
  if (!credentials) return;
  busy.value = true;
  error.value = "";
  const token = auth.member?.token;
  try {
    const profile = await accountApi.bindPhone(credentials);
    if (auth.member?.token !== token) return;
    auth.memberProfile = profile;
    fields.value?.clear();
    emit("saved");
    void auth.fetchMemberProfile().catch(() => {});
  } catch (failure) {
    if (phoneChallengeMustReset(failure)) fields.value?.clear();
    error.value =
      failure instanceof Error ? failure.message : "绑定失败，请稍后重试";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="panel security-panel">
    <div class="security-title">
      <h3>登录手机号</h3>
      <span class="badge">{{ phoneBound ? "已绑定" : "未绑定" }}</span>
    </div>
    <template v-if="phoneBound"
      ><p class="security-value">{{ phoneMasked }}</p>
      <p class="muted">
        此手机号可用于登录当前会员。为保护账户与资产，修改手机号请联系客服完成身份核验。
      </p></template
    >
    <form v-else-if="enabled" @submit.prevent="bind">
      <p class="muted">
        验证手机号后，将绑定当前会员，保留现有订单、积分和邀请关系。已属于其他会员的手机号不能直接合并。
      </p>
      <PhoneCodeFields
        ref="fields"
        :send="accountApi.sendBindingCode"
        :disabled="busy"
      />
      <p v-if="error" class="warning" role="alert">{{ error }}</p>
      <button class="button dark" :disabled="busy">
        {{ busy ? "绑定中…" : "确认绑定手机号" }}
      </button>
    </form>
    <p v-else class="notice">手机号服务暂未开放，请稍后再试。</p>
  </div>
</template>
