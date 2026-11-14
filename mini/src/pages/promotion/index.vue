<script setup lang="ts">
import { ref } from "vue";
import {
  onShow,
  onPullDownRefresh,
  onShareAppMessage,
} from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import LoginRequired from "../../components/LoginRequired.vue";
import { memberApi } from "../../api/member";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useTask } from "../../utils/useTask";
import { navigate } from "../../utils/router";
import { money, time, label } from "../../utils/format";
import type { Team } from "../../types/models";
const session = useSessionStore(),
  app = useAppStore();
const { busy, run } = useTask();
const team = ref<Team>({ level1: [], level2: [], commissions: [] }),
  accepted = ref(false);
async function refresh() {
  await app.load();
  await session.refresh();
  team.value = session.profile
    ? await memberApi.team()
    : { level1: [], level2: [], commissions: [] };
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
onShareAppMessage(() => ({
  title: "素见好物 · 少一些，更好一些",
  path:
    "/pages/index/index?invite=" +
    (session.profile?.promoter ? session.profile.invite_code : ""),
}));
</script>
<template>
  <PageShell tab="share"
    ><LoginRequired v-if="!session.profile" message="登录后分享美好" /><template
      v-else
      ><view class="share-hero"
        ><text class="eyebrow">BETTER TOGETHER</text
        ><view class="title">好物，值得分享。</view
        ><text class="sub"
          >无入门费，无注册现金奖励。<br />仅按两级真实成交及可分配毛利计佣。</text
        ><text
          v-if="app.rules && !app.rules.distributionEnabled"
          class="caption"
          >当前分销活动未开启，分享不产生现金佣金。</text
        ><template v-if="session.profile.promoter"
          ><view class="code" @tap="copyInvite">{{
            session.profile.invite_code
          }}</view
          ><button class="primary" open-type="share">分享给朋友</button
          ><text class="link" @tap="copyInvite">复制邀请码</text></template
        ><template v-else
          ><view class="checkbox" @tap="accepted = !accepted"
            ><text>{{ accepted ? "☑" : "□" }} 我已阅读并同意</text
            ><text
              class="link"
              @tap.stop="navigate('legal', { type: 'distribution' })"
              >推广规则</text
            ></view
          ><button class="primary" :disabled="!accepted || busy" @tap="promote">
            成为好物分享者
          </button></template
        ></view
      ><view class="asset-grid"
        ><view
          ><text>直接邀请</text><view>{{ team.level1.length }}</view></view
        ><view
          ><text>二级归因</text><view>{{ team.level2.length }}</view></view
        ><view
          ><text>可用佣金</text
          ><view>{{ money(session.profile.available) }}</view></view
        ></view
      ><view class="box"
        ><view class="field-title">成交佣金明细</view
        ><view v-for="item in team.commissions" :key="item.id" class="history"
          ><view class="row"
            ><text>{{ item.level_no }}级佣金 · {{ label(item.state) }}</text
            ><text>¥{{ money(item.amount) }}</text></view
          ><text class="sub">{{ item.order_id }}</text></view
        ><text v-if="!team.commissions.length" class="sub"
          >暂无有效成交佣金</text
        ></view
      ><view class="box"
        ><view class="field-title">我的直接邀请</view
        ><view v-for="person in team.level1" :key="person.id" class="row"
          ><text>{{ person.name }}</text
          ><text class="caption">{{ time(person.created_at) }}</text></view
        ><text v-if="!team.level1.length" class="sub"
          >分享你的邀请链接，邀请真正喜欢好物的朋友。</text
        ></view
      ></template
    ></PageShell
  >
</template>
