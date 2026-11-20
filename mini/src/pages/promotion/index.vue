<script setup lang="ts">
import { computed, ref } from "vue";
import {
  onShow,
  onPullDownRefresh,
  onShareAppMessage,
} from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import LoginRequired from "../../components/LoginRequired.vue";
import RecordPager from "../../components/RecordPager.vue";
import { memberApi } from "../../api/member";
import { memberRecords } from "../../api/records";
import { usePagedRecords } from "../../composables/usePagedRecords";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { navigate } from "../../utils/router";
import { money, time, label } from "../../utils/format";
import type { Commission, Invitee } from "../../types/models";
const session = useSessionStore(),
  app = useAppStore();
const { busy, run } = useTask();
const accepted = ref(false),
  state = ref(""),
  inviteSearch = ref(""),
  secondLevel = ref(false);
const states = ["", "PENDING", "SETTLED", "REVERSED"];
const stateLabels = states.map((value) => (value ? label(value) : "全部佣金"));
const commissions = usePagedRecords<Commission>(
  (page, size) =>
    memberRecords("commissions", page, size, { state: state.value }),
  true,
);
const direct = usePagedRecords<Invitee>(
  (page, size) =>
    memberRecords("invites", page, size, { search: inviteSearch.value }),
  true,
);
const indirect = usePagedRecords<Invitee>(
  (page, size) =>
    memberRecords("second-invites", page, size, { search: inviteSearch.value }),
  true,
);
const people = computed(() => (secondLevel.value ? indirect : direct));
const summary = commissions.summary;
async function refresh() {
  await app.load(true);
  await session.refresh();
  if (session.profile)
    await Promise.all([
      commissions.reload(),
      direct.reload(),
      indirect.reload(),
    ]);
  else {
    commissions.clear();
    direct.clear();
    indirect.clear();
  }
}
async function reload() {
  await run(refresh);
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function promote() {
  await run(async () => {
    await memberApi.promoter(accepted.value);
    await refresh();
  });
}
function copyInvite() {
  if (session.profile?.invite_code)
    uni.setClipboardData({ data: session.profile.invite_code });
}
function filterCommission(event: { detail: { value: string } }) {
  state.value = states[Number(event.detail.value)] || "";
  void run(commissions.reload);
}
function searchInvites() {
  void run(() => Promise.all([direct.reload(), indirect.reload()]));
}
onShareAppMessage(() => ({
  title: "素见好物 · 少一些，更好一些",
  path:
    "/pages/index/index?invite=" +
    (session.profile?.promoter ? session.profile.invite_code : ""),
}));
</script>
<template>
  <PageShell tab="share">
    <LoginRequired v-if="!session.profile" message="登录后分享美好" />
    <template v-else>
      <view class="share-hero">
        <text class="eyebrow">BETTER TOGETHER</text
        ><view class="title">好物，值得分享。</view>
        <text class="sub"
          >无入门费，无注册现金奖励。<br />仅按两级真实成交及可分配毛利计佣。</text
        >
        <text v-if="app.rules && !app.rules.distributionEnabled" class="caption"
          >当前分销活动未开启，分享不产生现金佣金。</text
        >
        <template v-if="session.profile.promoter">
          <view class="code" @tap="copyInvite">{{
            session.profile.invite_code
          }}</view>
          <button class="primary" open-type="share">分享给朋友</button
          ><text class="link" @tap="copyInvite">复制邀请码</text>
        </template>
        <template v-else>
          <view class="checkbox" @tap="accepted = !accepted"
            ><text>{{ accepted ? "☑" : "□" }} 我已阅读并同意</text
            ><text
              class="link"
              @tap.stop="navigate('legal', { type: 'distribution' })"
              >推广规则</text
            ></view
          >
          <button class="primary" :disabled="!accepted || busy" @tap="promote">
            成为好物分享者
          </button>
        </template>
      </view>
      <view class="asset-grid">
        <view
          ><text>直接邀请</text
          ><view>{{ summary.level1Count ?? "—" }}</view></view
        >
        <view
          ><text>二级归因</text
          ><view>{{ summary.level2Count ?? "—" }}</view></view
        >
        <view
          ><text>待结算佣金</text
          ><view>{{
            summary.pendingAmount === undefined
              ? "—"
              : money(summary.pendingAmount)
          }}</view></view
        >
      </view>
      <view class="box">
        <view class="field-title">成交佣金明细</view>
        <picker
          :range="stateLabels"
          :value="states.indexOf(state)"
          :disabled="commissions.loading.value"
          @change="filterCommission"
          ><view class="row"
            ><text>记录状态</text
            ><text class="link"
              >{{ stateLabels[states.indexOf(state)] }} ▾</text
            ></view
          ></picker
        >
        <view
          v-for="item in commissions.items.value"
          :key="item.id"
          class="history"
          ><view class="row"
            ><text>{{ item.level_no }}级佣金 · {{ label(item.state) }}</text
            ><text>¥{{ money(item.amount) }}</text></view
          ><text class="sub">{{ item.order_id }}</text></view
        >
        <text
          v-if="
            commissions.loaded.value &&
            !commissions.failed.value &&
            !commissions.items.value.length
          "
          class="sub"
          >当前条件下暂无成交佣金。</text
        >
        <RecordPager
          :page="commissions.page.value"
          :total="commissions.total.value"
          :total-pages="commissions.totalPages.value"
          :loading="commissions.loading.value"
          :failed="commissions.failed.value"
          @more="run(commissions.next)"
          @retry="run(commissions.retry)"
        />
      </view>
      <view class="box">
        <view class="row"
          ><text class="field-title">{{
            secondLevel ? "二级归因会员" : "我的直接邀请"
          }}</text
          ><text class="link" @tap="secondLevel = !secondLevel"
            >切换{{ secondLevel ? "直接" : "二级" }}邀请</text
          ></view
        >
        <view class="search"
          ><input
            v-model="inviteSearch"
            placeholder="按会员昵称查询"
            confirm-type="search"
            @confirm="searchInvites"
          /><text @tap="searchInvites">搜索</text></view
        >
        <view v-for="person in people.items.value" :key="person.id" class="row"
          ><text>{{ person.name }}</text
          ><text class="caption">{{ time(person.created_at) }}</text></view
        >
        <text
          v-if="
            people.loaded.value &&
            !people.failed.value &&
            !people.items.value.length
          "
          class="sub"
          >当前条件下暂无邀请记录。</text
        >
        <RecordPager
          :page="people.page.value"
          :total="people.total.value"
          :total-pages="people.totalPages.value"
          :loading="people.loading.value"
          :failed="people.failed.value"
          @more="run(people.next)"
          @retry="run(people.retry)"
        />
      </view>
    </template>
  </PageShell>
</template>
