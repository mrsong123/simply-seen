import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { memberApi } from "../api/member";
import type { Member } from "../types/models";
import { authStorage } from "../utils/storage";
import { wechatLoginCode } from "../utils/wechat";
export const useSessionStore = defineStore("session", () => {
  const profile = ref<Member | null>(null);
  const authenticated = computed(() => !!profile.value);
  function clear() {
    profile.value = null;
    authStorage.clear();
  }
  uni.$on("session-expired", clear);
  async function refresh() {
    if (!authStorage.token()) {
      profile.value = null;
      return;
    }
    profile.value = await memberApi.profile();
  }
  async function login() {
    const code = await wechatLoginCode();
    const data = await memberApi.login(code, authStorage.invite());
    authStorage.saveToken(data.token);
    profile.value = data.profile;
  }
  async function loginDev(name: string) {
    const data = await memberApi.devLogin(name, authStorage.invite());
    authStorage.saveToken(data.token);
    profile.value = data.profile;
  }
  async function logout() {
    try {
      await memberApi.logout();
    } finally {
      clear();
      uni.$emit("session-expired");
    }
  }
  async function updateName(name: string) {
    await memberApi.updateName(name.trim());
    await refresh();
  }
  return {
    profile,
    authenticated,
    refresh,
    login,
    loginDev,
    logout,
    updateName,
    clear,
  };
});
