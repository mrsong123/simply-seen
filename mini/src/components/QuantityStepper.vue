<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue: number;
    min?: number;
    max?: number;
    disabled?: boolean;
  }>(),
  { min: 0, max: 99 },
);
const emit = defineEmits<{
  (event: "update:modelValue", value: number): void;
}>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
function change(delta: number) {
  if (!props.disabled)
    emit(
      "update:modelValue",
      Math.max(props.min, Math.min(props.max, props.modelValue + delta)),
    );
}
</script>
<template>
  <view class="stepper"
    ><text @tap="change(-1)">−</text><text>{{ modelValue }}</text
    ><text @tap="change(1)">＋</text></view
  >
</template>
