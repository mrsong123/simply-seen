<script setup lang="ts">
import AdminModal from "./AdminModal.vue";
import { date } from "@/utils/format";
defineProps<{ record: any }>();
defineEmits<{ close: [] }>();
</script>
<template>
  <AdminModal title="操作审计详情" @close="$emit('close')"
    ><p>操作人 {{ record.actor_id }} · {{ date(record.created_at) }}</p>
    <p>{{ record.action }} · {{ record.resource }}</p>
    <pre>{{ JSON.stringify(JSON.parse(record.detail), null, 2) }}</pre>
  </AdminModal>
</template>
