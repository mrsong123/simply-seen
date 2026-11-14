import { money, date, label } from "@/utils/format";
import type { AdminColumn } from "@/config/admin";
const amounts = new Set([
  "cash_due",
  "original_amount",
  "available",
  "reserved",
  "debt",
  "gross",
  "tax",
  "net",
  "budget",
  "orders_total",
  "refunds_total",
  "transfers_total",
]);
export function formatAdminCell(row: any, field: string) {
  if (field === "amount" || field === "balance")
    return row.asset?.startsWith("points")
      ? `${row[field]} 点`
      : `¥${money(row[field])}`;
  if (amounts.has(field)) return `¥${money(row[field])}`;
  if (field.endsWith("_at")) return date(row[field]);
  if (["state", "status", "role", "asset", "payment_state"].includes(field))
    return label(row[field]);
  if (field === "active") return row[field] ? "已启用" : "已停用";
  return row[field] ?? "—";
}
export function exportAdminCsv(
  title: string,
  columns: AdminColumn[],
  rows: any[],
) {
  const safe = (value: unknown) => {
    let text = String(value ?? "");
    if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
    return `"${text.replaceAll('"', '""')}"`;
  };
  const content =
    "\ufeff" +
    [
      columns.map((column) => safe(column[1])).join(","),
      ...rows.map((row) =>
        columns
          .map((column) => safe(formatAdminCell(row, column[0])))
          .join(","),
      ),
    ].join("\r\n");
  const url = URL.createObjectURL(
    new Blob([content], { type: "text/csv;charset=utf-8;" }),
  );
  const link = document.createElement("a");
  link.href = url;
  link.download = `素见-${title}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
