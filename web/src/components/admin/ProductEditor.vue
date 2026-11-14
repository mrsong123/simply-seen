<script setup lang="ts">
import { ref, onMounted } from "vue";
import Icon from "@/components/common/Icon.vue";
import AdminModal from "./AdminModal.vue";
import { adminApi } from "@/api/admin";
import { catalogApi } from "@/api/catalog";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const props = defineProps<{ product?: any }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const categories = ref<any[]>([]);
const form = ref<any>(
  props.product
    ? JSON.parse(JSON.stringify(props.product))
    : {
        name: "",
        subtitle: "",
        description: "",
        image: "/art/cup.svg",
        color: "#e8e5df",
        category_id: 0,
        status: "OFF_SALE",
        featured: false,
        points_percent: 20,
        commission_percent: 7,
        skus: [
          { name: "标准款", code: "", price: 10000, cost: 5000, stock: 0 },
        ],
      },
);
const { busy, execute } = useAdminMutation();
onMounted(async () => {
  try {
    categories.value = await catalogApi.getCategories();
    if (!form.value.category_id)
      form.value.category_id = categories.value[0]?.id || 0;
  } catch {}
});
async function save() {
  if (await execute(() => adminApi.saveProduct(form.value))) emit("saved");
}
</script>
<template>
  <AdminModal
    :title="form.id ? '编辑商品' : '新增好物'"
    wide
    @close="emit('close')"
    ><form @submit.prevent="save">
      <div class="form-grid">
        <label class="input-label"
          >商品名称<input v-model="form.name" required maxlength="120" /></label
        ><label class="input-label"
          >商品分类<select v-model="form.category_id">
            <option
              v-for="category in categories"
              :key="category.id"
              :value="category.id"
            >
              {{ category.name }}
            </option>
          </select></label
        ><label class="input-label"
          >副标题<input v-model="form.subtitle" maxlength="240" /></label
        ><label class="input-label"
          >商品状态<select v-model="form.status">
            <option value="ON_SALE">销售中</option>
            <option value="OFF_SALE">已下架</option>
          </select></label
        ><label class="input-label"
          >商品图片（HTTPS URL或本地素材）<input
            v-model="form.image"
            required /></label
        ><label class="input-label"
          >背景颜色<input v-model="form.color" type="color" /></label
        ><label class="input-label"
          >积分抵扣上限（%）<input
            v-model.number="form.points_percent"
            type="number"
            min="0"
            max="100"
            required /></label
        ><label class="input-label"
          >佣金总比例上限（%）<input
            v-model.number="form.commission_percent"
            type="number"
            min="0"
            max="10"
            required
        /></label>
      </div>
      <label class="input-label"
        >商品详情<textarea
          v-model="form.description"
          rows="5"
          required
        ></textarea></label
      ><label class="checkbox-label"
        ><input v-model="form.featured" type="checkbox" /> 首页精选</label
      >
      <h3>规格与库存</h3>
      <p class="caption left">
        金额单位为分。修改总库存不可低于正在预占的数量。
      </p>
      <div
        v-for="(sku, index) in form.skus"
        :key="sku.id || index"
        class="sku-form"
      >
        <label>规格名称<input v-model="sku.name" required /></label
        ><label>SKU编码<input v-model="sku.code" required /></label
        ><label
          >售价（分）<input
            v-model.number="sku.price"
            type="number"
            min="1"
            required /></label
        ><label
          >成本（分）<input
            v-model.number="sku.cost"
            type="number"
            min="0"
            required /></label
        ><label
          >总库存<input
            v-model.number="sku.stock"
            type="number"
            min="0"
            required
        /></label>
      </div>
      <button
        type="button"
        class="text-link"
        :disabled="form.skus.length >= 20"
        @click="
          form.skus.push({
            name: '',
            code: '',
            price: 10000,
            cost: 0,
            stock: 0,
          })
        "
      >
        <Icon name="Plus" :size="15" /> 增加规格
      </button>
      <div class="modal-footer">
        <button class="button dark" :disabled="busy">保存商品</button>
      </div>
    </form></AdminModal
  >
</template>
