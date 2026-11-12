import { defineConfig, loadEnv } from "vite";
import fs from "node:fs";
import uni from "@dcloudio/vite-plugin-uni";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  if (!env.VITE_API_BASE_URL || !env.WX_APP_ID)
    throw new Error("请配置 VITE_API_BASE_URL 和 WX_APP_ID");
  const path = "src/manifest.json";
  const manifest = JSON.parse(fs.readFileSync(path, "utf8"));
  manifest["mp-weixin"].appid = env.WX_APP_ID;
  const original = fs.readFileSync(path, "utf8");
  fs.writeFileSync(path, JSON.stringify(manifest, null, 2) + "\n");
  process.once("exit", () => fs.writeFileSync(path, original));
  return {
    plugins: [
      uni(),
      {
        name: "restore-manifest",
        closeBundle() {
          fs.writeFileSync(path, original);
        },
      },
    ],
    define: { "process.env.WX_APP_ID": JSON.stringify(env.WX_APP_ID) },
  };
});
