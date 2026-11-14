<script setup lang="ts">
import { ref, watch } from "vue";
import BaseModal from "@/components/common/BaseModal.vue";
import { orderApi } from "@/api/order";
import { createRequestKey } from "@/utils/idempotency";
import { useAction } from "@/composables/useAction";
import { useUiStore } from "@/store/ui";
const props = defineProps<{ modelValue: boolean; order: any }>();
const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  saved: [];
}>();
const { busy, run } = useAction();
const refundForm = ref<any>({ reason: "", lines: [] });
let requestKey = createRequestKey();
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      requestKey = createRequestKey();
      refundForm.value = {
        reason: "",
        lines: props.order.items
          .filter((i: any) => i.quantity > i.refunded_quantity)
          .map((i: any) => ({
            orderItemId: i.id,
            name: i.name,
            quantity: i.quantity - i.refunded_quantity,
            max: i.quantity - i.refunded_quantity,
            selected: true,
          })),
      };
    }
  },
);
async function refund() {
  await run(async () => {
    await orderApi.createRefund(
      {
        orderNo: props.order.id,
        reason: refundForm.value.reason,
        lines: refundForm.value.lines
          .filter((i: any) => i.selected)
          .map((i: any) => ({
            orderItemId: i.orderItemId,
            quantity: Number(i.quantity),
          })),
      },
      requestKey,
    );
    emit("update:modelValue", false);
    emit("saved");
    useUiStore().message("售后申请已提交");
  });
}
</script>
<template>
  <BaseModal
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    ><h2>申请售后退款</h2>
    <form @submit.prevent="refund">
      <div
        v-for="i in refundForm.lines"
        :key="i.orderItemId"
        class="refund-line"
      >
        <label class="checkbox-label"
          ><input v-model="i.selected" type="checkbox" />{{ i.name }}</label
        ><input
          v-model.number="i.quantity"
          type="number"
          min="1"
          :max="i.max"
          :disabled="!i.selected"
        />
      </div>
      <label class="input-label"
        >退款原因<textarea
          v-model="refundForm.reason"
          required
          minlength="2"
          maxlength="240"
        ></textarea>
      </label>
      <p class="caption left">
        退款按原始订单行现金与积分分摊计算；提交后需审核，渠道成功后才会入账。
      </p>
      <button class="button dark full" :disabled="busy">提交申请</button>
    </form></BaseModal
  >
</template>
