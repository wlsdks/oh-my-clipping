import { Navigate, Outlet } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import { ChangePasswordModal } from "@/components/shared/ChangePasswordModal";
import { authService } from "@/services/authService";

interface ProtectedRouteProps {
  role: "ADMIN" | "USER";
}

export function ProtectedRoute({ role }: ProtectedRouteProps) {
  const { isLoggedIn, user, login } = useAuthStore();

  if (!isLoggedIn || !user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to="/login" replace />;
  if (user.role === "USER" && user.approvalStatus !== "APPROVED") {
    return <Navigate to="/login?pending_approval=1" replace />;
  }

  if (user.mustChangePassword) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <ChangePasswordModal
          open
          forced
          onSuccess={async () => {
            const updated = await authService.fetchMe();
            login(updated);
          }}
        />
      </div>
    );
  }

  return <Outlet />;
}
