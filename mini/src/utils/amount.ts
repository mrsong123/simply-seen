export function yuanToFen(value: string): number {
  const normalized = value.trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized))
    throw new Error("请输入有效金额，最多两位小数");
  const [whole, fraction = ""] = normalized.split(".");
  const amount = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(amount) || amount <= 0)
    throw new Error("请输入有效的正数金额，金额不能超出支持范围");
  return amount;
}
