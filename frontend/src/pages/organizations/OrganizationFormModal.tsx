import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { organizationService } from "@/services/organizationService";
import { organizationKeys } from "@/queries/organizationKeys";
import {
  ORGANIZATION_TYPE_LABELS,
  type Organization,
  type OrganizationType,
} from "@/types/organization";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// 경쟁사(COMPETITOR) 는 "경쟁사 관리" 화면이 단일 관리점이다.
// 조직 관리 모달에서는 CUSTOMER/PARTNER/OTHER 만 생성할 수 있도록 제한한다.
// (편집 케이스의 backward compat 을 위해 schema 는 COMPETITOR 도 허용한다.)
const CREATABLE_ORG_TYPE_VALUES = ["CUSTOMER", "PARTNER", "OTHER"] as const;
const ALL_ORG_TYPE_VALUES = ["COMPETITOR", ...CREATABLE_ORG_TYPE_VALUES] as const;

/** aliasText 필드를 파싱하여 trim + dedup + 빈줄 제거한 배열을 반환한다. */
function parseAliases(aliasText: string): string[] {
  const lines = aliasText.split("\n").map((s) => s.trim()).filter(Boolean);
  return Array.from(new Set(lines));
}

const schema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "이름을 입력하세요")
      .max(200, "이름은 최대 200자까지 입력할 수 있어요"),
    type: z.enum(ALL_ORG_TYPE_VALUES),
    domain: z
      .string()
      .trim()
      .max(255, "도메인은 최대 255자까지 입력할 수 있어요")
      .optional()
      .or(z.literal("")),
    description: z
      .string()
      .trim()
      .max(2000, "설명은 최대 2000자까지 입력할 수 있어요")
      .optional()
      .or(z.literal("")),
    aliasText: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (!data.aliasText) return;
    const parsed = parseAliases(data.aliasText);
    // 별칭 개수 상한 검사
    if (parsed.length > 20) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["aliasText"],
        message: "별칭은 최대 20개까지 입력할 수 있어요",
      });
      return;
    }
    // 개별 별칭 길이 상한 검사
    const tooLong = parsed.some((s) => s.length > 50);
    if (tooLong) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["aliasText"],
        message: "각 별칭은 최대 50자까지 입력할 수 있어요",
      });
    }
  });

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onClose: () => void;
  organization?: Organization | null;
}

/** 조직 생성/수정 모달. */
export function OrganizationFormModal({ open, onClose, organization }: Props) {
  const isEditMode = !!organization;
  const qc = useQueryClient();
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isValid },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: { name: "", type: "CUSTOMER", domain: "", description: "", aliasText: "" },
  });

  useEffect(() => {
    if (!open) return;
    if (organization) {
      reset({
        name: organization.name,
        type: organization.type,
        domain: organization.domain ?? "",
        description: organization.description ?? "",
        aliasText: organization.aliases?.join("\n") ?? "",
      });
    } else {
      reset({ name: "", type: "CUSTOMER", domain: "", description: "", aliasText: "" });
    }
  }, [open, organization, reset]);

  const { mutate: submitForm, isPending } = useMutation({
    mutationFn: (data: FormValues) => {
      // aliasText 를 parsed 배열로 변환하여 payload 에 포함한다.
      const aliases = parseAliases(data.aliasText ?? "");
      return isEditMode
        ? organizationService.update(organization!.id, {
            name: data.name,
            type: data.type,
            domain: data.domain ? data.domain : null,
            description: data.description ? data.description : null,
            aliases,
          })
        : organizationService.create({
            name: data.name,
            type: data.type,
            domain: data.domain || null,
            description: data.description || null,
          });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: organizationKeys.all });
      toast.success(isEditMode ? "조직이 수정됐어요" : "조직이 추가됐어요");
      onClose();
    },
    onError: (err) =>
      toast.error(userFriendlyMessage(err, isEditMode ? "수정에 실패했어요" : "추가에 실패했어요")),
  });

  const selectedType = watch("type");

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[min(70vh,640px)] overflow-y-auto p-0">
        <DialogHeader className="px-6 pt-6">
          <DialogTitle>{isEditMode ? "조직 수정" : "조직 추가"}</DialogTitle>
          <DialogDescription>
            {isEditMode
              ? "조직 정보를 수정하세요"
              : "경쟁사 / 고객사 / 파트너 등 분석에 사용할 외부 조직을 추가하세요"}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit((data) => submitForm(data))} className="space-y-5 px-6 pb-2">
          <div className="space-y-1.5">
            <Label htmlFor="org-name">이름</Label>
            <Input id="org-name" placeholder="조직 이름" {...register("name")} />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label>종류</Label>
            <Select
              value={selectedType}
              onValueChange={(v) => setValue("type", v as OrganizationType, { shouldValidate: true })}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {/* 편집 모드에서 기존 값이 COMPETITOR 이면 드롭다운에서 보이도록 포함한다 —
                    신규 생성 케이스에서는 CUSTOMER/PARTNER/OTHER 만 노출된다. */}
                {(isEditMode && organization?.type === "COMPETITOR"
                  ? ALL_ORG_TYPE_VALUES
                  : CREATABLE_ORG_TYPE_VALUES
                ).map((t) => (
                  <SelectItem key={t} value={t}>
                    {ORGANIZATION_TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="org-domain">
              도메인 <span className="text-muted-foreground font-normal">(선택)</span>
            </Label>
            <Input id="org-domain" placeholder="example.com" {...register("domain")} />
            {errors.domain && <p className="text-xs text-destructive">{errors.domain.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="org-description">
              설명 <span className="text-muted-foreground font-normal">(선택)</span>
            </Label>
            <Textarea
              id="org-description"
              placeholder="참고용 메모"
              rows={3}
              {...register("description")}
            />
            {errors.description && (
              <p className="text-xs text-destructive">{errors.description.message}</p>
            )}
          </div>

          {/* 별칭 편집은 edit 모드 전용 — create payload 가 aliases 를 받지 않으므로
              생성 시 입력받으면 조용히 버려진다. 생성 후 수정에서 설정하도록 유도한다. */}
          {isEditMode && (
            <div className="space-y-1.5">
              <Label htmlFor="org-alias-text">
                별칭 <span className="text-muted-foreground font-normal">(선택)</span>
              </Label>
              <Textarea
                id="org-alias-text"
                placeholder={"한 줄에 하나씩\n예:\nMegaCorp\nsamsung\nSEC"}
                rows={5}
                {...register("aliasText")}
              />
              <p className="text-xs text-muted-foreground">
                여기 입력한 별칭이 기사 본문 매칭에 사용됩니다
              </p>
              {errors.aliasText && (
                <p className="text-xs text-destructive">{errors.aliasText.message}</p>
              )}
            </div>
          )}
        </form>

        <DialogFooter className="px-6 pb-6">
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            취소
          </Button>
          <Button
            onClick={handleSubmit((data) => submitForm(data))}
            disabled={isPending || !isValid}
          >
            {isPending && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
            {isPending ? "저장 중..." : "저장"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
