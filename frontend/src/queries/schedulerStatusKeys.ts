/**
 * 스케줄러 상태 쿼리 키 팩토리.
 */
export const schedulerStatusKeys = {
  all: ["schedulerStatus"] as const,
  list: () => [...schedulerStatusKeys.all, "list"] as const,
};
