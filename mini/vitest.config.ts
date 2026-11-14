import { defineConfig } from "vitest/config";
export default defineConfig({
  test: {
    environment: "node",
    include: ["tests/**/*.spec.ts"],
    pool: "forks",
    maxWorkers: 1,
    minWorkers: 1,
  },
  define: {
    "import.meta.env.VITE_API_BASE_URL": JSON.stringify(
      "https://api.example.test/api/v1",
    ),
  },
});
