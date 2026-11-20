<script setup lang="ts">
import { ref, watch } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import ModalPanel from "../../components/ModalPanel.vue";
import AccountLoginPanel from "../../components/AccountLoginPanel.vue";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useCartStore } from "../../store/cart";
import { memberRecords } from "../../api/records";
import { usePagedRecords } from "../../composables/usePagedRecords";
import RecordPager from "../../components/RecordPager.vue";
import { useTask } from "../../utils/useTask";
import { navigate, afterLogin, type RouteName } from "../../utils/router";
import { money, time, label } from "../../utils/format";
import type { Ledger } from "../../types/models";
const session = useSessionStore(),
  app = useAppStore(),
  cart = useCartStore();
const { busy, run } = useTask();
const asset = ref("");
const assets = ["", "points", "points_held", "available", "reserved", "debt"];
const assetLabels = assets.map((value) => (value ? label(value) : "全部资产"));
const records = usePagedRecords<Ledger>(
  (page, size) => memberRecords("ledger", page, size, { asset: asset.value }),
  true,
);
const {
  items: ledger,
  page,
  total,
  totalPages,
  loaded,
  loading,
  failed,
  next,
  retry,
} = records;
const name = ref(""),
  nameOpen = ref(false);
const links: { name: RouteName; label: string }[] = [
  { name: "orders", label: "我的订单" },
  { name: "addresses", label: "地址管理" },
  { name: "share", label: "分享与佣金" },
  { name: "withdraw", label: "佣金提现" },
  { name: "security", label: "账户与安全" },
  { name: "identity", label: "实名认证" },
  { name: "awards", label: "月度奖励" },
];
async function refresh() {
  await app.load(true);
  await session.refresh();
  if (session.profile) await records.reload();
  else records.clear();
}
async function reload() {
  await run(refresh);
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
function changeAsset(event: { detail: { value: string } }) {
  asset.value = assets[Number(event.detail.value)] || "";
  void run(records.reload);
}
watch(
  () => session.profile?.id,
  () => records.clear(),
  { flush: "sync" },
);
function loggedIn() {
  afterLogin();
  void cart.refresh().catch(() => undefined);
  void refresh().catch(() => undefined);
}
function editName() {
  name.value = session.profile?.name || "";
  nameOpen.value = true;
}
async function saveName() {
  await run(async () => {
    if (name.value.trim().length < 2) throw new Error("昵称至少2个字符");
    await session.updateName(name.value);
    nameOpen.value = false;
  });
}
async function logout() {
  await run(async () => {
    await session.logout();
    records.clear();
  });
}
</script>
<template>
  <PageShell tab="account"
    ><AccountLoginPanel v-if="!session.profile && !session.hasStoredSession" @success="loggedIn" />
    <view v-else-if="!session.profile" class="box"><text class="sub">本机已有登录凭据，账户状态尚未读取完成。可重试读取，无需重新注册。</text><button class="secondary" :disabled="busy" @tap="reload">重新读取账户</button></view>
    <template v-else
      ><view class="member-card"
        ><view class="avatar">{{ session.profile.name.slice(0, 1) }}</view
        ><view
          ><text class="eyebrow">WELCOME BACK</text
          ><view class="title">{{ session.profile.name }}</view></view
        ><text class="link" @tap="editName">编辑</text></view
      ><view class="asset-grid"
        ><view @tap="navigate('points')"
          ><text>可用积分</text><view>{{ session.profile.points }}</view></view
        ><view @tap="navigate('withdraw')"
          ><text>可用佣金</text
          ><view>{{ money(session.profile.available) }}</view></view
        ><view @tap="navigate('withdraw')"
          ><text>提现冻结</text
          ><view>{{ money(session.profile.reserved) }}</view></view
        ></view
      ><view class="box"
        ><view
          v-for="link in links"
          :key="link.name"
          class="menu-row"
          @tap="navigate(link.name)"
          ><text>{{ link.label }}</text
          ><text>→</text></view
        ><view class="menu-row" @tap="navigate('legal', { type: 'privacy' })"
          ><text>隐私与信息保护</text><text>→</text></view
        ><view class="menu-row" @tap="navigate('legal', { type: 'support' })"
          ><text>联系素见</text><text>→</text></view
        ></view
      ><view class="box"
        ><view class="field-title">资产明细</view
        ><picker
          :range="assetLabels"
          :value="assets.indexOf(asset)"
          :disabled="loading"
          @change="changeAsset"
          ><view class="row"
            ><text>资产类型</text
            ><text class="link"
              >{{ assetLabels[assets.indexOf(asset)] }} ▾</text
            ></view
          ></picker
        ><view v-for="item in ledger" :key="item.id" class="history"
          ><view class="row"
            ><text>{{ item.note }}</text
            ><text
              >{{ item.amount > 0 ? "+" : ""
              }}{{
                item.asset.startsWith("points")
                  ? item.amount
                  : money(item.amount)
              }}</text
            ></view
          ><text class="sub"
            >{{ label(item.asset) }} · {{ time(item.created_at) }}</text
          ></view
        ><text v-if="loaded && !failed && !ledger.length" class="sub"
          >当前条件下暂无资产变动</text
        ><RecordPager
          :page="page"
          :total="total"
          :total-pages="totalPages"
          :loading="loading"
          :failed="failed"
          @more="run(next)"
          @retry="run(retry)" /></view
      ><button class="text-button" :disabled="busy" @tap="logout">
        退出登录
      </button></template
    ><ModalPanel :open="nameOpen" title="怎么称呼你？" @close="nameOpen = false"
      ><view class="field"><input v-model="name" maxlength="40" /></view
      ><button class="primary" :disabled="busy" @tap="saveName">
        保存昵称
      </button></ModalPanel
    ></PageShell
  >
</template>
