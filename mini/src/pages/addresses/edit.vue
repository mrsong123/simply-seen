<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import PageShell from "../../components/PageShell.vue";
import PageHeading from "../../components/PageHeading.vue";
import PageLoadState from "../../components/PageLoadState.vue";
import { addressApi } from "../../api/address";
import { useTask } from "../../utils/useTask";
import { back, requireSession } from "../../utils/router";
import type { Address } from "../../types/models";
import { takeAddressDraft } from "../../utils/addressDraft";
const address = ref<Address>({
  name: "",
  phone: "",
  region: "",
  detail: "",
  is_default: true,
});
const { busy, run } = useTask();
const loaded = ref(false), loadError = ref(false);
let editId = 0, imported = false, validLink = true;
onLoad((options) => {
  editId = Number(options?.id || 0);
  imported = options?.imported === "1";
  validLink = Number.isSafeInteger(editId) && editId >= 0;
  if (!validLink) {
    loadError.value = true;
    return;
  }
  if (!requireSession("addressEdit", { id: editId, imported: imported ? 1 : 0 })) return;
  void reload();
});
async function reload() {
  await run(async () => {
    loaded.value = loadError.value = false;
    try {
      if (imported) {
        address.value = takeAddressDraft() || address.value;
      } else if (editId) {
        const found = (await addressApi.list()).find(
          (item) => item.id === editId,
        );
        if (!found) throw new Error("地址已删除或无权访问");
        address.value = { ...found };
      }
      loaded.value = true;
    } catch (error) {
      loadError.value = true;
      throw error;
    }
  });
}
async function save() {
  await run(async () => {
    if (!loaded.value) throw new Error("请先完成地址读取，再保存修改");
    const value = address.value;
    if (
      !value.name.trim() ||
      !/^1[3-9]\d{9}$/.test(value.phone) ||
      !value.region.trim() ||
      !value.detail.trim()
    )
      throw new Error("请完整填写收货人、有效手机号、地区和地址");
    await addressApi.save(value);
    uni.showToast({ title: "地址已保存", icon: "success" });
    back();
  });
}
</script>
<template>
  <PageShell
    ><PageHeading :title="editId ? '编辑收货地址' : '新增收货地址'" />
    <PageLoadState :loading="busy && !loaded" :failed="loadError" :retryable="validLink" @retry="reload" />
    <view v-if="loadError" class="box"><button class="text-button" @tap="back">返回地址列表</button></view>
    <view
      class="box"
      ><view class="field"
        ><text>收货人</text
        ><input v-model="address.name" maxlength="60" /></view
      ><view class="field"
        ><text>手机号</text
        ><input v-model="address.phone" type="number" maxlength="11" /></view
      ><view class="field"
        ><text>省市区</text
        ><input v-model="address.region" maxlength="160" /></view
      ><view class="field"
        ><text>详细地址</text
        ><textarea v-model="address.detail" maxlength="240" /></view
      ><view class="checkbox" @tap="address.is_default = !address.is_default"
        >{{ address.is_default ? "☑" : "□" }} 设为默认地址</view
      ><button class="primary" :disabled="busy || !loaded" @tap="save">
        保存地址
      </button></view
    ></PageShell
  >
</template>
