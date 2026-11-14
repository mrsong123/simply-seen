<script setup lang="ts">
import { computed, ref, watch } from "vue";
import Icon from "@/components/common/Icon.vue";
import type { AdminColumn } from "@/config/admin";
import { label } from "@/utils/format";
import {
  exportAdminCsv,
  formatAdminCell,
} from "@/composables/admin/tableFormat";
const props = withDefaults(
  defineProps<{
    rows: any[];
    columns: AdminColumn[];
    title: string;
    loading?: boolean;
    icon?: string;
  }>(),
  { loading: false, icon: "FileText" },
);
const query = ref(""),
  status = ref(""),
  current = ref(1);
const states = computed(() => [
  ...new Set(props.rows.map((row) => row.state || row.status).filter(Boolean)),
]);
const filtered = computed(() =>
  props.rows.filter(
    (row) =>
      (!query.value ||
        JSON.stringify(row)
          .toLowerCase()
          .includes(query.value.toLowerCase())) &&
      (!status.value ||
        row.state === status.value ||
        row.status === status.value),
  ),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const visible = computed(() =>
  filtered.value.slice((current.value - 1) * 10, current.value * 10),
);
watch([query, status], () => (current.value = 1));
watch(
  () => props.rows,
  () => (current.value = Math.min(current.value, pages.value)),
);
</script>
<template>
  <div class="data-panel">
    <div class="table-toolbar">
      <label class="search-field"
        ><Icon name="Search" :size="17" /><input
          v-model="query"
          placeholder="搜索当前记录"
          aria-label="搜索管理记录"
      /></label>
      <div>
        <select v-if="states.length" v-model="status" aria-label="按状态筛选">
          <option value="">全部状态</option>
          <option v-for="state in states" :key="String(state)" :value="state">
            {{ label(String(state)) }}
          </option></select
        ><button
          class="button small"
          @click="exportAdminCsv(title, columns, filtered)"
        >
          <Icon name="Download" :size="15" /> 导出记录
        </button>
      </div>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th v-for="column in columns" :key="column[0]">{{ column[1] }}</th>
            <th class="right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in visible" :key="row.id ?? index">
            <td v-for="column in columns" :key="column[0]">
              <slot :name="'cell-' + column[0]" :row="row"
                ><span
                  v-if="
                    ['state', 'status', 'payment_state'].includes(column[0])
                  "
                  class="badge"
                  :class="{
                    neutral: [
                      'DRAFT',
                      'CLOSED',
                      'REFUNDED',
                      'ARCHIVED',
                    ].includes(row[column[0]]),
                    warning: [
                      'UNKNOWN',
                      'ABNORMAL',
                      'OPEN',
                      'REVIEWING',
                    ].includes(row[column[0]]),
                  }"
                  >{{ formatAdminCell(row, column[0]) }}</span
                ><span
                  v-else
                  :class="{
                    mono: column[0] === 'id' || column[0].endsWith('_id'),
                    'truncate-cell': ['detail', 'reason'].includes(column[0]),
                  }"
                  :title="String(row[column[0]] ?? '')"
                  >{{ formatAdminCell(row, column[0]) }}</span
                ></slot
              >
            </td>
            <td class="table-actions"><slot name="actions" :row="row" /></td>
          </tr>
        </tbody>
      </table>
      <div v-if="!loading && !filtered.length" class="empty-state compact">
        <Icon :name="icon" :size="32" />
        <h3>暂无{{ query ? "匹配" : "" }}记录</h3>
        <p>真实业务发生后，记录将在这里呈现。</p>
      </div>
    </div>
    <div class="table-footer">
      <span>当前加载 {{ rows.length }} 条，匹配 {{ filtered.length }} 条</span>
      <div>
        <button
          class="icon-button"
          :disabled="current <= 1"
          aria-label="上一页"
          @click="current--"
        >
          <Icon name="ArrowLeft" :size="16" /></button
        ><span>{{ current }} / {{ pages }}</span
        ><button
          class="icon-button"
          :disabled="current >= pages"
          aria-label="下一页"
          @click="current++"
        >
          <Icon name="ArrowRight" :size="16" />
        </button>
      </div>
    </div>
  </div>
</template>
