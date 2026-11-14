import { defineStore } from "pinia";
import { cartApi } from "@/api/cart";
export const useCartStore = defineStore("cart", {
  state: () => ({ items: [] as any[] }),
  getters: {
    count: (s) => s.items.reduce((sum, item) => sum + item.quantity, 0),
    total: (s) =>
      s.items.reduce((sum, item) => sum + item.quantity * item.price, 0),
  },
  actions: {
    async load(silent = false) {
      this.items = await cartApi.getItems(silent);
    },
    async change(sku: number, quantity: number) {
      await cartApi.updateItem(sku, Math.max(0, Math.min(99, quantity)));
      await this.load();
    },
    async add(sku: number, quantity: number) {
      const current = this.items.find((x) => x.sku_id === sku);
      await this.change(sku, quantity + (current?.quantity || 0));
    },
    clear() {
      this.items = [];
    },
  },
});
