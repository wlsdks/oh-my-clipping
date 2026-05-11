export interface UnhealthySource {
  id: string;
  name: string;
  lastSuccessAt: string | null;
  crawlFailCount: number;
  reason: string;
}

export interface SourceHealthResponse {
  totalCount: number;
  healthyCount: number;
  unhealthy: UnhealthySource[];
}
