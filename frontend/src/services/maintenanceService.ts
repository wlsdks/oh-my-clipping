import { api } from "@/lib/kyInstance";

interface MaintenanceStatus {
  active: boolean;
  message: string;
}

export const maintenanceService = {
  getStatus: (): Promise<MaintenanceStatus> =>
    api.get("public/maintenance").json(),
};
