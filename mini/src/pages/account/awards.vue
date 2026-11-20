<script setup lang="ts">
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import RecordPager from "../../components/RecordPager.vue";
import { usePagedRecords } from "../../composables/usePagedRecords";
import { memberRecords } from "../../api/records";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { requireSession, navigate } from "../../utils/router";
import { money } from "../../utils/format";
interface Award {
  id: number;
  period: string;
  sales: number;
  amount: number;
  rank_no: number;
  state: string;
}
const app = useAppStore();
const { run } = useTask();
const {
  items,
  page,
  total,
  totalPages,
  loaded,
  loading,
  failed,
  reload: readAwards,
  next,
  retry,
} = usePagedRecords<Award>(
  (page, size) => memberRecords("awards", page, size),
  true,
);
async function reload() {
  if (!requireSession("awards")) return;
  await run(async () => {
    await app.load(true);
    await readAwards();
  });
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
</script>
<template>
  <PageShell>
    <PageHeading title="月度奖励" />
    <view class="box">
      <text class="sub"
        >仅显示已完成双人复核并入账的奖励；奖励进入佣金账户，实际出款仍需申请提现。</text
      >
      <text v-if="app.config && !app.config.monthlyEnabled" class="caption"
        >当前月度活动未开放，历史已核准记录仍可查询。</text
      >
      <button class="secondary" @tap="navigate('withdraw')">
        查看佣金及提现
      </button>
    </view>
    <view v-for="award in items" :key="award.id" class="box">
      <view class="row"
        ><text>{{ award.period }}</text
        ><text class="badge">已核准入账</text></view
      >
      <view class="balance">¥{{ money(award.amount) }}</view>
      <text class="caption"
        >核准成交 ¥{{ money(award.sales) }} · 排名{{ award.rank_no }}</text
      >
    </view>
    <view v-if="loaded && !failed && !items.length" class="empty"
      >暂无已核准的月度奖励。</view
    >
    <view class="box"
      ><RecordPager
        :page="page"
        :total="total"
        :total-pages="totalPages"
        :loading="loading"
        :failed="failed"
        @more="run(next)"
        @retry="run(retry)"
    /></view>
  </PageShell>
</template>
