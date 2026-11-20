<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/auth";
import { useAdminMutation } from "@/composables/admin/useAdminMutation";
import AdminModal from "./AdminModal.vue";
const props = defineProps<{ accountId?: number | string }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const auth = useAuthStore(),
  router = useRouter();
const form = ref({ currentPassword: "", password: "" });
const { busy, execute } = useAdminMutation();
async function save() {
  if (props.accountId) {
    if (
      await execute(() =>
        adminApi.resetAccountPassword(props.accountId!, {
          password: form.value.password,
        }),
      )
    )
      emit("saved");
    return;
  }
  if (
    await execute(
      () => adminApi.updatePassword(form.value),
      "密码已更新，请重新登录",
    )
  ) {
    auth.clearSession("admin");
    emit("close");
    router.replace("/admin/login");
  }
}
</script>
<template>
  <AdminModal
    :title="accountId ? '重置其他账号密码' : '更新登录密码'"
    @close="emit('close')"
    ><form @submit.prevent="save">
      <label v-if="!accountId" class="input-label"
        >原密码<input
          v-model="form.currentPassword"
          type="password"
          required
          autocomplete="current-password" /></label
      ><label class="input-label"
        >新密码（至少12位）<input
          v-model="form.password"
          type="password"
          minlength="12"
          required
          autocomplete="new-password"
      /></label>
      <p v-if="accountId" class="notice">
        保存后撤销该账号的所有会话，请通过安全渠道交付新密码。
      </p>
      <button class="button dark full" :disabled="busy">
        {{ accountId ? "重置密码" : "更新密码并退出" }}
      </button>
    </form></AdminModal
  >
</template>
