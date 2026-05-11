export const systemStatusKeys = {
  all: ["systemStatus"] as const,
  status: () => [...systemStatusKeys.all, "status"] as const,
};
