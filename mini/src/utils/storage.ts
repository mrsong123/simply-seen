const TOKEN = "sj-token";
const INVITE = "sj-invite";
export const authStorage = {
  token: (): string => uni.getStorageSync(TOKEN) || "",
  saveToken: (token: string) => uni.setStorageSync(TOKEN, token),
  clear: () => uni.removeStorageSync(TOKEN),
  invite: (): string => uni.getStorageSync(INVITE) || "",
  saveInvite: (code: string) => {
    if (code) uni.setStorageSync(INVITE, code);
  },
};
