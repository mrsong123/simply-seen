<script setup lang="ts">
import { computed } from "vue";
import { useSessionStore } from "../store/session";
import { useAppStore } from "../store/app";
import { withdrawalRequirements } from "../utils/identity";
import { navigate } from "../utils/router";
defineOptions({ options: { styleIsolation: "apply-shared" } });
const session = useSessionStore(),
  app = useAppStore();
const needs = computed(() =>
  withdrawalRequirements(session.profile, app.config),
);
</script>
<template>
  <view v-if="needs.identity || needs.wechat" class="box">
    <view class="field-title">提现前完善账户信息</view>
    <template v-if="needs.identity"
      ><text class="sub">请先完成本人姓名与身份证号码核验。</text
      ><button class="secondary" @tap="navigate('identity')">
        前往实名认证
      </button></template
    >
    <template v-if="needs.wechat"
      ><text class="sub">微信收款需要当前会员绑定真实微信账户。</text
      ><button class="secondary" @tap="navigate('security')">
        前往绑定微信
      </button></template
    >
  </view>
</template>
