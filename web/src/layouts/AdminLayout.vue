<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "@/components/common/Icon.vue";
import ResourceState from "@/components/common/ResourceState.vue";
import PasswordDialog from "@/components/admin/PasswordDialog.vue";
import { adminDefinitions } from "@/config/admin";
import { useAuthStore } from "@/store/auth";
import { useConfigStore } from "@/store/config";
import { label } from "@/utils/format";
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  settings = useConfigStore();
const mobile = ref(false),
  passwordDialog = ref(false);
const user = computed(() => auth.adminProfile || auth.admin?.profile);
const page = computed(() => String(route.meta.adminPage || "overview"));
const definition = computed(
  () => adminDefinitions[page.value] || adminDefinitions.overview,
);
const menu = computed(() =>
  Object.entries(adminDefinitions)
    .filter(([, definition]) =>
      definition.roles.includes(String(user.value?.role || "")),
    )
    .map(([key, definition]) => ({ key, ...definition })),
);
watch(
  () => route.fullPath,
  () => (mobile.value = false),
);
watch(
  () => auth.admin,
  (value) => {
    if (!value) router.replace("/admin/login");
  },
);
onMounted(async () => {
  try {
    await auth.fetchAdminProfile();
    await settings.load();
  } catch {}
});
async function signOut() {
  try {
    await auth.logout("admin");
  } finally {
    router.replace("/admin/login");
  }
}
</script>
<template>
  <div class="admin-shell">
    <aside class="sidebar" :class="{ open: mobile }">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">素</span
        ><span>素见好物<small>BUSINESS STUDIO</small></span></RouterLink
      >
      <div class="sidebar-section">工作空间</div>
      <nav>
        <RouterLink
          v-for="item in menu"
          :key="item.key"
          :to="'/admin/' + (item.key === 'overview' ? '' : item.key)"
          :class="{ active: page === item.key }"
          ><Icon :name="item.icon" :size="19" /><span>{{
            item.title
          }}</span></RouterLink
        >
      </nav>
      <div class="sidebar-foot">
        <span class="status-dot"></span
        ><span>{{
          settings.config.devMode ? "开发环境 · 无真实资金" : "生产业务环境"
        }}</span
        ><RouterLink to="/" aria-label="前往商城"
          ><Icon name="ArrowRight" :size="17"
        /></RouterLink>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <div>
          <button
            class="icon-button mobile-menu"
            aria-label="管理导航"
            @click="mobile = !mobile"
          >
            <Icon name="Menu" /></button
          ><span>工作台</span><Icon name="ChevronRight" :size="14" /><strong>{{
            definition.title
          }}</strong>
        </div>
        <div>
          <span class="badge neutral">{{ label(user?.role) }}</span
          ><button class="profile-button" @click="passwordDialog = true">
            <span class="mini-avatar">{{
              user?.username?.slice(0, 1).toUpperCase()
            }}</span
            >{{ user?.username }}</button
          ><button
            class="icon-button"
            aria-label="退出管理后台"
            @click="signOut"
          >
            <Icon name="LogOut" :size="18" />
          </button>
        </div>
      </header>
      <main class="admin-content"><ResourceState v-if="settings.error" :error="settings.error" @retry="settings.load(true).catch(() => {})" /><RouterView /></main>
      <footer class="admin-footer">
        SIMPLY SEEN BUSINESS STUDIO <span>让每一份经营，有迹可循。</span>
      </footer>
    </div>
    <PasswordDialog v-if="passwordDialog" @close="passwordDialog = false" />
  </div>
</template>
