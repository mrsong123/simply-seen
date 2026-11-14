<script setup lang="ts">
import BaseModal from "@/components/common/BaseModal.vue";
import Icon from "@/components/common/Icon.vue";
import { useConfigStore } from "@/store/config";
const config = useConfigStore();
defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
</script>
<template>
  <BaseModal
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    ><h2>积分，把心意留住</h2>
    <p>
      每满100元合格商品现金消费赠
      {{ config.rules.pointsPer100Yuan }}
      积分，按单计算。运费与积分抵扣部分不再赠送积分。
    </p>
    <p>
      1积分在参与商品限额内抵1元，不能提现或转让。未付订单预占积分，取消后原路释放；退款按订单快照退回及回收积分。
    </p>
    <RouterLink
      to="/legal/terms"
      class="text-link"
      @click="emit('update:modelValue', false)"
      >查看完整交易规则 <Icon name="ArrowRight" :size="16" /></RouterLink
  ></BaseModal>
</template>
