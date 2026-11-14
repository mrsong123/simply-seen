import { defineStore } from "pinia";
import { authApi } from "@/api/auth";
import type { PhoneLogin } from "@/types/account";
import {
  readSession,
  persistSession,
  type AuthSession,
  type SessionKind,
} from "@/utils/session";
export const useAuthStore = defineStore("auth", {
  state: () => ({
    member: readSession("member"),
    admin: readSession("admin"),
    memberProfile: null as any,
    adminProfile: null as any,
    adminValidated: false,
  }),
  getters: {
    isMember: (s) => Boolean(s.member),
    isAdmin: (s) => Boolean(s.admin),
  },
  actions: {
    saveSession(kind: SessionKind, value: AuthSession) {
      this[kind] = value;
      persistSession(kind, value);
      if (kind === "member") this.memberProfile = value.profile;
      else {
        this.adminProfile = value.profile;
        this.adminValidated = false;
      }
    },
    clearSession(kind: SessionKind) {
      this[kind] = null;
      persistSession(kind, null);
      if (kind === "member") this.memberProfile = null;
      else {
        this.adminProfile = null;
        this.adminValidated = false;
      }
    },
    async loginPhone(body: PhoneLogin) {
      this.saveSession("member", await authApi.loginPhone(body));
    },
    async loginMember(body: { name: string; inviteCode: string }) {
      this.saveSession("member", await authApi.loginDev(body));
    },
    async loginAdmin(body: { username: string; password: string }) {
      this.saveSession("admin", await authApi.loginAdmin(body));
    },
    async fetchMemberProfile() {
      const token = this.member?.token;
      if (!token) return null;
      const profile = await authApi.getMember();
      if (this.member?.token !== token) return null;
      this.memberProfile = profile;
      return this.memberProfile;
    },
    async fetchAdminProfile() {
      this.adminProfile = await authApi.getAdmin();
      this.adminValidated = true;
      return this.adminProfile;
    },
    async updateMember(name: string) {
      await authApi.updateMember({ name });
      await this.fetchMemberProfile();
    },
    async logout(kind: SessionKind = "member") {
      try {
        await authApi.logout(kind);
      } finally {
        this.clearSession(kind);
      }
    },
  },
});
