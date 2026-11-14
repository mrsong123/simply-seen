import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import { existsSync, readFileSync } from "node:fs";
import { parse } from "dotenv";
export default defineConfig(({ mode, command }) => {
  const local = existsSync(".env.local")
    ? parse(readFileSync(".env.local"))
    : {};
  const env = { ...loadEnv(mode, process.cwd(), ""), ...local };
  for (const name of ["VITE_API_BASE_URL", "API_PROXY_TARGET", "WEB_PORT"])
    if (process.env[name] !== undefined) env[name] = process.env[name]!;
  if (!env.VITE_API_BASE_URL) throw new Error("VITE_API_BASE_URL is required");
  if (
    mode === "production" &&
    !env.VITE_API_BASE_URL.startsWith("/") &&
    !env.VITE_API_BASE_URL.startsWith("https://")
  )
    throw new Error("Production API base must use HTTPS or a same-origin path");
  if (command === "serve" && !env.API_PROXY_TARGET)
    throw new Error("API_PROXY_TARGET is required for local development");
  return {
    plugins: [vue()],
    resolve: {
      alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
    },
    define: {
      "import.meta.env.VITE_API_BASE_URL": JSON.stringify(
        env.VITE_API_BASE_URL,
      ),
    },
    server: {
      port: Number(env.WEB_PORT || 5173),
      strictPort: true,
      proxy:
        command === "serve"
          ? {
              "/api": { target: env.API_PROXY_TARGET, changeOrigin: true },
              "/actuator": { target: env.API_PROXY_TARGET, changeOrigin: true },
            }
          : undefined,
    },
  };
});
