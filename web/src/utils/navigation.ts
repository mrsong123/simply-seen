export function safeRedirect(value: unknown, fallback: string) {
  const target = typeof value === "string" ? value : "";
  return target.startsWith("/") &&
    !target.startsWith("//") &&
    !target.startsWith("/login")
    ? target
    : fallback;
}
