<script setup lang="ts">
import { computed } from "vue";
import AdminModal from "./AdminModal.vue";
import { ruleFields } from "@/config/admin";
const props = defineProps<{ rule: any }>();
defineEmits<{ close: [] }>();
const config = computed(() =>
  typeof props.rule.config === "string"
    ? JSON.parse(props.rule.config)
    : props.rule.config,
);
</script>
<template>
  <AdminModal :title="'规则版本 ' + rule.id" wide @close="$emit('close')"
    ><div v-for="field in ruleFields" :key="field[0]" class="history-row">
      <span>{{ field[1] }}</span
      ><strong>{{ config[field[0]] }}</strong>
    </div>
    <div class="history-row">
      <span>分销计佣</span
      ><strong>{{ config.distributionEnabled ? "启用" : "关闭" }}</strong>
    </div></AdminModal
  >
</template>
