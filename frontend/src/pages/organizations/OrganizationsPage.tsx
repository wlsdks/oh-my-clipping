import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { organizationKeys } from "@/queries/organizationKeys";
import { organizationService } from "@/services/organizationService";
import {
  ORGANIZATION_TYPE_LABELS,
  type Organization,
  type OrganizationType,
  type OrgOrigin,
} from "@/types/organization";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { useTabSync } from "@/hooks/useTabSync";
import { OrganizationFormModal } from "./OrganizationFormModal";
import { BackfillPanel } from "./BackfillPanel";

const TYPE_FILTER_VALUES: Array<OrganizationType | "ALL"> = [
  "ALL",
  "COMPETITOR",
  "CUSTOMER",
  "PARTNER",
  "OTHER",
];

const TYPE_FILTER_LABELS: Record<OrganizationType | "ALL", string> = {
  ALL: "전체",
  ...ORGANIZATION_TYPE_LABELS,
};

const ORIGIN_LABELS: Record<OrgOrigin, string> = {
  user_wizard: "유저 신청",
  admin_created: "관리자 생성",
  competitor_mirror: "경쟁사 미러",
  backfill: "자동 생성",
  legacy: "레거시",
};

const VALID_TABS = ["list", "backfill"] as const;

/** `/admin/organizations` — 외부 조직 (경쟁사/고객사/파트너) 관리 페이지. */
export function OrganizationsPage() {
  const qc = useQueryClient();
  const [activeTab, setActiveTab] = useTabSync("list", VALID_TABS);
  const [typeFilter, setTypeFilter] = useState<OrganizationType | "ALL">("ALL");
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Organization | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Organization | null>(null);

  const queryType = typeFilter === "ALL" ? undefined : typeFilter;

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: organizationKeys.list(queryType),
    queryFn: () => organizationService.list(queryType),
  });

  const organizations = data?.content ?? [];

  const { mutate: deleteOrganization } = useMutation({
    mutationFn: (id: string) => organizationService.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: organizationKeys.all });
      toast.success("조직이 삭제됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제에 실패했어요")),
  });

  const handleAdd = () => {
    setEditTarget(null);
    setFormOpen(true);
  };

  const handleEdit = (org: Organization) => {
    setEditTarget(org);
    setFormOpen(true);
  };

  // usageCount 에 따라 ConfirmModal 텍스트를 분기한다.
  // 백엔드 DELETE 가 category_organizations FK ON DELETE CASCADE 로 링크 row 를 자동 제거하므로
  // "모든 카테고리 링크가 해제됩니다" 문구는 실제 동작과 일치한다.
  const deleteDescription =
    deleteTarget && deleteTarget.usageCount > 0
      ? `"${deleteTarget.name}"은(는) ${deleteTarget.usageCount}개 카테고리에서 사용 중입니다. 강제 삭제하시겠습니까? 삭제하면 모든 카테고리 링크가 해제됩니다.`
      : `"${deleteTarget?.name}"을(를) 삭제하면 연결된 카테고리의 조직 링크도 함께 제거돼요`;

  const deleteConfirmLabel =
    deleteTarget && deleteTarget.usageCount > 0 ? "강제 삭제" : "삭제";

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 크로스-링크 안내 배너 */}
      <div className="bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)] border rounded-xl p-3 text-sm">
        <strong>관심 기업</strong>은 뉴스 구독 타겟(고객사·파트너).{" "}
        <strong>경쟁사</strong>는{" "}
        <Link to="/admin/competitors" className="underline hover:opacity-80">
          경쟁사 관리
        </Link>
        에서 따로 관리합니다.
      </div>

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">관심 기업</h1>
          <p className="text-sm text-muted-foreground mt-1">
            뉴스 추적 대상 경쟁사·고객사·파트너 기업 마스터 — 유저 구독에서 추가되거나 관리자가 직접 등록합니다
          </p>
        </div>
        <Button onClick={handleAdd} size="sm">
          <Plus className="mr-1.5 h-4 w-4" />
          기업 추가
        </Button>
      </div>

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as typeof activeTab)}>
        <TabsList>
          <TabsTrigger value="list">조직 목록</TabsTrigger>
          <TabsTrigger value="backfill">기존 구독 가져오기</TabsTrigger>
        </TabsList>

        <TabsContent value="list" className="mt-4 space-y-4">
          {/* 종류 필터 칩 — 토글 버튼 그룹 (true 탭이 아니라서 role="tab" 금지; nested tablist→tabpanel→tablist ARIA 위반 회피). */}
          <div className="flex flex-wrap gap-2" aria-label="조직 종류 필터">
            {TYPE_FILTER_VALUES.map((value) => {
              const isActive = typeFilter === value;
              return (
                <button
                  key={value}
                  type="button"
                  aria-pressed={isActive}
                  onClick={() => setTypeFilter(value)}
                  className={
                    "rounded-full px-4 py-1.5 text-sm transition-colors " +
                    (isActive
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-muted/80")
                  }
                >
                  {TYPE_FILTER_LABELS[value]}
                </button>
              );
            })}
          </div>

          {isLoading ? (
            <div className="space-y-3" role="status" aria-live="polite">
              <span className="sr-only">로딩 중...</span>
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-14 animate-pulse rounded-xl bg-muted" />
              ))}
            </div>
          ) : isError ? (
            <div className="flex flex-col items-center gap-3 p-12 text-center">
              <p className="text-sm text-muted-foreground">조직 목록을 불러오지 못했어요</p>
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                다시 시도
              </Button>
            </div>
          ) : organizations.length === 0 ? (
            <div className="flex flex-col items-center gap-4 py-16 text-center">
              <p className="text-sm text-muted-foreground">
                {typeFilter === "ALL"
                  ? "아직 등록된 조직이 없어요"
                  : `${TYPE_FILTER_LABELS[typeFilter]} 종류의 조직이 없어요`}
              </p>
              {typeFilter === "ALL" && (
                <Button size="sm" onClick={handleAdd}>
                  <Plus className="mr-1.5 h-4 w-4" />
                  조직 추가
                </Button>
              )}
            </div>
          ) : (
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>이름</TableHead>
                    <TableHead>종류</TableHead>
                    <TableHead>출처</TableHead>
                    <TableHead>사용</TableHead>
                    <TableHead>도메인</TableHead>
                    <TableHead>설명</TableHead>
                    <TableHead className="text-right">액션</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {organizations.map((org) => (
                    <OrganizationRow
                      key={org.id}
                      organization={org}
                      onEdit={handleEdit}
                      onDelete={setDeleteTarget}
                    />
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>

        <TabsContent value="backfill" className="mt-4">
          <BackfillPanel />
        </TabsContent>
      </Tabs>

      <OrganizationFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        organization={editTarget}
      />

      <ConfirmModal
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title="조직을 삭제할까요?"
        description={deleteDescription}
        confirmLabel={deleteConfirmLabel}
        variant="destructive"
        onConfirm={() => {
          if (deleteTarget) {
            deleteOrganization(deleteTarget.id);
            setDeleteTarget(null);
          }
        }}
      />
    </div>
  );
}

/* ── 테이블 행 서브 컴포넌트 ── */

interface RowProps {
  organization: Organization;
  onEdit: (org: Organization) => void;
  onDelete: (org: Organization) => void;
}

function OrganizationRow({ organization, onEdit, onDelete }: RowProps) {
  const isCompetitor = organization.type === "COMPETITOR";

  // origin 레이블 변환 — null 이면 "-" 표시
  const originLabel = organization.origin ? ORIGIN_LABELS[organization.origin] : null;

  return (
    <TableRow>
      <TableCell className="font-medium">{organization.name}</TableCell>
      <TableCell>
        <Badge variant="secondary" className="text-xs">
          {ORGANIZATION_TYPE_LABELS[organization.type]}
        </Badge>
      </TableCell>
      <TableCell>
        {originLabel ? (
          <Badge variant="secondary" className="text-xs">
            {originLabel}
          </Badge>
        ) : (
          <span className="text-muted-foreground">-</span>
        )}
      </TableCell>
      <TableCell className="text-muted-foreground">
        {`${organization.usageCount}개 카테고리`}
      </TableCell>
      <TableCell className="text-muted-foreground">
        {organization.domain || "-"}
      </TableCell>
      <TableCell className="max-w-[320px] truncate text-muted-foreground">
        {organization.description || "-"}
      </TableCell>
      <TableCell className="text-right">
        {isCompetitor ? (
          // 경쟁사는 "경쟁사 관리" 화면이 단일 관리점 — 여기서는 읽기 전용.
          <Link
            to="/admin/competitors"
            className="inline-flex items-center rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground hover:bg-muted/80"
            aria-label={`${organization.name} 편집은 경쟁사 관리에서 진행`}
          >
            경쟁사 관리에서 편집
          </Link>
        ) : (
          <div className="flex items-center justify-end gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => onEdit(organization)}
              aria-label={`${organization.name} 편집`}
            >
              <Pencil className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-destructive hover:text-destructive"
              onClick={() => onDelete(organization)}
              aria-label={`${organization.name} 삭제`}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        )}
      </TableCell>
    </TableRow>
  );
}
