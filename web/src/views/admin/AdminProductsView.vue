<script setup lang="ts">
import { ref, computed } from "vue";
import { adminApi } from "@/api/admin";
import { adminDefinitions } from "@/config/admin";
import { useAdminResource } from "@/composables/admin/useAdminResource";
import AdminPageHeader from "@/components/admin/AdminPageHeader.vue";
import AdminDataTable from "@/components/admin/AdminDataTable.vue";
import ProductEditor from "@/components/admin/ProductEditor.vue";
import CategoryEditor from "@/components/admin/CategoryEditor.vue";
import Icon from "@/components/common/Icon.vue";
const { data, loading, error, load } = useAdminResource<any[]>(() =>
  adminApi.getProducts(),
  "products",
);
const rows = computed(() => data.value || []);
const editor = ref(false),
  categoryEditor = ref(false),
  selected = ref<any>();
function edit(product?: any) {
  selected.value = product;
  editor.value = true;
}
function saved() {
  editor.value = false;
  load();
}
</script>
<template>
  <AdminPageHeader page="products" :busy="loading" @refresh="load"
    ><button class="button small" @click="categoryEditor = true">
      管理分类</button
    ><button class="button dark small" @click="edit()">
      <Icon name="Plus" :size="16" /> 新增商品
    </button></AdminPageHeader
  ><AdminDataTable
    :rows="rows"
    :columns="adminDefinitions.products.columns!"
    title="商品管理"
    :loading="loading"
    :error="error"
    @retry="load"
    icon="Package"
    ><template #cell-name="{ row }"
      ><div class="table-product">
        <img
          :src="row.image"
          :alt="row.name"
          :style="{ background: row.color }"
        />
        <div>
          <strong>{{ row.name }}</strong
          ><small>{{ row.skus?.length }} 个规格</small>
        </div>
      </div></template
    ><template #actions="{ row }"
      ><button class="text-link" @click="edit(row)">编辑</button></template
    ></AdminDataTable
  ><ProductEditor
    v-if="editor"
    :product="selected"
    @close="editor = false"
    @saved="saved"
    @manage-categories="editor = false; categoryEditor = true"
  /><CategoryEditor
    v-if="categoryEditor"
    @close="categoryEditor = false"
    @saved="load"
  />
</template>
