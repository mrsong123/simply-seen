import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
interface Credentials {
  username: string;
  password: string;
}
function readValues(file: string) {
  return Object.fromEntries(
    readFileSync(file, "utf8")
      .split(/\r?\n/)
      .filter(
        (line) =>
          line.trim() && !line.trim().startsWith("#") && line.includes("="),
      )
      .map((line) => {
        const index = line.indexOf("=");
        const name = line
          .slice(0, index)
          .trim()
          .replace(/^export\s+/, "");
        let value = line.slice(index + 1).trim();
        if (
          (value.startsWith('"') && value.endsWith('"')) ||
          (value.startsWith("'") && value.endsWith("'"))
        )
          value = value.slice(1, -1);
        return [name, value];
      }),
  );
}
export function readAdminCredentials(
  projectRoot = fileURLToPath(new URL("../../../", import.meta.url)),
  environment: Record<string, string | undefined> = process.env,
): Credentials {
  const username = environment.E2E_ADMIN_USER || environment.E2E_ADMIN_USERNAME,
    password = environment.E2E_ADMIN_PASSWORD;
  if (username || password) {
    if (!username || !password)
      throw new Error("Set both E2E_ADMIN_USER and E2E_ADMIN_PASSWORD");
    return { username, password };
  }
  const explicit = environment.E2E_ADMIN_CREDENTIALS_PATH;
  if (explicit && !existsSync(resolve(projectRoot, explicit)))
    throw new Error("E2E_ADMIN_CREDENTIALS_PATH does not exist");
  const files = explicit
    ? [resolve(projectRoot, explicit)]
    : [
        resolve(projectRoot, "server/data/bootstrap-admin.txt"),
        resolve(projectRoot, "data/bootstrap-admin.txt"),
        resolve(projectRoot, ".env"),
      ];
  for (const file of files) {
    if (!existsSync(file)) continue;
    const values = readValues(file);
    const name = values.username || values.ADMIN_BOOTSTRAP_USER || "admin";
    const secret = values.password || values.ADMIN_BOOTSTRAP_PASSWORD;
    if (secret) return { username: name, password: secret };
  }
  throw new Error(
    "Start the local backend to generate data/bootstrap-admin.txt, or configure E2E_ADMIN_USER and E2E_ADMIN_PASSWORD",
  );
}
