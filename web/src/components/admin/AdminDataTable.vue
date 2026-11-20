<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import { adminRecordsKey } from "@/composables/admin/useAdminRecords";
import ResourceState from "@/components/common/ResourceState.vue";
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
    error?: string | boolean;
    icon?: string;
  }>(),
  { loading: false, error: false, icon: "FileText" },
);
defineEmits<{ retry: [] }>();
const records = inject(adminRecordsKey, null);
const localQuery = ref(""), localStatus = ref(""), localPage = ref(1);
const query = computed({ get: () => records?.search.value ?? localQuery.value, set: (value: string) => { if (records) records.setSearch(value); else localQuery.value = value; } });
const status = computed({ get: () => records?.state.value ?? localStatus.value, set: (value: string) => { if (records) records.setState(value); else localStatus.value = value; } });
const current = computed({ get: () => records?.page.value ?? localPage.value, set: (value: number) => { if (records) records.setPage(value); else localPage.value = value; } });
const states = computed(() => records ? records.states.value : [...new Set(props.rows.map(row => row.state || row.status).filter(Boolean))]);
const filtered = computed(() => records ? props.rows : props.rows.filter(row => (!query.value || JSON.stringify(row).toLowerCase().includes(query.value.toLowerCase())) && (!status.value || row.state === status.value || row.status === status.value)));
const pages = computed(() => records ? records.totalPages.value : Math.max(1, Math.ceil(filtered.value.length / 10)));
const visible = computed(() => records ? filtered.value : filtered.value.slice((current.value - 1) * 10, current.value * 10));
watch([localQuery, localStatus], () => { localPage.value = 1; });
watch(() => props.rows, () => { if (!records) localPage.value = Math.min(localPage.value, pages.value); });
</script>
<template>
  <div class="data-panel">
    <div class="table-toolbar">
      <label class="search-field"
        ><Icon name="Search" :size="17" /><input
          v-model="query"
          :placeholder="records ? '搜索全部记录' : '搜索当前记录'"
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
          :disabled="loading || !!error"
          @click="exportAdminCsv(title + '-当前页' + current, columns, filtered)"
        >
          <Icon name="Download" :size="15" /> 导出当前页
        </button>
      </div>
    </div>
    <div class="table-scroll">
      <ResourceState :loading="loading" :error="error" @retry="$emit('retry')" />
      <table v-if="!loading && !error">
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
      <div v-if="!loading && !error && !filtered.length" class="empty-state compact">
        <Icon :name="icon" :size="32" />
        <h3>暂无{{ query ? "匹配" : "" }}记录</h3>
        <p>真实业务发生后，记录将在这里呈现。</p>
      </div>
    </div>
    <div class="table-footer">
      <span v-if="records">共 {{ records.total.value }} 条，当前页 {{ rows.length }} 条</span>
      <span v-else>当前加载 {{ rows.length }} 条，匹配 {{ filtered.length }} 条</span>
      <div>
        <select v-if="records" :value="records.size.value" aria-label="每页记录数" :disabled="loading" @change="records.setSize(Number(($event.target as HTMLSelectElement).value))"><option :value="10">每页10条</option><option :value="20">每页20条</option><option :value="50">每页50条</option></select>
        <button
          class="icon-button"
          :disabled="loading || current <= 1"
          aria-label="上一页"
          @click="current--"
        >
          <Icon name="ArrowLeft" :size="16" /></button
        ><span>{{ current }} / {{ pages }}</span
        ><button
          class="icon-button"
          :disabled="loading || current >= pages"
          aria-label="下一页"
          @click="current++"
        >
          <Icon name="ArrowRight" :size="16" />
        </button>
      </div>
    </div>
  </div>
</template>
