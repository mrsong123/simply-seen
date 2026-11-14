<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import AccountConsent from "../../components/AccountConsent.vue";
import PhoneAccessForm from "../../components/PhoneAccessForm.vue";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask, confirm } from "../../utils/useTask";
import { navigate, requireSession } from "../../utils/router";
import { identityLabel } from "../../utils/identity";
const session = useSessionStore(),
  app = useAppStore();
const accepted = ref(false),
  loadError = ref(false);
const { busy, run } = useTask();
async function reload() {
  if (!requireSession("security")) return;
  await run(async () => {
    loadError.value = false;
    try {
      await Promise.all([app.load(), session.refresh()]);
    } catch (error) {
      loadError.value = true;
      throw error;
    }
  });
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function bindWechat() {
  if (
    !(await confirm(
      "将当前微信绑定到此会员，用于微信支付和收款。若微信已属于其他账户，将拒绝绑定，不会合并账户。",
      "绑定当前微信",
    ))
  )
    return;
  await run(async () => {
    await session.bindWechat();
    uni.showToast({ title: "微信已绑定", icon: "success" });
  });
}
</script>
<template>
  <PageShell>
    <PageHeading title="账户与安全" />
    <view v-if="loadError" class="box"
      ><text class="sub">暂时无法读取账户信息，请重试。</text
      ><button class="secondary" :disabled="busy" @tap="reload">
        重新加载
      </button></view
    >
    <template v-if="session.profile">
      <view class="box">
        <view class="field-title">登录手机号</view>
        <template v-if="session.profile.phoneBound">
          <view class="security-value">{{ session.profile.phoneMasked }}</view>
          <text class="sub">手机号已验证，可用于短信登录。</text>
          <text class="caption"
            >为保护账户资金，不能在此直接更换绑定手机号。换号请联系客服核验账户归属。</text
          >
        </template>
        <template v-else-if="app.config?.phoneLoginEnabled">
          <text class="sub"
            >验证本人手机号后，可通过短信登录当前账户。已有其他手机号账户时不自动合并。</text
          >
          <AccountConsent
            v-model="accepted"
            purpose="我同意为账户安全处理本人手机号并发送本次验证码，已阅读"
            :terms="false"
          />
          <PhoneAccessForm
            mode="bind"
            :accepted="accepted"
            @success="accepted = false"
          />
        </template>
        <text v-else class="sub">手机号绑定服务暂未开放，请稍后再试。</text>
      </view>
      <view class="box">
        <view class="field-title">微信账户</view>
        <view class="security-value">{{
          session.profile.wechatBound
            ? "当前会员已绑定微信"
            : "尚未绑定真实微信"
        }}</view>
        <text class="sub"
          >绑定后可使用当前会员的订单进行微信支付，并接收佣金转账。</text
        >
        <text class="caption"
          >手机号登录与微信登录可能对应不同会员。若发生占用冲突，请联系客服处理，不会自动合并积分、资金及邀请归属。</text
        >
        <button
          v-if="!session.profile.wechatBound"
          class="secondary"
          :disabled="busy"
          @tap="bindWechat"
        >
          绑定当前微信
        </button>
      </view>
      <view class="box">
        <view class="field-title">实名认证</view>
        <view class="security-value">{{
          identityLabel(
            session.profile.identityStatus,
            session.profile.identityDevelopment,
          )
        }}</view>
        <text class="sub"
          >通过姓名与身份证号码一致性核验保护提现账户。仅核验信息匹配，不包含人脸或活体识别。</text
        >
        <button class="secondary" @tap="navigate('identity')">
          查看或完成认证
        </button>
      </view>
    </template>
  </PageShell>
</template>
