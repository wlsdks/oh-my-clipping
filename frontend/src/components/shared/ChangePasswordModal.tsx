import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { passwordSchema } from "@/shared/lib/passwordSchema";
import { authService } from "@/services/authService";
import { userFriendlyMessage } from "@/shared/lib/httpError";

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "현재 비밀번호를 입력하세요"),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, "비밀번호를 다시 입력하세요"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "비밀번호가 일치하지 않아요",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof changePasswordSchema>;

interface Props {
  open: boolean;
  forced?: boolean;
  onSuccess: () => void;
  onClose?: () => void;
}

export function ChangePasswordModal({
  open,
  forced = false,
  onSuccess,
  onClose,
}: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(changePasswordSchema),
  });

  const { mutate, isPending } = useMutation({
    mutationFn: (data: FormValues) =>
      authService.changePassword(data.currentPassword, data.newPassword),
    onSuccess: () => {
      toast.success("비밀번호가 변경되었습니다");
      reset();
      onSuccess();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "비밀번호 변경에 실패했어요"));
    },
  });

  return (
    <Dialog open={open} onOpenChange={forced ? undefined : onClose}>
      <DialogContent
        className="max-w-md"
        onPointerDownOutside={forced ? (e) => e.preventDefault() : undefined}
        onEscapeKeyDown={forced ? (e) => e.preventDefault() : undefined}
      >
        <DialogHeader>
          <DialogTitle>비밀번호 변경</DialogTitle>
          <DialogDescription>
            {forced
              ? "임시 비밀번호를 사용 중입니다. 새 비밀번호를 설정해 주세요."
              : "현재 비밀번호를 확인한 후 새 비밀번호를 설정합니다."}
          </DialogDescription>
        </DialogHeader>
        <form
          onSubmit={handleSubmit((data) => mutate(data))}
          className="space-y-4"
        >
          <div className="space-y-1">
            <Label htmlFor="currentPassword">현재 비밀번호</Label>
            <Input
              id="currentPassword"
              type="password"
              {...register("currentPassword")}
            />
            {errors.currentPassword && (
              <p className="text-xs text-destructive">
                {errors.currentPassword.message}
              </p>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="newPassword">새 비밀번호</Label>
            <Input
              id="newPassword"
              type="password"
              {...register("newPassword")}
            />
            {errors.newPassword && (
              <p className="text-xs text-destructive">
                {errors.newPassword.message}
              </p>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="confirmPassword">새 비밀번호 확인</Label>
            <Input
              id="confirmPassword"
              type="password"
              {...register("confirmPassword")}
            />
            {errors.confirmPassword && (
              <p className="text-xs text-destructive">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>
          <Button type="submit" className="w-full" disabled={isPending}>
            {isPending ? "변경 중..." : "비밀번호 변경"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
