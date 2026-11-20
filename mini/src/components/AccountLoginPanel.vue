<script setup lang="ts">
import { ref } from "vue";
import { useAppStore } from "../store/app";
import { useSessionStore } from "../store/session";
import { useTask } from "../utils/useTask";
import AccountConsent from "./AccountConsent.vue";
import PhoneAccessForm from "./PhoneAccessForm.vue";
import InviteCodeField from "./InviteCodeField.vue";
const emit = defineEmits<{ success: [] }>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
const app = useAppStore(),
  session = useSessionStore();
const accepted = ref(false),
  testName = ref(""),
  testVisible = ref(false);
const { busy, run } = useTask();
async function login(development = false) {
  await run(async () => {
    if (!accepted.value) throw new Error("请阅读并同意服务规则和隐私政策");
    if (development) {
      if (!app.config?.devMode) throw new Error("测试登录未开放");
      if (testName.value.trim().length < 2)
        throw new Error("测试身份名称至少2个字符");
      await session.loginDev(testName.value.trim());
      testName.value = "";
    } else await session.login();
    emit("success");
  });
}
</script>
<template>
  <view class="login-card">
    <text class="mark">素</text>
    <view class="title">欢迎来到素见</view>
    <text class="sub">用手机号或微信，开启你的质感日常。</text>
    <text class="caption account-login-help"
      >已有微信账户，请先微信登录后绑定手机号，再使用手机号登录。新号码首次登录会创建独立账户，积分、资金和邀请归属不会自动合并。</text
    >
    <InviteCodeField />
    <AccountConsent v-model="accepted" />
    <PhoneAccessForm
      v-if="app.config?.phoneLoginEnabled"
      mode="login"
      :accepted="accepted"
      @success="emit('success')"
    />
    <text v-else-if="app.config" class="caption"
      >短信登录暂未开放，可使用微信登录。</text
    >
    <view class="divider"></view>
    <button class="secondary" :disabled="busy || !accepted" @tap="login()">
      微信登录
    </button>
    <template v-if="app.config?.devMode">
      <button class="text-button" @tap="testVisible = !testVisible">
        {{ testVisible ? "收起测试登录" : "使用开发测试身份" }}
      </button>
      <view v-if="testVisible" class="field">
        <text class="caption">仅供开发测试，不代表微信实名身份。</text>
        <input
          v-model="testName"
          placeholder="测试身份名称（至少2字）"
          maxlength="30"
        />
        <button
          class="secondary"
          :disabled="busy || !accepted"
          @tap="login(true)"
        >
          登录开发账户
        </button>
      </view>
    </template>
  </view>
</template>
