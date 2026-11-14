import { defineStore } from "pinia";
import { ref } from "vue";
import { catalogApi } from "../api/catalog";
import type { AppConfig, PublicRule, Category } from "../types/models";
export const useAppStore = defineStore("app", () => {
  const config = ref<AppConfig | null>(null),
    rules = ref<PublicRule | null>(null),
    categories = ref<Category[]>([]);
  let pending: Promise<void> | undefined;
  async function load() {
    if (config.value && rules.value) return;
    if (pending) return pending;
    pending = Promise.all([
      catalogApi.config(),
      catalogApi.rules(),
      catalogApi.categories(),
    ])
      .then(([c, r, list]) => {
        config.value = c;
        rules.value = r;
        categories.value = list;
      })
      .finally(() => (pending = undefined));
    return pending;
  }
  return { config, rules, categories, load };
});
