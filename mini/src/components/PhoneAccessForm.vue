<script setup lang="ts">
import { ref } from "vue";
import { accountApi } from "../api/account";
import { usePhoneChallenge } from "../composables/usePhoneChallenge";
import { useSessionStore } from "../store/session";
import { useTask } from "../utils/useTask";
const props = defineProps<{ mode: "login" | "bind"; accepted: boolean }>();
const emit = defineEmits<{ success: [] }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
const session = useSessionStore(),
  { busy, run } = useTask();
const failure = ref("");
const sms = usePhoneChallenge((phone) =>
  props.mode === "login"
    ? accountApi.sendLoginCode(phone)
    : accountApi.sendBindingCode(phone),
);
async function sendCode() {
  if (!props.accepted) return;
  await run(async () => {
    failure.value = "";
    try {
      await sms.send();
    } catch (error) {
      failure.value = (error as Error).message;
      throw error;
    }
  });
}
async function submit() {
  await run(async () => {
    if (!props.accepted)
      throw new Error("请阅读并同意本页信息处理说明和隐私政策");
    const proof = sms.proof();
    failure.value = "";
    try {
      if (props.mode === "login")
        await session.loginPhone({ ...proof, accepted: true });
      else await session.bindPhone(proof);
    } catch (error) {
      const value = error as Error & { code?: string };
      failure.value = value.message;
      if (
        [
          "SMS_CODE_EXPIRED",
          "SMS_CODE_LOCKED",
          "SMS_CODE_USED",
          "SMS_CODE_SUPERSEDED",
          "SMS_CODE_DEVELOPMENT",
          "PHONE_IN_USE",
          "PHONE_ALREADY_BOUND",
        ].includes(value.code || "")
      )
        sms.discardProof();
      throw error;
    }
    sms.reset();
    uni.showToast({
      title: props.mode === "login" ? "登录成功" : "手机号已绑定",
      icon: "success",
    });
    emit("success");
  });
}
</script>
<template>
  <view class="phone-form">
    <text v-if="failure" class="security-error">{{ failure }}</text>
    <view class="field">
      <text>手机号码</text>
      <input
        v-model="sms.phone.value"
        type="number"
        maxlength="11"
        placeholder="请输入本人大陆手机号"
        :disabled="busy"
      />
    </view>
    <view class="field">
      <text>短信验证码</text>
      <view class="sms-input-row">
        <input
          v-model="sms.code.value"
          type="number"
          maxlength="6"
          placeholder="6位验证码"
          :disabled="busy"
        />
        <button
          class="secondary sms-send"
          :disabled="busy || !accepted || !sms.canSend.value"
          @tap="sendCode"
        >
          {{
            sms.retrySeconds.value
              ? sms.retrySeconds.value + "秒后重发"
              : "获取验证码"
          }}
        </button>
      </view>
    </view>
    <view
      v-if="sms.challenge.value?.development && sms.challenge.value.devCode"
      class="security-notice"
    >
      <text
        >开发短信模拟，未发送真实短信。本次验证码：{{
          sms.challenge.value.devCode
        }}</text
      >
    </view>
    <text v-if="sms.expired.value" class="security-error"
      >验证码已过期，请重新获取。</text
    >
    <text v-if="mode === 'login'" class="caption"
      >未注册手机号验证后自动创建会员，邀请归属按首次注册记录。</text
    >
    <button
      class="primary"
      :disabled="busy || !accepted || !sms.challenge.value || sms.expired.value"
      @tap="submit"
    >
      {{ mode === "login" ? "验证并登录" : "验证并绑定手机号" }}
    </button>
  </view>
</template>
