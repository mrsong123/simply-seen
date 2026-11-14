<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import { memberApi } from "../../api/member";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { requireSession } from "../../utils/router";
import { confirmMerchantTransfer } from "../../utils/wechat";
import { requestKey } from "../../utils/idempotency";
import { money, time, label } from "../../utils/format";
import type { Withdrawal } from "../../types/models";
const session = useSessionStore(),
  app = useAppStore();
const { busy, run } = useTask();
const amount = ref(""),
  withdrawals = ref<Withdrawal[]>([]);
let idempotency = requestKey();
async function refresh() {
  await app.load();
  await session.refresh();
  withdrawals.value = await memberApi.withdrawals();
}
async function reload() {
  if (!requireSession("withdraw")) return;
  await run(refresh);
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function submit() {
  await run(async () => {
    if (!/^\d+(\.\d{1,2})?$/.test(amount.value) || Number(amount.value) <= 0)
      throw new Error("请输入有效金额，最多两位小数");
    await memberApi.withdraw(
      Math.round(Number(amount.value) * 100),
      idempotency,
    );
    idempotency = requestKey();
    amount.value = "";
    await refresh();
    uni.showToast({ title: "提现申请已提交", icon: "success" });
  });
}
async function receive(item: Withdrawal) {
  await run(async () => {
    if (!item.package_info) throw new Error("请稍后刷新收款参数");
    if (!app.config?.mchId || !app.config.appId)
      throw new Error("商户收款配置不可用，请联系客服");
    await confirmMerchantTransfer(
      app.config.mchId,
      app.config.appId,
      item.package_info,
    );
    await refresh();
    uni.showToast({ title: "已提交确认，请等待渠道结果", icon: "none" });
  });
}
</script>
<template>
  <PageShell
    ><PageHeading title="佣金提现" /><view class="box"
      ><text class="sub">可申请提现佣金</text
      ><view class="balance">¥{{ money(session.profile?.available) }}</view
      ><text class="caption"
        >冻结 ¥{{ money(session.profile?.reserved) }} · 待追偿 ¥{{
          money(session.profile?.debt)
        }}</text
      ><view class="field"
        ><text>申请金额（元）</text
        ><input
          v-model="amount"
          type="digit"
          placeholder="请输入提现金额" /></view
      ><text class="caption"
        >税额经财务根据实际适用依据确认。渠道确认前不表示已到账。</text
      ><button class="primary" :disabled="busy || !amount" @tap="submit">
        提交申请
      </button></view
    ><view class="box"
      ><view class="field-title">提现记录</view
      ><view v-for="item in withdrawals" :key="item.id" class="history"
        ><view class="row"
          ><text>¥{{ money(item.gross) }}</text
          ><text class="badge">{{ label(item.state) }}</text></view
        ><text class="sub"
          >税款 ¥{{ money(item.tax) }} · 税后 ¥{{ money(item.net) }}</text
        ><text class="caption"
          >{{ time(item.created_at) }} {{ item.reason || "" }}</text
        ><button
          v-if="item.state === 'CONFIRMING'"
          class="primary"
          :disabled="busy"
          @tap="receive(item)"
        >
          确认收款
        </button></view
      ><text v-if="!withdrawals.length" class="sub">暂无提现记录</text></view
    ></PageShell
  >
</template>
