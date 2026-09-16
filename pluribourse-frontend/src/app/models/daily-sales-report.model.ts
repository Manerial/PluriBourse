export interface DailySalesReportDto {
  reportDate: string;
  soldItemCount: number;
  unsoldItemCount: number;
  // Every amount below: number, not BigDecimal string — see item.model.ts for why.
  grossRevenue: number;
  commission: number;
  cashTotal: number;
  checkTotal: number;
  cardTotal: number;
  currency: string;
}
