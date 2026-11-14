import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { cartApi } from "../api/trade";
import { authStorage } from "../utils/storage";
import type { CartItem } from "../types/models";
export const useCartStore = defineStore("cart", () => {
  const items = ref<CartItem[]>([]);
  const total = computed(() =>
    items.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
  );
  const count = computed(() =>
    items.value.reduce((sum, item) => sum + item.quantity, 0),
  );
  let revision = 0;
  uni.$on("session-expired", () => {
    revision++;
    items.value = [];
  });
  async function refresh() {
    const attempt = ++revision;
    if (!authStorage.token()) {
      items.value = [];
      return;
    }
    const current = await cartApi.list();
    if (attempt === revision) items.value = current;
  }
  async function setQuantity(skuId: number, quantity: number) {
    const acceptedQuantity = Math.max(0, Math.min(99, quantity));
    await cartApi.quantity(skuId, acceptedQuantity);
    revision++;
    if (acceptedQuantity === 0)
      items.value = items.value.filter((item) => item.sku_id !== skuId);
    else {
      const item = items.value.find((item) => item.sku_id === skuId);
      if (item) item.quantity = acceptedQuantity;
    }
    // The cart write already succeeded; a supplementary reload must not turn it into failure.
    void refresh().catch(() => undefined);
  }
  async function add(skuId: number, quantity: number) {
    await refresh();
    const current =
      items.value.find((item) => item.sku_id === skuId)?.quantity || 0;
    await setQuantity(skuId, current + quantity);
  }
  return { items, total, count, refresh, setQuantity, add };
});
