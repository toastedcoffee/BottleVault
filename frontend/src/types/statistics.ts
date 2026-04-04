export interface StatisticsResponse {
  totalBottles: number;
  totalValue: number;
  averageCost: number | null;
  averageRating: number | null;
  percentageOpened: number;
  statusBreakdown: StatusCount[];
  typeDistribution: TypeCount[];
  spendingOverTime: MonthlySpending[];
  topRatedBottles: BottleSummary[];
  recentAdditions: BottleSummary[];
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface TypeCount {
  type: string;
  count: number;
}

export interface MonthlySpending {
  year: number;
  month: number;
  total: number;
}

export interface BottleSummary {
  id: string;
  productName: string;
  brandName: string;
  type: string;
  rating: number | null;
  purchaseCost: number | null;
  purchaseDate: string | null;
  createdAt: string;
}
