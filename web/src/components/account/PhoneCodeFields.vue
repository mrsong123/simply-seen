<script setup lang="ts">
import { usePhoneChallenge } from "@/composables/account/usePhoneChallenge";
import type { PhoneChallenge } from "@/types/account";
const props = defineProps<{
  send: (phone: string) => Promise<PhoneChallenge>;
  disabled?: boolean;
  consentRequired?: boolean;
}>();
const sms = usePhoneChallenge((phone) => props.send(phone));
defineExpose({ credentials: sms.credentials, clear: sms.clear });
</script>
<template>
  <label class="input-label"
    >手机号码
    <input
      v-model="sms.phone"
      type="tel"
      inputmode="tel"
      autocomplete="tel-national"
      maxlength="11"
      required
      pattern="1[3-9][0-9]{9}"
      :disabled="disabled || sms.sending"
    />
  </label>
  <div class="phone-code-row">
    <label class="input-label"
      >短信验证码
      <input
        v-model="sms.code"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        required
        pattern="[0-9]{6}"
        :disabled="disabled"
      />
    </label>
    <button
      class="button outline small"
      type="button"
      :disabled="
        disabled || sms.sending || sms.remaining > 0 || consentRequired
      "
      @click="sms.sendCode"
    >
      {{
        sms.sending
          ? "发送中…"
          : sms.remaining
            ? sms.remaining + "秒后重发"
            : "获取验证码"
      }}
    </button>
  </div>
  <p v-if="consentRequired" class="caption left">
    请先勾选服务规则与隐私政策，再获取验证码。
  </p>
  <p v-if="sms.error" role="alert" class="warning">{{ sms.error }}</p>
  <p v-if="sms.developmentCode" class="notice" role="status">
    开发模拟短信验证码：<strong>{{ sms.developmentCode }}</strong
    >。未发送真实短信，请勿用于生产。
  </p>
</template>
