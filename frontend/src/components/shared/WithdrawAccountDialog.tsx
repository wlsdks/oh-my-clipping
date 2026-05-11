import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/kyInstance";
import { userService } from "@/services/userService";
import { authService } from "@/services/authService";
import { authStore } from "@/store/authStore";
import { userFriendlyMessage } from "@/shared/lib/httpError";

interface WithdrawAccountDialogProps {
  open: boolean;
  userId: string;
  /** admin이 다른 유저를 탈퇴시킬 때 true */
  isAdmin?: boolean;
  onClose: () => void;
}

export function WithdrawAccountDialog({ open, userId, isAdmin = false, onClose }: WithdrawAccountDialogProps) {
  const [confirmed, setConfirmed] = useState(false);
  const [password, setPassword] = useState("");

  const { mutate: withdraw, isPending } = useMutation({
    mutationFn: async () => {
      if (isAdmin) {
        await userService.withdrawAdminUserAccount(userId);
      } else {
        await api.post("user/account/withdraw", { json: { password } });
      }
    },
    onSuccess: async () => {
      toast.success("탈퇴가 완료됐어요");
      if (!isAdmin) {
        try { await authService.logout(); } catch { /* ignore */ }
        authStore.getState().logout();
        window.location.assign("/login");
      }
      onClose();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "탈퇴에 실패했어요"))
  });

  function handleOpenChange(isOpen: boolean) {
    if (!isOpen && !isPending) {
      setConfirmed(false);
      setPassword("");
      onClose();
    }
  }

  function handleConfirm() {
    if (!confirmed) return;
    if (!isAdmin && !password.trim()) {
      toast.warning("비밀번호를 입력해 주세요");
      return;
    }
    withdraw();
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle className="text-destructive">회원 탈퇴</DialogTitle>
          <DialogDescription className="sr-only">회원 탈퇴를 진행합니다</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground">
            모든 구독이 해제되고 계정이 비활성화돼요. 이 작업은 되돌릴 수 없어요.
          </p>

          {!isAdmin && (
            <div className="space-y-1.5">
              <Label htmlFor="withdraw-password">비밀번호 확인</Label>
              <Input
                id="withdraw-password"
                type="password"
                placeholder="현재 비밀번호를 입력하세요"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isPending}
              />
            </div>
          )}

          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              disabled={isPending}
              className="mt-0.5 h-4 w-4 rounded border-input accent-primary cursor-pointer"
            />
            <span className="text-sm leading-snug">위 내용을 확인했으며, 탈퇴에 동의합니다.</span>
          </label>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            취소
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={!confirmed || isPending}>
            {isPending ? "처리 중..." : "탈퇴하기"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
