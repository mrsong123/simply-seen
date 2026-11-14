<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow, onHide, onUnload, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import AccountConsent from "../../components/AccountConsent.vue";
import { accountApi } from "../../api/account";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { requireSession } from "../../utils/router";
import { identityLabel, validateIdentity } from "../../utils/identity";
import { time } from "../../utils/format";
import type { IdentityVerification } from "../../types/account";
const app = useAppStore(),
  session = useSessionStore();
const { busy, run } = useTask();
const identity = ref<IdentityVerification | null>(null),
  loadError = ref(false);
const name = ref(""),
  idNumber = ref(""),
  accepted = ref(false);
const now = ref(Date.now()),
  retryAt = ref(0);
let clock: ReturnType<typeof setInterval> | undefined;
const retrySeconds = computed(() =>
  Math.max(0, Math.ceil((retryAt.value - now.value) / 1000)),
);
function setIdentity(value: IdentityVerification) {
  identity.value = value;
  now.value = Date.now();
  retryAt.value =
    now.value +
    Math.max(0, value.retryAfter ?? (value.status === "PENDING" ? 120 : 0)) *
      1000;
}
const complete = computed(
  () =>
    identity.value?.status === "VERIFIED" &&
    (!identity.value.development || app.config?.devMode),
);
function clearForm() {
  name.value = "";
  idNumber.value = "";
  accepted.value = false;
}
async function reload() {
  if (!requireSession("identity")) return;
  await run(async () => {
    loadError.value = false;
    try {
      await app.load();
      setIdentity(await accountApi.identity());
    } catch (error) {
      loadError.value = true;
      throw error;
    }
  });
}
onShow(() => {
  now.value = Date.now();
  if (!clock) clock = setInterval(() => (now.value = Date.now()), 1000);
  void reload();
});
function leavePage() {
  clearForm();
  clearInterval(clock);
  clock = undefined;
}
onHide(leavePage);
onUnload(leavePage);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function submit() {
  await run(async () => {
    if (!accepted.value) throw new Error("请阅读并同意实名认证信息处理说明");
    const value = validateIdentity(name.value, idNumber.value);
    try {
      setIdentity(
        await accountApi.verifyIdentity(value.name, value.idNumber, true),
      );
      void session.refresh().catch(() => undefined);
      uni.showToast({
        title: identityLabel(
          identity.value!.status,
          identity.value!.development,
        ),
        icon: "none",
      });
    } catch (error) {
      try {
        setIdentity(await accountApi.identity());
      } catch {
        /* keep the last confirmed status */
      }
      throw error;
    } finally {
      clearForm();
    }
  });
}
</script>
<template>
  <PageShell>
    <PageHeading title="实名认证" />
    <view class="box">
      <view class="field-title">核验真实姓名与证件信息</view>
      <text class="sub"
        >仅支持本人大陆居民身份证。核验姓名与身份证号码是否一致，不包含人脸或活体识别，也不代表已核验微信收款账户同名。</text
      >
      <text class="caption"
        >信息经加密传输与服务端加密保存，页面只显示脱敏结果；不会写入本机缓存。核验结果用于账户安全及提现准入。</text
      >
    </view>
    <view v-if="loadError" class="box">
      <text class="sub">认证状态暂时无法读取，请重试后再提交。</text>
      <button class="secondary" :disabled="busy" @tap="reload">
        重新读取状态
      </button>
    </view>
    <view v-if="identity" class="box">
      <text class="eyebrow">IDENTITY VERIFICATION</text>
      <view class="security-value">{{
        identityLabel(identity.status, identity.development)
      }}</view>
      <view v-if="identity.nameMasked" class="row"
        ><text>姓名</text><text>{{ identity.nameMasked }}</text></view
      >
      <view v-if="identity.idNumberMasked" class="row"
        ><text>身份证</text><text>{{ identity.idNumberMasked }}</text></view
      >
      <text v-if="identity.verifiedAt" class="caption"
        >核验时间：{{ time(identity.verifiedAt) }}</text
      >
      <view v-if="identity.development" class="security-notice"
        >此结果来自开发模拟，不具备真实身份核验效力，不能用于生产提现。</view
      >
      <text v-if="identity.rejectionReason" class="security-error">{{
        identity.rejectionReason
      }}</text>
      <template v-if="identity.status === 'PENDING'">
        <text class="sub"
          >核验正在处理，请先刷新结果。若超时未完成，可在约2分钟后重新填写同一姓名证件重试；重复提交不会绕过服务端限制。</text
        >
        <button class="secondary" :disabled="busy" @tap="reload">
          刷新认证结果
        </button>
      </template>
    </view>
    <view
      v-if="
        identity &&
        !complete &&
        app.config?.identityVerificationEnabled &&
        !loadError
      "
      class="box"
    >
      <view class="field"
        ><text>真实姓名</text
        ><input
          v-model="name"
          maxlength="40"
          placeholder="请输入身份证上的姓名"
          :disabled="busy"
      /></view>
      <view class="field"
        ><text>18位身份证号码</text
        ><input
          v-model="idNumber"
          maxlength="18"
          placeholder="请输入本人大陆居民身份证号码"
          :disabled="busy"
      /></view>
      <AccountConsent
        v-model="accepted"
        purpose="我同意将本人姓名与身份证号码提供给认证服务商核验，并用于提现账户安全，已阅读"
        :terms="false"
      />
      <text class="caption"
        >核验有次数与频率限制。请确认信息准确；核验未通过可核对后重新提交。</text
      >
      <text v-if="retrySeconds" class="caption"
        >服务端要求等待{{ retrySeconds }}秒后重试，可先刷新结果。</text
      >
      <button
        class="primary"
        :disabled="busy || !accepted || !name || !idNumber || retrySeconds > 0"
        @tap="submit"
      >
        {{
          retrySeconds
            ? retrySeconds + "秒后可重试"
            : identity.status === "UNVERIFIED"
              ? "提交实名认证"
              : "重新提交核验"
        }}
      </button>
    </view>
    <view
      v-else-if="
        identity &&
        !complete &&
        app.config &&
        !app.config.identityVerificationEnabled
      "
      class="box"
      ><text class="sub"
        >实名认证渠道暂未开放，请稍后重试或联系客服。未通过核验时不能进行要求实名的提现操作。</text
      ></view
    >
  </PageShell>
</template>
