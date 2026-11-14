import { describe, expect, it } from "vitest";
import {
  identityLabel,
  validateIdentity,
  withdrawalRequirements,
} from "../src/utils/identity";
import type { Member, AppConfig } from "../src/types/models";
const profile = {
  identityStatus: "VERIFIED",
  identityDevelopment: false,
  wechatBound: true,
} as Member;
const production = {
  devMode: false,
  identityRequiredForWithdrawal: true,
} as AppConfig;
describe("Identity matching and financial entry requirements", () => {
  it("validates resident-ID checksum and normalizes a lower-case X without asserting provider success", () => {
    expect(validateIdentity(" 张三 ", "11010519491231002x")).toEqual({
      name: "张三",
      idNumber: "11010519491231002X",
    });
    expect(() => validateIdentity("张三", "110105194912310020")).toThrow(
      "校验位",
    );
    expect(() => validateIdentity("张", "11010519491231002X")).toThrow(
      "完整姓名",
    );
  });
  it("never treats a simulated successful match as production verification", () => {
    expect(withdrawalRequirements(profile, production)).toEqual({
      identity: false,
      wechat: false,
    });
    expect(
      withdrawalRequirements(
        { ...profile, identityDevelopment: true },
        production,
      ).identity,
    ).toBe(true);
    expect(
      withdrawalRequirements(
        { ...profile, identityStatus: "PENDING" },
        production,
      ).identity,
    ).toBe(true);
    expect(
      withdrawalRequirements({ ...profile, wechatBound: false }, production)
        .wechat,
    ).toBe(true);
  });
  it("keeps development matching visibly separate from actual two-element matching", () => {
    expect(identityLabel("VERIFIED", true)).toBe("开发模拟通过");
    expect(identityLabel("VERIFIED", false)).toBe("姓名证件信息已匹配");
    expect(identityLabel("PENDING")).toBe("核验处理中");
  });
});
