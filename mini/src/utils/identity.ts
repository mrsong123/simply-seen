import type { AppConfig, Member } from "../types/models";
import type { IdentityStatus } from "../types/account";

export function identityLabel(status?: IdentityStatus, development = false) {
  if (status === "VERIFIED")
    return development ? "开发模拟通过" : "姓名证件信息已匹配";
  return (
    { UNVERIFIED: "未认证", PENDING: "核验处理中", REJECTED: "核验未通过" }[
      status || "UNVERIFIED"
    ] || "未认证"
  );
}

export function withdrawalRequirements(
  profile: Member | null,
  config: AppConfig | null,
) {
  return {
    identity:
      !!config?.identityRequiredForWithdrawal &&
      (profile?.identityStatus !== "VERIFIED" ||
        (!!profile.identityDevelopment && !config.devMode)),
    wechat: !!config && !config.devMode && !profile?.wechatBound,
  };
}

export function validateIdentity(name: string, idNumber: string) {
  const cleanName = name.trim(),
    cleanNumber = idNumber.trim().toUpperCase();
  if (cleanName.length < 2 || cleanName.length > 40)
    throw new Error("请输入证件上的完整姓名，2至40个字");
  if (!/^\d{17}[\dX]$/.test(cleanNumber))
    throw new Error("请输入18位大陆居民身份证号码");
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const sum = weights.reduce(
    (total, weight, index) => total + Number(cleanNumber[index]) * weight,
    0,
  );
  if ("10X98765432"[sum % 11] !== cleanNumber[17])
    throw new Error("身份证校验位不正确，请核对后重试");
  const year = Number(cleanNumber.slice(6, 10)),
    month = Number(cleanNumber.slice(10, 12)),
    day = Number(cleanNumber.slice(12, 14));
  const birthday = new Date(year, month - 1, day);
  if (
    year < 1900 ||
    birthday.getFullYear() !== year ||
    birthday.getMonth() !== month - 1 ||
    birthday.getDate() !== day ||
    birthday.getTime() > Date.now()
  )
    throw new Error("身份证出生日期不正确，请核对后重试");
  return { name: cleanName, idNumber: cleanNumber };
}
