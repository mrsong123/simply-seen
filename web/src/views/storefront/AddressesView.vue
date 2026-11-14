<script setup lang="ts">
import Icon from "@/components/common/Icon.vue";
import { money, date, label } from "@/utils/format";
import { ref, onMounted } from "vue";
import { memberApi } from "@/api/member";
import { useAction } from "@/composables/useAction";
import AccountHeader from "@/components/storefront/AccountHeader.vue";
import AddressDialog from "@/components/storefront/AddressDialog.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
const addresses = ref<any[]>([]),
  addressForm = ref<any>(null),
  modal = ref(""),
  addressVisible = ref(false);
const { busy, run } = useAction();
async function load() {
  addresses.value = await memberApi.getAddresses();
}
onMounted(() => load().catch(() => {}));
function openAddress(a?: any) {
  addressForm.value = a || null;
  addressVisible.value = true;
}
async function removeAddress() {
  await run(async () => {
    await memberApi.deleteAddress(addressForm.value.id);
    modal.value = "";
    await load();
  });
}
</script>
<template>
  <AccountHeader title="收货地址" />
  <section class="section narrow">
    <div class="panel-title">
      <p class="muted">每一个地址，都是生活的坐标。</p>
      <button class="button dark" @click="openAddress()">
        <Icon name="Plus" :size="17" /> 新增地址
      </button>
    </div>
    <div v-for="a in addresses" :key="a.id" class="panel address-card">
      <div>
        <h3>
          {{ a.name }} <span class="muted">{{ a.phone }}</span>
          <span v-if="a.is_default" class="badge">默认</span>
        </h3>
        <p>{{ a.region }} {{ a.detail }}</p>
      </div>
      <div class="button-row">
        <button class="text-link" @click="openAddress(a)">编辑</button
        ><button
          class="text-link muted"
          @click="
            addressForm = a;
            modal = 'deleteAddress';
          "
        >
          删除
        </button>
      </div>
    </div>
    <div v-if="!addresses.length" class="empty-state">
      <Icon name="MapPin" :size="40" />
      <h3>还没有收货地址</h3>
      <p>添加地址，让美好准确抵达。</p>
    </div>
  </section>
  <AddressDialog
    v-model="addressVisible"
    :address="addressForm"
    @saved="load"
  /><ConfirmDialog
    :model-value="modal === 'deleteAddress'"
    title="删除这个地址？"
    description="已创建订单的收货快照会保留。"
    confirm-label="确认删除"
    :busy="busy"
    @update:model-value="modal = ''"
    @confirm="removeAddress"
  />
</template>
