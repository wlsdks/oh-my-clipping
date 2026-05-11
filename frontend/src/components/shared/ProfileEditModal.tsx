import { useEffect } from "react";
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
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { userService } from "@/services/userService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { useDepartmentTree } from "@/hooks/useDepartmentTree";

/**
 * V129: 사용자 본인 프로필(부서/팀 FK) 편집 모달.
 *
 * - 부서/팀은 `/api/public/departments/tree` 로 로드한 목록에서 cascade 선택한다.
 * - 부서는 선택 시 팀 선택이 리셋된다.
 * - 팀은 선택 사항(빈 값 허용). 빈 문자열은 서버에서 null 저장으로 해석된다.
 * - `initialDepartmentId` 없이 legacy `initialDepartment` 문자열만 있는 사용자는
 *   상단 경고 밴드로 "정식 부서 선택" 을 유도한다 (정규화 마이그레이션 전환기 UX).
 */

const profileSchema = z.object({
  departmentId: z.string().min(1, "부서를 선택하세요"),
  teamId: z.string().optional(),
});

type FormValues = z.infer<typeof profileSchema>;

interface ProfileEditModalProps {
  open: boolean;
  /** V129: 현재 저장된 부서 FK (없으면 null). */
  initialDepartmentId?: string | null;
  /** V129: 현재 저장된 팀 FK (없으면 null). */
  initialTeamId?: string | null;
  /** legacy: 정규화 전 사용자 입력 부서명. FK id 가 없을 때 경고 밴드로만 사용. */
  initialDepartment?: string | null;
  /** legacy: 정규화 전 사용자 입력 팀명. */
  initialTeam?: string | null;
  onClose: () => void;
  /**
   * 저장 성공 시 호출 — 상위에서 authStore 등 캐시를 갱신할 수 있게 FK + 이름을 함께 전달한다.
   */
  onSaved?: (result: {
    departmentId: string | null;
    departmentName: string | null;
    teamId: string | null;
    teamName: string | null;
  }) => void;
}

export function ProfileEditModal({
  open,
  initialDepartmentId,
  initialTeamId,
  initialDepartment,
  initialTeam,
  onClose,
  onSaved,
}: ProfileEditModalProps) {
  const { departments, isLoading, isError, refetch } = useDepartmentTree({
    enabled: open,
  });

  const {
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      departmentId: initialDepartmentId ?? "",
      teamId: initialTeamId ?? "",
    },
  });

  // 모달이 열릴 때마다 최신 초기값으로 리셋 (재오픈 시 이전 상태가 남지 않도록).
  useEffect(() => {
    if (open) {
      reset({
        departmentId: initialDepartmentId ?? "",
        teamId: initialTeamId ?? "",
      });
    }
  }, [open, initialDepartmentId, initialTeamId, reset]);

  const departmentId = watch("departmentId");
  const selectedDepartment = departments.find((d) => d.id === departmentId);
  const availableTeams = selectedDepartment?.teams ?? [];

  // FK 없이 legacy 이름만 있는 사용자에게는 정식 부서 선택을 유도한다.
  const showLegacyWarning =
    !initialDepartmentId &&
    (initialDepartment != null && initialDepartment.trim().length > 0);

  const { mutate, isPending } = useMutation({
    mutationFn: (values: FormValues) =>
      userService.updateProfile({
        departmentId: values.departmentId,
        teamId: values.teamId ?? "",
      }),
    onSuccess: (result) => {
      toast.success("프로필을 업데이트했어요");
      onSaved?.(result);
      onClose();
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "프로필 업데이트에 실패했어요"));
    },
  });

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) onClose(); }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>프로필 편집</DialogTitle>
          <DialogDescription>
            부서와 팀은 분석 통계에 사용돼요.
          </DialogDescription>
        </DialogHeader>

        {showLegacyWarning && (
          <div
            role="alert"
            className="text-sm text-[var(--status-warning-text)] bg-[var(--status-warning-bg)] p-2 rounded"
          >
            현재: 레거시 부서 &quot;{initialDepartment}
            {initialTeam ? ` · ${initialTeam}` : ""}
            &quot; · 정식 부서를 선택해 주세요
          </div>
        )}

        {isError && (
          <div
            role="alert"
            className="text-sm text-[var(--status-danger-text)] bg-[var(--status-danger-bg)] p-2 rounded flex items-center justify-between gap-2"
          >
            <span>부서 목록을 불러오지 못했어요</span>
            <button
              type="button"
              onClick={() => refetch()}
              className="text-xs underline"
            >
              다시 시도
            </button>
          </div>
        )}

        <form onSubmit={handleSubmit((data) => mutate(data))} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="profile-department-trigger">부서</Label>
            <Select
              value={departmentId}
              onValueChange={(id) => {
                setValue("departmentId", id, { shouldDirty: true, shouldValidate: true });
                // 부서 변경 시 팀 선택은 초기화.
                setValue("teamId", "", { shouldDirty: true });
              }}
              disabled={isLoading}
            >
              <SelectTrigger
                id="profile-department-trigger"
                aria-label="부서 선택"
                aria-invalid={Boolean(errors.departmentId)}
              >
                <SelectValue placeholder={isLoading ? "부서를 불러오는 중..." : "부서 선택"} />
              </SelectTrigger>
              <SelectContent>
                {departments.map((dept) => (
                  <SelectItem key={dept.id} value={dept.id}>
                    {dept.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.departmentId && (
              <p role="alert" className="text-xs text-destructive">
                {errors.departmentId.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="profile-team-trigger">팀 (선택)</Label>
            <Select
              value={watch("teamId") ?? ""}
              onValueChange={(id) =>
                setValue("teamId", id, { shouldDirty: true })
              }
              disabled={!departmentId || availableTeams.length === 0}
            >
              <SelectTrigger id="profile-team-trigger" aria-label="팀 선택">
                <SelectValue
                  placeholder={
                    !departmentId
                      ? "먼저 부서를 선택하세요"
                      : availableTeams.length === 0
                        ? "이 부서에는 팀이 없어요"
                        : "팀 선택"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {availableTeams.map((team) => (
                  <SelectItem key={team.id} value={team.id}>
                    {team.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={onClose} disabled={isPending}>
              취소
            </Button>
            <Button type="submit" disabled={isPending || (!isDirty && !showLegacyWarning)}>
              {isPending ? "저장 중…" : "저장"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
