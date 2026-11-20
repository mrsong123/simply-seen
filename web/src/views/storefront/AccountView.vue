<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/store/auth";
import { memberApi } from "@/api/member";
import { useAction } from "@/composables/useAction";
import { useMemberRecords } from "@/composables/useMemberRecords";
import PageNavigation from "@/components/common/PageNavigation.vue";
import ResourceState from "@/components/common/ResourceState.vue";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import NameDialog from "@/components/storefront/NameDialog.vue";
const auth = useAuthStore(),
  router = useRouter();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const records = useMemberRecords<any>("ledger");
const ledger = records.items;
const nameVisible = ref(false);
const { run } = useAction();
const assets = [
  ["points", "可用积分", "Gift"],
  ["available", "可用佣金", "Wallet"],
  ["reserved", "提现冻结", "Clock"],
];
onMounted(() =>
  run(async () => {
    await auth.fetchMemberProfile();
  }),
);
</script>
<template>
  <AccountHeader :title="'你好，' + (profile?.name || '素见朋友')" /><template
    v-if="profile"
    ><section class="section narrow">
      <div class="member-card">
        <div class="avatar">{{ profile.name.slice(0, 1) }}</div>
        <div>
          <small>WELCOME BACK</small>
          <h2>{{ profile.name }}</h2>
          <span>每一份喜欢，都值得认真对待。</span>
        </div>
        <button class="text-link" @click="nameVisible = true">修改昵称</button>
      </div>
      <div class="asset-grid">
        <RouterLink
          v-for="a in assets"
          :key="a[0]"
          :to="a[0] === 'points' ? '/points' : '/withdraw'"
          class="asset-card"
          ><Icon :name="a[2]" /><span>{{ a[1] }}</span
          ><strong>{{
            a[0] === "points" ? profile[a[0]] : money(profile[a[0]])
          }}</strong></RouterLink
        >
      </div>
      <div class="account-links">
        <RouterLink
          v-for="link in [
            ['orders', '我的订单', 'Package'],
            ['account/security', '账户安全与实名认证', 'Shield'],
            ['addresses', '地址管理', 'MapPin'],
            ['share', '分享与佣金', 'Share2'],
            ['withdraw', '佣金提现', 'Wallet'],
            ['awards', '月度奖励', 'Gift'],
          ]"
          :key="link[0]"
          :to="'/' + link[0]"
          ><Icon :name="link[2]" /><span>{{ link[1] }}</span
          ><Icon name="ChevronRight" :size="16"
        /></RouterLink>
      </div>
      <div class="panel">
        <h3>资产明细</h3>
        <label class="input-label">查找明细<input :value="records.search.value" aria-label="搜索资产明细" @input="records.setSearch(($event.target as HTMLInputElement).value)" /></label>
        <select :value="records.asset.value" aria-label="资产类型" @change="records.setAsset(($event.target as HTMLSelectElement).value)"><option value="">全部资产</option><option v-for="asset in ['points', 'points_held', 'available', 'reserved', 'debt']" :key="asset" :value="asset">{{ label(asset) }}</option></select>
        <ResourceState :loading="records.loading.value" :error="records.error.value" @retry="records.load" />
        <div v-for="l in (records.error.value ? [] : ledger)" :key="l.id" class="history-row">
          <div>
            <strong>{{ l.note }}</strong>
            <p>{{ label(l.asset) }} · {{ date(l.created_at) }}</p>
          </div>
          <span :class="{ green: l.amount > 0 }"
            >{{ l.amount > 0 ? "+" : ""
            }}{{
              l.asset.startsWith("points") ? l.amount : money(l.amount)
            }}</span
          >
        </div>
        <PageNavigation :records="records" label="资产" />
        <p v-if="!records.loading.value && !records.error.value && !ledger.length" class="muted">暂无资产变动记录。</p>
      </div>
      <button
        class="text-link muted"
        @click="
          run(async () => {
            await auth.logout('member');

            router.push('/');
          })
        "
      >
        <Icon name="LogOut" :size="16" /> 退出登录
      </button>
    </section></template
  ><NameDialog v-model="nameVisible" />
</template>
