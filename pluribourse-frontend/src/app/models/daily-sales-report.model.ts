export interface DailySalesReportDto {
  reportDate: string;
  soldItemCount: number;
  unsoldItemCount: number;
  grossRevenue: number;
  commission: number;
  cashTotal: number;
  checkTotal: number;
  cardTotal: number;
}
