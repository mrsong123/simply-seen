import { defineStore } from "pinia";
import { catalogApi } from "@/api/catalog";
const pending = new WeakMap<object, Promise<void>>();
export const useConfigStore = defineStore("config", {
  state: () => ({
    config: {} as Record<string, any>,
    rules: {} as Record<string, any>,
    categories: [] as any[],
    loaded: false,
    loading: false,
    error: "",
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return;
      const existing = pending.get(this);
      if (existing) return existing;
      this.loading = true;
      this.error = "";
      const request = Promise.resolve().then(async () => {
        try {
          const [config, rules, categories] = await Promise.all([
            catalogApi.getConfig(), catalogApi.getRules(), catalogApi.getCategories(),
          ]);
          this.config = config;
          this.rules = rules;
          this.categories = categories;
          this.loaded = true;
        } catch (failure) {
          this.error = failure instanceof Error ? failure.message : "服务配置加载失败";
          throw failure;
        } finally {
          this.loading = false;
          pending.delete(this);
        }
      });
      pending.set(this, request);
      return request;
    },
  },
});
