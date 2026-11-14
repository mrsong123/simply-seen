<script setup lang="ts">
import { ref, watch } from "vue";
import BaseModal from "@/components/common/BaseModal.vue";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { useAction } from "@/composables/useAction";
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
const auth = useAuthStore(),
  nickname = ref("");
const { busy, run } = useAction();
watch(
  () => props.modelValue,
  (open) => {
    if (open) nickname.value = auth.memberProfile?.name || "";
  },
);
async function saveName() {
  await run(async () => {
    await auth.updateMember(nickname.value);
    emit("update:modelValue", false);
    useUiStore().message("昵称已更新");
  });
}
</script>
<template>
  <BaseModal
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    ><h2>怎么称呼你？</h2>
    <form @submit.prevent="saveName">
      <label class="input-label"
        >昵称<input
          v-model="nickname"
          minlength="2"
          maxlength="40"
          required /></label
      ><button class="button dark full" :disabled="busy">保存昵称</button>
    </form></BaseModal
  >
</template>
