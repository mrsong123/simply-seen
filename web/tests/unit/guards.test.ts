import { describe, it, expect, vi } from "vitest";
import { accessGuard } from "../../src/router/guards";
const route = (meta: Record<string, unknown> = {}, path = "/catalog") => ({
  meta,
  fullPath: path,
  name: "catalog",
  query: {},
});
const session = { token: "test-token", kind: "ADMIN", profile: {} };
function setup() {
  const auth = {
    member: null as any,
    admin: null as any,
    adminValidated: false,
    adminProfile: null as any,
    fetchAdminProfile: vi.fn(async () => null),
  };
  const ui = { message: vi.fn() };
  return { auth, ui };
}
describe("route access policy", () => {
  it("keeps public browsing available without login", async () => {
    const { auth, ui } = setup();
    expect(await accessGuard(route(), auth, ui)).toBe(true);
  });
  it("redirects a member-only page to login while preserving the intended route", async () => {
    const { auth, ui } = setup();
    expect(
      await accessGuard(route({ requiresMember: true }, "/checkout"), auth, ui),
    ).toEqual({ name: "login", query: { redirect: "/checkout" } });
  });
  it("does not allow a member session to access administration", async () => {
    const { auth, ui } = setup();
    auth.member = { ...session, kind: "MEMBER" };
    expect(
      await accessGuard(
        route({ requiresAdmin: true }, "/admin/accounts"),
        auth,
        ui,
      ),
    ).toEqual({ path: "/admin/login", query: { redirect: "/admin/accounts" } });
  });
  it("refreshes an unverified admin session and enforces roles returned by the server", async () => {
    const { auth, ui } = setup();
    auth.admin = session;
    auth.fetchAdminProfile.mockImplementation(async () => {
      auth.adminProfile = { role: "FINANCE" };
      auth.adminValidated = true;
      return auth.adminProfile;
    });
    expect(
      await accessGuard(
        route({ requiresAdmin: true, roles: ["ADMIN"] }, "/admin/accounts"),
        auth,
        ui,
      ),
    ).toBe("/admin");
    expect(auth.fetchAdminProfile).toHaveBeenCalledOnce();
    expect(ui.message).toHaveBeenCalled();
  });
  it("allows verified administrators to access their permitted routes without redundant validation", async () => {
    const { auth, ui } = setup();
    auth.admin = session;
    auth.adminProfile = { role: "OPERATOR" };
    auth.adminValidated = true;
    expect(
      await accessGuard(
        route(
          { requiresAdmin: true, roles: ["ADMIN", "OPERATOR"] },
          "/admin/orders",
        ),
        auth,
        ui,
      ),
    ).toBe(true);
    expect(auth.fetchAdminProfile).not.toHaveBeenCalled();
  });
  it("redirects an expired admin session to login", async () => {
    const { auth, ui } = setup();
    auth.admin = session;
    auth.fetchAdminProfile.mockRejectedValue(new Error("expired"));
    expect(
      await accessGuard(route({ requiresAdmin: true }, "/admin"), auth, ui),
    ).toEqual({ path: "/admin/login", query: { redirect: "/admin" } });
  });
  it("prevents external and self-referencing member login redirects", async () => {
    const { auth, ui } = setup();
    auth.member = { ...session, kind: "MEMBER" };
    for (const redirect of ["//outside.example", "/login"])
      expect(
        await accessGuard(
          { ...route(), name: "login", query: { redirect } },
          auth,
          ui,
        ),
      ).toBe("/account");
  });
});
