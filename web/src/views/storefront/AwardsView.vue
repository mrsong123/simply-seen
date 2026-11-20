<script setup lang="ts">
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import PageNavigation from "@/components/common/PageNavigation.vue";
import ResourceState from "@/components/common/ResourceState.vue";
import { useMemberRecords } from "@/composables/useMemberRecords";
import { date, money } from "@/utils/format";
const records = useMemberRecords<any>("awards");
</script>
<template>
  <AccountHeader title="月度奖励" />
  <section class="section narrow">
    <RouterLink to="/account" class="back-link">返回我的账户</RouterLink>
    <div class="panel">
      <h2>每一份认可，都有据可查。</h2>
      <p class="notice">仅展示已复核入账的月度权益；候选或待审批金额不会提前计入余额。</p>
      <label class="input-label">查找奖励月份<input :value="records.search.value" aria-label="搜索月度奖励" placeholder="例如 2026-11" @input="records.setSearch(($event.target as HTMLInputElement).value)" /></label>
      <ResourceState :loading="records.loading.value" :error="records.error.value" @retry="records.load" />
      <template v-if="!records.loading.value && !records.error.value">
        <div v-for="award in records.items.value" :key="award.id" class="history-row">
          <div><strong>{{ award.period }} 月度奖励</strong><p>第 {{ award.rank_no }} 名 · 有效业绩 ¥{{ money(award.sales) }}</p><small>{{ date(award.created_at) }}</small></div>
          <strong>¥{{ money(award.amount) }}</strong>
        </div>
        <p v-if="!records.items.value.length" class="muted">暂无已核准的月度奖励。</p>
      </template>
      <PageNavigation :records="records" label="奖励" />
    </div>
    <RouterLink to="/withdraw" class="text-link">查看佣金余额与提现</RouterLink>
  </section>
</template>
