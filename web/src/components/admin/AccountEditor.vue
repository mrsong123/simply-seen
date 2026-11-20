<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "@/api/admin";
import AdminModal from "./AdminModal.vue";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
const emit = defineEmits<{ close: []; saved: [] }>();
const form = ref({ username: "", password: "", role: "OPERATOR" });
const { busy, execute } = useAdminMutation();
async function save() {
  if (await execute(() => adminApi.createAccount(form.value))) emit("saved");
}
</script>
<template>
  <AdminModal title="新增管理账号" @close="emit('close')"
    ><form @submit.prevent="save">
      <label class="input-label"
        >账号<input
          v-model="form.username"
          required
          pattern="[a-zA-Z0-9_-]{3,40}"
          autocomplete="off" /></label
      ><label class="input-label"
        >初始密码（至少12位）<input
          v-model="form.password"
          type="password"
          minlength="12"
          required
          autocomplete="new-password" /></label
      ><label class="input-label"
        >角色<select v-model="form.role" aria-label="角色">
          <option value="OPERATOR">运营：商品、履约、规则草稿</option>
          <option value="FINANCE">财务：审核、账本、对账</option>
          <option value="ADMIN">管理员：权限、规则审批、出款执行</option>
        </select></label
      ><button class="button dark full" :disabled="busy">创建账号</button>
    </form></AdminModal
  >
</template>
