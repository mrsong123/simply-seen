<script setup lang="ts">
import { ref, watch } from "vue";
import BaseModal from "@/components/common/BaseModal.vue";
import { memberApi } from "@/api/member";
import { useAction } from "@/composables/useAction";
const props = defineProps<{ modelValue: boolean; address?: any }>();
const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  saved: [];
}>();
const form = ref<any>({});
const { busy, run } = useAction();
watch(
  () => props.modelValue,
  (open) => {
    if (open)
      form.value = props.address
        ? { ...props.address }
        : { name: "", phone: "", region: "", detail: "", is_default: true };
  },
  { immediate: true },
);
async function submit() {
  await run(async () => {
    await memberApi.saveAddress(form.value);
    emit("update:modelValue", false);
    emit("saved");
  });
}
</script>
<template>
  <BaseModal
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    ><h2>{{ form.id ? "编辑地址" : "添加收货地址" }}</h2>
    <form @submit.prevent="submit">
      <label class="input-label"
        >收货人<input
          v-model="form.name"
          required
          maxlength="60"
          autocomplete="name" /></label
      ><label class="input-label"
        >手机号码<input
          v-model="form.phone"
          required
          pattern="1[3-9][0-9]{9}"
          inputmode="tel"
          autocomplete="tel" /></label
      ><label class="input-label"
        >省 / 市 / 区<input
          v-model="form.region"
          required
          maxlength="160"
          placeholder="例如：浙江省 杭州市 西湖区" /></label
      ><label class="input-label"
        >详细地址<textarea
          v-model="form.detail"
          required
          maxlength="240"
          autocomplete="street-address"
        ></textarea></label
      ><label class="checkbox-label"
        ><input v-model="form.is_default" type="checkbox" /> 设为默认地址</label
      ><button class="button dark full" :disabled="busy">保存地址</button>
    </form></BaseModal
  >
</template>
