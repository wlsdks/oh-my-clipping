export interface DeliveryOpsSummary {
  total: number;
  sent: number;
  failed: number;
}

export interface PipelineOpsSummary {
  total: number;
  success: number;
  failed: number;
}

export interface OpsSummary {
  delivery: DeliveryOpsSummary;
  pipeline: PipelineOpsSummary;
}
