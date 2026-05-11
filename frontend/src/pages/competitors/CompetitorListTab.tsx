import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Info, Plus, Pencil, Trash2, RefreshCw } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { competitorKeys } from "@/queries/competitorKeys";
import { competitorService } from "@/services/competitorService";
import type { Competitor } from "@/types/competitor";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { CompetitorSummaryCards } from "./CompetitorSummaryCards";
import { CompetitorFormModal } from "./CompetitorFormModal";

const TIER_LABELS: Record<Competitor["tier"], string> = {
  DIRECT: "직접경쟁",
  ADJACENT: "인접",
  GLOBAL: "글로벌",
};

const MAX_VISIBLE_ALIASES = 3;

export function CompetitorListTab() {
  const qc = useQueryClient();
  const [deleteTarget, setDeleteTarget] = useState<Competitor | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Competitor | null>(null);

  // 경쟁사 목록 조회
  const { data: competitors = [], isLoading, isError, refetch } = useQuery({
    queryKey: competitorKeys.lists(),
    queryFn: () => competitorService.list(),
  });

  // 활성 상태 토글
  const { mutate: toggleActive } = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      competitorService.update(id, { isActive }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: competitorKeys.all });
      toast.success("상태가 변경됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "상태 변경에 실패했어요")),
  });

  // 경쟁사 삭제
  const { mutate: deleteCompetitor } = useMutation({
    mutationFn: (id: string) => competitorService.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: competitorKeys.all });
      toast.success("경쟁사가 삭제됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제에 실패했어요")),
  });

  // 수동 수집
  const { mutate: collect, isPending: isCollecting } = useMutation({
    mutationFn: () => competitorService.collect(),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: competitorKeys.all });
      toast.success(result.message ?? `${result.newArticles}건의 새 기사를 수집했어요`);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "수집에 실패했어요")),
  });

  const handleAdd = () => {
    setEditTarget(null);
    setFormOpen(true);
  };

  const handleEdit = (competitor: Competitor) => {
    setEditTarget(competitor);
    setFormOpen(true);
  };

  // 로딩 상태
  if (isLoading) {
    return (
      <div className="space-y-3" role="status" aria-live="polite">
        <span className="sr-only">로딩 중...</span>
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-14 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    );
  }

  // 에러 상태
  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-muted-foreground">경쟁사 목록을 불러오지 못했어요</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          다시 시도
        </Button>
      </div>
    );
  }

  // 빈 상태
  if (competitors.length === 0) {
    return (
      <>
        <div className="flex flex-col items-center gap-4 py-16 text-center">
          <p className="text-sm text-muted-foreground">아직 등록된 경쟁사가 없어요</p>
          <Button size="sm" onClick={handleAdd}>
            <Plus className="mr-1.5 h-4 w-4" />
            경쟁사 추가
          </Button>
        </div>
        <CompetitorFormModal
          open={formOpen}
          onClose={() => setFormOpen(false)}
          competitor={editTarget}
        />
      </>
    );
  }

  return (
    <div className="space-y-5">
      {/* 경쟁사 관리 = 단일 관리점. Organizations 테이블로 자동 mirror 됨을 안내한다. */}
      <div
        role="note"
        className="flex items-start gap-2 rounded-xl border bg-muted/40 p-3 text-xs text-muted-foreground"
      >
        <Info className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" aria-hidden />
        <p>
          경쟁사로 등록된 항목은{" "}
          <Link
            to="/admin/organizations"
            className="text-primary underline-offset-2 hover:underline"
          >
            관심 기업
          </Link>
          에도 자동으로 동기화돼요
        </p>
      </div>

      <CompetitorSummaryCards competitors={competitors} />

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          총 {competitors.length}개의 경쟁사
        </p>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => collect()}
            disabled={isCollecting}
          >
            <RefreshCw className={`mr-1.5 h-4 w-4 ${isCollecting ? "animate-spin" : ""}`} />
            {isCollecting ? "수집 중..." : "수동 수집"}
          </Button>
          <Button size="sm" onClick={handleAdd}>
            <Plus className="mr-1.5 h-4 w-4" />
            경쟁사 추가
          </Button>
        </div>
      </div>

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>이름</TableHead>
              <TableHead>등급</TableHead>
              <TableHead>별칭</TableHead>
              <TableHead className="text-center">수동 RSS</TableHead>
              <TableHead className="text-center">최근 24h</TableHead>
              <TableHead className="text-center">활성</TableHead>
              <TableHead className="text-right">액션</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {competitors.map((c) => (
              <CompetitorRow
                key={c.id}
                competitor={c}
                onEdit={handleEdit}
                onDelete={setDeleteTarget}
                onToggleActive={(isActive) => toggleActive({ id: c.id, isActive })}
              />
            ))}
          </TableBody>
        </Table>
      </div>

      {/* 삭제 확인 모달 */}
      <ConfirmModal
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title="경쟁사를 삭제할까요?"
        description={`"${deleteTarget?.name}"을(를) 삭제하면 수집된 뉴스 데이터도 함께 삭제돼요`}
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          if (deleteTarget) {
            deleteCompetitor(deleteTarget.id);
            setDeleteTarget(null);
          }
        }}
      />

      {/* 추가/수정 모달 */}
      <CompetitorFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        competitor={editTarget}
      />
    </div>
  );
}

/* ── 테이블 행 서브 컴포넌트 ── */

interface CompetitorRowProps {
  competitor: Competitor;
  onEdit: (c: Competitor) => void;
  onDelete: (c: Competitor) => void;
  onToggleActive: (isActive: boolean) => void;
}

function CompetitorRow({ competitor, onEdit, onDelete, onToggleActive }: CompetitorRowProps) {
  const visibleAliases = competitor.aliases.slice(0, MAX_VISIBLE_ALIASES);
  const overflowCount = competitor.aliases.length - MAX_VISIBLE_ALIASES;

  return (
    <TableRow>
      <TableCell className="font-medium">{competitor.name}</TableCell>
      <TableCell>
        <TierBadge tier={competitor.tier} />
      </TableCell>
      <TableCell>
        <div className="flex flex-wrap gap-1">
          {visibleAliases.map((kw) => (
            <Badge key={kw} variant="secondary" className="text-xs">
              {kw}
            </Badge>
          ))}
          {overflowCount > 0 && (
            <Badge variant="outline" className="text-xs">
              +{overflowCount}
            </Badge>
          )}
          {competitor.aliases.length === 0 && (
            <span className="text-xs text-muted-foreground">-</span>
          )}
        </div>
      </TableCell>
      <TableCell className="text-center tabular-nums">
        {competitor.rssFeeds.length}
      </TableCell>
      <TableCell className="text-center tabular-nums">
        {competitor.last24hCount}
      </TableCell>
      <TableCell className="text-center">
        <Switch
          checked={competitor.isActive}
          onCheckedChange={onToggleActive}
          aria-label={`${competitor.name} 활성 상태 토글`}
        />
      </TableCell>
      <TableCell className="text-right">
        <div className="flex items-center justify-end gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={() => onEdit(competitor)}
            aria-label={`${competitor.name} 편집`}
          >
            <Pencil className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-destructive hover:text-destructive"
            onClick={() => onDelete(competitor)}
            aria-label={`${competitor.name} 삭제`}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}

/* ── 등급 뱃지 ── */

function TierBadge({ tier }: { tier: Competitor["tier"] }) {
  const label = TIER_LABELS[tier];

  const variantMap: Record<Competitor["tier"], "default" | "secondary" | "outline"> = {
    DIRECT: "default",
    ADJACENT: "secondary",
    GLOBAL: "outline",
  };

  return (
    <Badge variant={variantMap[tier]} className="text-xs">
      {label}
    </Badge>
  );
}
