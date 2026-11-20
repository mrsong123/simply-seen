import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { memberApi } from "../api/member";
import { accountApi } from "../api/account";
import type { Member, Session } from "../types/models";
import type { PhoneLogin, PhoneProof } from "../types/account";
import { authStorage } from "../utils/storage";
import { wechatLoginCode } from "../utils/wechat";
export const useSessionStore = defineStore("session", () => {
  const profile = ref<Member | null>(null);
  const hasStoredSession = ref(!!authStorage.token());
  const authenticated = computed(() => !!profile.value);
  let revision = 0;
  function clear() {
    revision++;
    profile.value = null;
    authStorage.clear();
    hasStoredSession.value = false;
  }
  uni.$on("session-expired", clear);
  async function refresh() {
    const token = authStorage.token(),
      current = revision;
    if (!token) {
      hasStoredSession.value = false;
      profile.value = null;
      return;
    }
    hasStoredSession.value = true;
    const data = await memberApi.profile();
    if (current === revision && token === authStorage.token())
      profile.value = data;
  }
  async function authenticate(operation: () => Promise<Session>) {
    const current = ++revision;
    const data = await operation();
    if (current !== revision) throw new Error("登录状态已变化，请重新操作");
    authStorage.saveToken(data.token);
    hasStoredSession.value = true;
    profile.value = data.profile;
  }
  async function login() {
    await authenticate(async () =>
      memberApi.login(await wechatLoginCode(), authStorage.invite()),
    );
  }
  async function loginDev(name: string) {
    await authenticate(() => memberApi.devLogin(name, authStorage.invite()));
  }
  async function loginPhone(body: PhoneLogin) {
    await authenticate(() =>
      accountApi.loginPhone({ ...body, inviteCode: authStorage.invite() }),
    );
  }
  async function updateBinding(operation: () => Promise<Member>) {
    const token = authStorage.token(),
      current = ++revision;
    const data = await operation();
    if (current === revision && token === authStorage.token())
      profile.value = data;
  }
  async function bindPhone(body: PhoneProof) {
    await updateBinding(() => accountApi.bindPhone(body));
  }
  async function bindWechat() {
    const token = authStorage.token(),
      current = revision;
    const code = await wechatLoginCode();
    if (token !== authStorage.token() || current !== revision)
      throw new Error("登录状态已变化，请重新绑定微信");
    await updateBinding(() => accountApi.bindWechat(code));
  }
  async function logout() {
    const current = ++revision;
    try {
      await memberApi.logout();
    } finally {
      if (current === revision) {
        clear();
        uni.$emit("session-expired");
      }
    }
  }
  async function updateName(name: string) {
    await memberApi.updateName(name.trim());
    await refresh();
  }
  return {
    profile,
    authenticated,
    hasStoredSession,
    refresh,
    login,
    loginDev,
    loginPhone,
    bindPhone,
    bindWechat,
    logout,
    updateName,
    clear,
  };
});
