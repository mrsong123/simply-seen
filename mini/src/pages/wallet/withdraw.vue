<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import WithdrawalRequirements from "../../components/WithdrawalRequirements.vue";
import { memberApi } from "../../api/member";
import { memberRecords } from "../../api/records";
import { usePagedRecords } from "../../composables/usePagedRecords";
import RecordPager from "../../components/RecordPager.vue";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { requireSession, navigate } from "../../utils/router";
import { withdrawalRequirements } from "../../utils/identity";
import { confirmMerchantTransfer } from "../../utils/wechat";
import { requestKey } from "../../utils/idempotency";
import { money, time, label } from "../../utils/format";
import type { Withdrawal } from "../../types/models";
import { yuanToFen } from "../../utils/amount";
const session = useSessionStore(),
  app = useAppStore();
const { busy, run } = useTask();
const amount = ref(""),
  state = ref("");
const states = [
  "",
  "REVIEWING",
  "APPROVED",
  "SUBMITTED",
  "CONFIRMING",
  "UNKNOWN",
  "SUCCESS",
  "FAILED",
  "REJECTED",
];
const stateLabels = states.map((value) => (value ? label(value) : "全部状态"));
const records = usePagedRecords<Withdrawal>(
  (page, size) =>
    memberRecords("withdrawals", page, size, { state: state.value }),
  true,
);
const {
  items: withdrawals,
  page,
  total,
  totalPages,
  loaded,
  loading,
  failed,
  next,
  retry,
} = records;
const needs = computed(() =>
  withdrawalRequirements(session.profile, app.config),
);
const transferAvailable = computed(
  () => !!app.config && (app.config.devMode || app.config.transferEnabled),
);
let idempotency = requestKey();
async function refresh() {
  await app.load(true);
  await session.refresh();
  await records.reload();
}
async function reload() {
  if (!requireSession("withdraw")) return;
  await run(refresh);
}
function filter(event: { detail: { value: string } }) {
  state.value = states[Number(event.detail.value)] || "";
  void reload();
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function submit() {
  await run(async () => {
    await session.refresh();
    if (!transferAvailable.value) throw new Error("提现服务尚未开放");
    if (needs.value.identity) {
      navigate("identity");
      throw new Error("请先完成实名认证，再申请提现");
    }
    if (needs.value.wechat) {
      navigate("security");
      throw new Error("请先绑定真实微信账户，再申请提现");
    }
    await memberApi.withdraw(yuanToFen(amount.value), idempotency);
    idempotency = requestKey();
    amount.value = "";
    uni.showToast({ title: "提现申请已提交", icon: "success" });
    void refresh().catch(() => undefined);
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
    uni.showToast({ title: "已提交确认，请等待渠道结果", icon: "none" });
    void refresh().catch(() => undefined);
  });
}
</script>
<template>
  <PageShell
    ><PageHeading title="佣金提现" /><WithdrawalRequirements /><view class="box"
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
      ><text v-if="app.config && !transferAvailable" class="security-notice"
        >当前提现服务未开放。可继续查看历史记录，开放后再提交申请。</text
      ><button
        class="primary"
        :disabled="
          busy ||
          !amount ||
          !transferAvailable ||
          needs.identity ||
          needs.wechat
        "
        @tap="submit"
      >
        提交申请
      </button></view
    ><view class="box"
      ><view class="row"
        ><text class="field-title">提现记录</text
        ><text class="link" @tap="reload">刷新记录</text></view
      ><picker
        :range="stateLabels"
        :value="states.indexOf(state)"
        :disabled="loading"
        @change="filter"
        ><view class="row"
          ><text>记录状态</text
          ><text class="link"
            >{{ stateLabels[states.indexOf(state)] }} ▾</text
          ></view
        ></picker
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
      ><text v-if="loaded && !failed && !withdrawals.length" class="sub"
        >当前条件下暂无提现记录</text
      ><RecordPager
        :page="page"
        :total="total"
        :total-pages="totalPages"
        :loading="loading"
        :failed="failed"
        @more="run(next)"
        @retry="run(retry)" /></view
  ></PageShell>
</template>
