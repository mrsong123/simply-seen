import { defineStore } from "pinia";
import { catalogApi } from "@/api/catalog";
export const useConfigStore = defineStore("config", {
  state: () => ({
    config: {} as Record<string, any>,
    rules: {} as Record<string, any>,
    categories: [] as any[],
    loaded: false,
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return;
      const [config, rules, categories] = await Promise.all([
        catalogApi.getConfig(),
        catalogApi.getRules(),
        catalogApi.getCategories(),
      ]);
      this.config = config;
      this.rules = rules;
      this.categories = categories;
      this.loaded = true;
    },
  },
});
