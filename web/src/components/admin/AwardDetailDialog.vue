<script setup lang="ts">
import AdminModal from "./AdminModal.vue";
import { money, label } from "@/utils/format";
defineProps<{ settlement: any }>();
defineEmits<{ close: [] }>();
</script>
<template>
  <AdminModal
    :title="settlement.period + ' 候选明细'"
    wide
    @close="$emit('close')"
    ><p>预算 ¥{{ money(settlement.budget) }} · {{ label(settlement.state) }}</p>
    <div v-for="award in settlement.awards" :key="award.id" class="history-row">
      <span
        >第 {{ award.rank_no }} 名 · 会员 {{ award.member_id
        }}<small>有效业绩 ¥{{ money(award.sales) }}</small></span
      ><strong>¥{{ money(award.amount) }}</strong>
    </div>
    <p v-if="!settlement.awards.length" class="muted">
      本月无合格候选或可用预算。
    </p></AdminModal
  >
</template>
