import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig(({ mode }) => {
  const e = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [vue()],
    server: {
      port: Number(e.WEB_PORT || 5173),
      proxy: {
        "/api": { target: e.API_PROXY_TARGET, changeOrigin: true },
        "/actuator": { target: e.API_PROXY_TARGET, changeOrigin: true },
      },
    },
  };
});
