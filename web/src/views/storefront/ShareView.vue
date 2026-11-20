<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, computed, onMounted } from "vue";
import { memberApi } from "@/api/member";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
import { useMemberRecords } from "@/composables/useMemberRecords";
import type { MemberRecordKind } from "@/types/pagination";
import ResourceState from "@/components/common/ResourceState.vue";
import PageNavigation from "@/components/common/PageNavigation.vue";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
const auth = useAuthStore(), config = useConfigStore(), ui = useUiStore();
const profile = computed(() => auth.memberProfile || auth.member?.profile);
const tab = ref<"commissions" | "invites" | "second-invites">("commissions");
const records = useMemberRecords<any>(computed<MemberRecordKind>(() => tab.value));
const accepted = ref(false);
const { busy, run } = useAction();
const summary = records.summary;
onMounted(() => auth.fetchMemberProfile().catch(() => {}));
async function promote() {
  await run(async () => {
    await memberApi.acceptPromotion(accepted.value);
    await auth.fetchMemberProfile();
    await records.load();
    ui.message("推广规则已确认");
  });
}
async function copyInvite() {
  try {
    await navigator.clipboard.writeText((config.config.shareBase || location.origin).replace(/\/$/, "") + "/?invite=" + profile.value.invite_code);
    ui.message("邀请链接已复制");
  } catch { ui.message("邀请码：" + profile.value.invite_code); }
}
</script>
<template>
  <AccountHeader title="好物，值得分享。" />
  <section v-if="profile" class="section narrow">
    <div class="share-card">
      <div class="eyebrow">GOOD THINGS ARE BETTER SHARED</div>
      <h2>分享喜欢，收获心意。</h2>
      <p>推荐真实好物，在有效成交后获得佣金。<br />不收取入门费用，不因邀请注册获得现金奖励。</p>
      <template v-if="profile.promoter">
        <div class="invite-code">{{ profile.invite_code }}</div>
        <button class="button dark" @click="copyInvite"><Icon name="Copy" :size="16" /> 复制邀请链接</button>
      </template>
      <template v-else-if="config.rules.distributionEnabled">
        <label class="checkbox-label"><input type="checkbox" v-model="accepted" /><span>我已阅读<RouterLink to="/legal/distribution">《推广服务规则》</RouterLink></span></label>
        <button class="button dark" :disabled="!accepted || busy" @click="promote">成为好物分享者</button>
      </template>
      <p v-else class="notice">分享服务当前尚未开放，历史邀请和佣金记录仍可查看。</p>
    </div>
    <div v-if="records.loaded.value && !records.error.value" class="asset-grid">
      <div class="asset-card"><span>直接邀请</span><strong>{{ summary.level1Count || 0 }}</strong><small>位朋友 · 全部记录</small></div>
      <div class="asset-card"><span>二级归因</span><strong>{{ summary.level2Count || 0 }}</strong><small>位朋友 · 全部记录</small></div>
      <div class="asset-card"><span>待结算佣金</span><strong>¥{{ money(summary.pendingAmount || 0) }}</strong><small>以有效成交及售后结果为准</small></div>
    </div>
    <div class="category-pills">
      <button :class="{ active: tab === 'commissions' }" @click="tab = 'commissions'">成交佣金明细</button>
      <button :class="{ active: tab === 'invites' }" @click="tab = 'invites'">我的直接邀请</button>
      <button :class="{ active: tab === 'second-invites' }" @click="tab = 'second-invites'">二级邀请记录</button>
    </div>
    <div class="panel">
      <label class="input-label">搜索记录<input :value="records.search.value" aria-label="搜索推广记录" @input="records.setSearch(($event.target as HTMLInputElement).value)" /></label>
      <select v-if="records.states.value.length" :value="records.state.value" aria-label="佣金状态" @change="records.setState(($event.target as HTMLSelectElement).value)"><option value="">全部状态</option><option v-for="state in records.states.value" :key="state" :value="state">{{ label(state) }}</option></select>
      <ResourceState :loading="records.loading.value" :error="records.error.value" @retry="records.load" />
      <template v-if="!records.loading.value && !records.error.value">
        <div v-for="row in records.items.value" :key="row.id" class="history-row">
          <template v-if="tab === 'commissions'">
            <div><strong>{{ row.level_no === 1 ? '一级' : '二级' }}成交佣金 <span class="badge">{{ label(row.state) }}</span></strong><p>{{ row.order_id }} · {{ date(row.created_at) }}</p></div><span>¥{{ money(row.amount) }}</span>
          </template>
          <template v-else><span>{{ row.name }}</span><small>{{ date(row.created_at) }}</small></template>
        </div>
        <p v-if="!records.items.value.length" class="muted">暂无匹配记录。</p>
      </template>
      <PageNavigation :records="records" label="推广" />
    </div>
  </section>
</template>
