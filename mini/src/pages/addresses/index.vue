<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import { addressApi } from "../../api/address";
import { useTask, confirm } from "../../utils/useTask";
import { navigate, requireSession } from "../../utils/router";
import { chooseShippingAddress } from "../../utils/wechat";
import type { Address } from "../../types/models";
const addresses = ref<Address[]>([]);
const { busy, run } = useTask();
async function reload() {
  if (!requireSession("addresses")) return;
  await run(async () => (addresses.value = await addressApi.list()));
}
onShow(reload);
async function remove(address: Address) {
  if (
    !address.id ||
    !(await confirm("已创建订单的地址快照会保留。", "删除这个地址？"))
  )
    return;
  await run(async () => {
    await addressApi.remove(address.id!);
    addresses.value = await addressApi.list();
  });
}
async function importAddress() {
  await run(async () => {
    const address = await chooseShippingAddress();
    uni.setStorageSync("sj-address-draft", address);
    navigate("addressEdit", { imported: 1 });
  });
}
</script>
<template>
  <PageShell
    ><PageHeading title="收货地址" /><view
      v-for="address in addresses"
      :key="address.id"
      class="box"
      ><view class="row"
        ><text>{{ address.name }} · {{ address.phone }}</text
        ><text v-if="address.is_default" class="badge">默认</text></view
      ><text class="sub">{{ address.region }} {{ address.detail }}</text
      ><view class="row"
        ><text
          class="link"
          @tap="navigate('addressEdit', { id: address.id || 0 })"
          >编辑</text
        ><text class="link" @tap="remove(address)">删除</text></view
      ></view
    ><view v-if="!addresses.length" class="empty">还没有收货地址</view
    ><view class="box"
      ><button class="primary" :disabled="busy" @tap="navigate('addressEdit')">
        手动添加地址</button
      ><button class="secondary" :disabled="busy" @tap="importAddress">
        导入微信收货地址
      </button></view
    ></PageShell
  >
</template>
