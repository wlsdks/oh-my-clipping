export interface DeliverySummary {
  totalCount: number;
  sentCount: number;
  failedCount: number;
  skippedCount: number;
  successRate: number;
}

export interface DeliveryLogRecord {
  id: string;
  categoryId: string;
  channelId: string;
  deliveryDate: string;
  deliveryHour: number;
  status: "RESERVED" | "SENT" | "FAILED" | "SKIPPED";
  itemCount: number;
  slackMessageTs?: string | null;
  retryAttempted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DeliveryLogsPage {
  content: DeliveryLogRecord[];
  totalCount: number;
  page: number;
  size: number;
}
