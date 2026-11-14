<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import { ruleFields } from "@/config/admin";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
import AdminModal from "./AdminModal.vue";
const props = defineProps<{ rule?: any }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const form = ref<any>(
  props.rule
    ? JSON.parse(JSON.stringify(props.rule))
    : {
        level1Bps: 500,
        level2Bps: 200,
        marginSharePercent: 30,
        settlementDays: 7,
        pointsPer100Yuan: 10,
        freightFen: 600,
        freeShippingFen: 19900,
        commissionBudgetFen: 1000000,
        monthlyPoolBps: 1000,
        monthlyBudgetFen: 100000,
        monthlyCapFen: 50000,
        distributionEnabled: false,
      },
);
const { busy, execute } = useAdminMutation();
async function save() {
  if (await execute(() => adminApi.createRule(form.value))) emit("saved");
}
</script>
<template>
  <AdminModal title="创建规则草稿" wide @close="emit('close')"
    ><p class="notice">保存后由另一名管理员审批生效，历史订单保留原版本。</p>
    <form @submit.prevent="save">
      <div class="form-grid">
        <label v-for="field in ruleFields" :key="field[0]" class="input-label"
          >{{ field[1]
          }}<input
            v-model.number="form[field[0]]"
            required
            type="number"
            min="0"
        /></label>
      </div>
      <label class="checkbox-label"
        ><input v-model="form.distributionEnabled" type="checkbox" />
        启用真实成交两级分销（须环境允许）</label
      ><button class="button dark" :disabled="busy">保存待复核草稿</button>
    </form></AdminModal
  >
</template>
