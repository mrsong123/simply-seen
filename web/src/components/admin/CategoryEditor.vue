<script setup lang="ts">
import { ref, onMounted } from "vue";
import { adminApi } from "@/api/admin";
import { catalogApi } from "@/api/catalog";
import AdminModal from "./AdminModal.vue";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const emit = defineEmits<{ close: []; saved: [] }>();
const categories = ref<any[]>([]);
const form = ref<any>({ name: "", sort_no: 0 });
const { busy, execute } = useAdminMutation();
async function load() {
  try {
    categories.value = await catalogApi.getCategories();
  } catch {}
}
onMounted(load);
async function save() {
  if (await execute(() => adminApi.saveCategory(form.value))) {
    await load();
    form.value = { name: "", sort_no: 0 };
    emit("saved");
  }
}
</script>
<template>
  <AdminModal title="商品分类" @close="emit('close')"
    ><div v-for="category in categories" :key="category.id" class="history-row">
      <span>{{ category.name }}</span
      ><button class="text-link" @click="form = { ...category }">编辑</button>
    </div>
    <form @submit.prevent="save">
      <label class="input-label"
        >{{ form.id ? "修改分类" : "新增分类"
        }}<input v-model="form.name" required maxlength="60" /></label
      ><label class="input-label"
        >排序<input v-model.number="form.sort_no" type="number" /></label
      ><button class="button dark" :disabled="busy">保存分类</button
      ><button
        type="button"
        class="button"
        @click="form = { name: '', sort_no: 0 }"
      >
        新建分类
      </button>
    </form></AdminModal
  >
</template>
