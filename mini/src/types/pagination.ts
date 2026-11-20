export interface RecordPage<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  states: string[];
  summary: Record<string, number>;
}
