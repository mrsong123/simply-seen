<script setup lang="ts">
import { ref } from "vue";
import { onShow, onPullDownRefresh } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import ModalPanel from "../../components/ModalPanel.vue";
import { useSessionStore } from "../../store/session";
import { useAppStore } from "../../store/app";
import { useCartStore } from "../../store/cart";
import { memberApi } from "../../api/member";
import { useTask } from "../../utils/useTask";
import { navigate, afterLogin, type RouteName } from "../../utils/router";
import { money, time, label } from "../../utils/format";
import type { Ledger } from "../../types/models";
const session = useSessionStore(),
  app = useAppStore(),
  cart = useCartStore();
const { busy, run } = useTask();
const ledger = ref<Ledger[]>([]),
  name = ref(""),
  nameOpen = ref(false),
  testName = ref(""),
  accepted = ref(false);
const links: { name: RouteName; label: string }[] = [
  { name: "orders", label: "我的订单" },
  { name: "addresses", label: "地址管理" },
  { name: "share", label: "分享与佣金" },
  { name: "withdraw", label: "佣金提现" },
];
async function refresh() {
  await app.load();
  await session.refresh();
  ledger.value = session.profile ? await memberApi.ledger() : [];
}
async function reload() {
  await run(refresh);
}
onShow(reload);
onPullDownRefresh(async () => {
  await reload();
  uni.stopPullDownRefresh();
});
async function login(dev = false) {
  await run(async () => {
    if (!accepted.value) throw new Error("请阅读并同意服务规则和隐私政策");
    if (dev) {
      if (!app.config?.devMode) throw new Error("测试登录未开放");
      if (testName.value.trim().length < 2)
        throw new Error("测试身份名称至少2个字符");
      await session.loginDev(testName.value.trim());
    } else await session.login();
    await cart.refresh();
    await refresh();
    afterLogin();
  });
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
    ledger.value = [];
  });
}
</script>
<template>
  <PageShell tab="account"
    ><view v-if="!session.profile" class="login-card"
      ><text class="mark">素</text><view class="title">欢迎来到素见</view
      ><text class="sub">登录，开启你的质感日常。</text
      ><view class="checkbox" @tap="accepted = !accepted"
        ><text>{{ accepted ? "☑" : "□" }} 我已阅读并同意</text
        ><text class="link" @tap.stop="navigate('legal', { type: 'terms' })"
          >服务规则</text
        ><text>与</text
        ><text class="link" @tap.stop="navigate('legal', { type: 'privacy' })"
          >隐私政策</text
        ></view
      ><button class="primary" :disabled="busy || !accepted" @tap="login()">
        微信授权登录</button
      ><view v-if="app.config?.devMode" class="field"
        ><text class="caption">开发环境测试登录，不代表真实微信身份</text
        ><input
          v-model="testName"
          placeholder="测试身份名称（至少2字）"
          maxlength="30"
        /><button
          class="secondary"
          :disabled="busy || !accepted"
          @tap="login(true)"
        >
          登录开发账户
        </button></view
      ></view
    ><template v-else
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
        ><view
          v-for="item in ledger.slice(0, 30)"
          :key="item.id"
          class="history"
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
        ><text v-if="!ledger.length" class="sub">暂无资产变动</text></view
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
