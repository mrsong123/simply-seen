<script setup lang="ts">
import { ref, watch } from "vue";
import ModalPanel from "./ModalPanel.vue";
import QuantityStepper from "./QuantityStepper.vue";
import { tradeApi } from "../api/trade";
import { useTask } from "../utils/useTask";
import { requestKey } from "../utils/idempotency";
import type { Order } from "../types/models";
const props = defineProps<{ open: boolean; order: Order }>();
const emit = defineEmits<{
  (event: "close"): void;
  (event: "submitted"): void;
}>();
defineOptions({ options: { styleIsolation: "apply-shared" } });
const reason = ref(""),
  lines = ref<
    {
      id: number;
      name: string;
      quantity: number;
      max: number;
      selected: boolean;
    }[]
  >([]);
const { busy, run } = useTask();
let idempotency = requestKey();
watch(
  () => props.open,
  (open) => {
    if (!open) return;
    reason.value = "";
    idempotency = requestKey();
    lines.value = props.order.items
      .filter((item) => item.quantity > item.refunded_quantity)
      .map((item) => ({
        id: item.id,
        name: item.name,
        quantity: item.quantity - item.refunded_quantity,
        max: item.quantity - item.refunded_quantity,
        selected: true,
      }));
  },
);
async function submit() {
  await run(async () => {
    if (reason.value.trim().length < 2) throw new Error("退款原因至少2个字");
    const selected = lines.value.filter((item) => item.selected);
    if (!selected.length) throw new Error("请选择退款商品");
    await tradeApi.refund(
      {
        orderNo: props.order.id,
        reason: reason.value.trim(),
        lines: selected.map((item) => ({
          orderItemId: item.id,
          quantity: item.quantity,
        })),
      },
      idempotency,
    );
    emit("submitted");
    emit("close");
    uni.showToast({ title: "申请已提交", icon: "success" });
  });
}
</script>
<template>
  <ModalPanel :open="open" title="申请售后退款" @close="emit('close')"
    ><view v-for="item in lines" :key="item.id" class="refund-line"
      ><text @tap="item.selected = !item.selected"
        >{{ item.selected ? "☑" : "□" }} {{ item.name }}</text
      ><QuantityStepper
        v-model="item.quantity"
        :min="1"
        :max="item.max"
        :disabled="busy || !item.selected" /></view
    ><view class="field"
      ><text>退款原因</text><textarea v-model="reason" maxlength="240" /></view
    ><text class="caption">按原始订单分摊计算，提交不代表退款成功。</text
    ><button class="primary" :disabled="busy" @tap="submit">
      提交申请
    </button></ModalPanel
  >
</template>
