import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, GripVertical, Trash2 } from "lucide-react";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useDepartmentTree } from "@/hooks/useDepartmentTree";
import { departmentKeys } from "@/queries/departmentKeys";
import { departmentService } from "@/services/departmentService";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type {
  AdminDepartment,
  AdminDepartmentTreeNode,
  AdminDepartmentTreeResponse,
  AdminTeam,
} from "@/types/department";
import { cn } from "@/utils/cn";

/**
 * 사내 부서·팀 관리 어드민 페이지.
 *
 * Master-detail 레이아웃 — 좌측 부서 / 우측 선택 부서의 팀.
 * 정렬은 드래그 앤 드롭 (핸들 잡고 위아래로 이동, Space + 방향키 키보드 지원).
 * soft-delete 는 `isActive=false` 토글이 대신한다.
 */
export function DepartmentsPage() {
  const queryClient = useQueryClient();
  const { departments, isLoading, isError, refetch } = useDepartmentTree({ admin: true });
  const [selectedDeptId, setSelectedDeptId] = useState<string | null>(null);
  const [newDeptName, setNewDeptName] = useState("");
  const [newTeamName, setNewTeamName] = useState("");
  // 삭제 확인 모달 상태 — 부서/팀 둘 중 하나만 동시에 열린다.
  const [deleteTarget, setDeleteTarget] = useState<
    | { kind: "department"; id: string; name: string }
    | { kind: "team"; id: string; name: string }
    | null
  >(null);

  // 트리가 로드/변경되면 선택 보정: 선택된 부서가 사라졌으면 첫 부서로 이동.
  useEffect(() => {
    if (departments.length === 0) {
      setSelectedDeptId(null);
      return;
    }
    const exists = departments.some((node) => node.department.id === selectedDeptId);
    if (!selectedDeptId || !exists) {
      setSelectedDeptId(departments[0].department.id);
    }
  }, [departments, selectedDeptId]);

  const invalidateTree = () => {
    queryClient.invalidateQueries({ queryKey: departmentKeys.adminTree() });
    // signup 에서 쓰는 공개 트리도 동시 무효화해 UI 일관성을 유지한다.
    queryClient.invalidateQueries({ queryKey: departmentKeys.tree() });
  };

  const selectedNode = departments.find((d) => d.department.id === selectedDeptId) ?? null;

  // DnD 센서 — 포인터는 4px 이동 후 드래그로 간주 (클릭과 구분). 키보드는 Space 로 잡고 ↑/↓ 로 이동.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  // --- 부서 CRUD ---
  const createDepartment = useMutation({
    mutationFn: (name: string) => departmentService.createDepartment({ name }),
    onSuccess: () => {
      toast.success("부서를 추가했어요");
      setNewDeptName("");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "부서 추가에 실패했어요")),
  });

  const updateDepartment = useMutation({
    mutationFn: (vars: { id: string; name?: string; displayOrder?: number }) =>
      departmentService.updateDepartment(vars.id, {
        name: vars.name,
        displayOrder: vars.displayOrder,
      }),
    onSuccess: () => {
      toast.success("부서를 수정했어요");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "부서 수정에 실패했어요")),
  });

  const toggleDepartmentActive = useMutation({
    mutationFn: (vars: { id: string; isActive: boolean }) =>
      departmentService.setDepartmentActive(vars.id, vars.isActive),
    onSuccess: (_, vars) => {
      toast.success(vars.isActive ? "부서를 활성화했어요" : "부서를 비활성화했어요");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "부서 상태 변경에 실패했어요")),
  });

  // --- 팀 CRUD ---
  const createTeam = useMutation({
    mutationFn: (vars: { departmentId: string; name: string }) =>
      departmentService.createTeam(vars.departmentId, { name: vars.name }),
    onSuccess: () => {
      toast.success("팀을 추가했어요");
      setNewTeamName("");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "팀 추가에 실패했어요")),
  });

  const updateTeam = useMutation({
    mutationFn: (vars: {
      id: string;
      name?: string;
      displayOrder?: number;
      departmentId?: string;
    }) =>
      departmentService.updateTeam(vars.id, {
        name: vars.name,
        displayOrder: vars.displayOrder,
        departmentId: vars.departmentId,
      }),
    onSuccess: () => {
      toast.success("팀을 수정했어요");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "팀 수정에 실패했어요")),
  });

  const toggleTeamActive = useMutation({
    mutationFn: (vars: { id: string; isActive: boolean }) =>
      departmentService.setTeamActive(vars.id, vars.isActive),
    onSuccess: (_, vars) => {
      toast.success(vars.isActive ? "팀을 활성화했어요" : "팀을 비활성화했어요");
      invalidateTree();
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "팀 상태 변경에 실패했어요")),
  });

  // 부서/팀 물리 삭제 — BE 이중 가드(비활성 + 하위 팀/참조 사용자 없음) 통과 시에만 성공.
  const deleteDepartment = useMutation({
    mutationFn: (id: string) => departmentService.deleteDepartment(id),
    onSuccess: () => {
      toast.success("부서를 삭제했어요");
      invalidateTree();
      setDeleteTarget(null);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "부서 삭제에 실패했어요")),
  });

  const deleteTeam = useMutation({
    mutationFn: (id: string) => departmentService.deleteTeam(id),
    onSuccess: () => {
      toast.success("팀을 삭제했어요");
      invalidateTree();
      setDeleteTarget(null);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "팀 삭제에 실패했어요")),
  });

  // --- 드래그 앤 드롭 정렬 ---

  const reorderDepartments = async (newList: AdminDepartmentTreeNode[]) => {
    const queryKey = departmentKeys.adminTree();
    const previous = queryClient.getQueryData<AdminDepartmentTreeResponse>(queryKey);

    // 낙관적 업데이트 — displayOrder 는 새 인덱스로 즉시 반영해 재드래그 시 일관성을 유지한다.
    queryClient.setQueryData<AdminDepartmentTreeResponse>(queryKey, (old) =>
      old
        ? {
            ...old,
            content: newList.map((node, idx) => ({
              ...node,
              department: { ...node.department, displayOrder: idx },
            })),
          }
        : old,
    );

    // 실제 순서가 바뀐 항목만 PUT — 이미 올바른 자리에 있던 항목은 네트워크 낭비.
    const changed = newList
      .map((node, idx) => ({ id: node.department.id, order: idx, prev: node.department.displayOrder }))
      .filter((x) => x.order !== x.prev);

    if (changed.length === 0) return;

    try {
      await Promise.all(
        changed.map((c) => departmentService.updateDepartment(c.id, { displayOrder: c.order })),
      );
      toast.success("부서 순서를 바꿨어요");
      invalidateTree();
    } catch (err) {
      queryClient.setQueryData(queryKey, previous);
      toast.error(userFriendlyMessage(err, "부서 순서 변경에 실패했어요"));
    }
  };

  const reorderTeams = async (deptId: string, newTeams: AdminTeam[]) => {
    const queryKey = departmentKeys.adminTree();
    const previous = queryClient.getQueryData<AdminDepartmentTreeResponse>(queryKey);

    queryClient.setQueryData<AdminDepartmentTreeResponse>(queryKey, (old) =>
      old
        ? {
            ...old,
            content: old.content.map((node) =>
              node.department.id === deptId
                ? {
                    ...node,
                    teams: newTeams.map((t, idx) => ({ ...t, displayOrder: idx })),
                  }
                : node,
            ),
          }
        : old,
    );

    const changed = newTeams
      .map((t, idx) => ({ id: t.id, order: idx, prev: t.displayOrder }))
      .filter((x) => x.order !== x.prev);

    if (changed.length === 0) return;

    try {
      await Promise.all(
        changed.map((c) => departmentService.updateTeam(c.id, { displayOrder: c.order })),
      );
      toast.success("팀 순서를 바꿨어요");
      invalidateTree();
    } catch (err) {
      queryClient.setQueryData(queryKey, previous);
      toast.error(userFriendlyMessage(err, "팀 순서 변경에 실패했어요"));
    }
  };

  const handleDepartmentDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = departments.findIndex((n) => n.department.id === active.id);
    const newIndex = departments.findIndex((n) => n.department.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    reorderDepartments(arrayMove(departments, oldIndex, newIndex));
  };

  const handleTeamDragEnd = (event: DragEndEvent) => {
    if (!selectedNode) return;
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = selectedNode.teams.findIndex((t) => t.id === active.id);
    const newIndex = selectedNode.teams.findIndex((t) => t.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    reorderTeams(selectedNode.department.id, arrayMove(selectedNode.teams, oldIndex, newIndex));
  };

  return (
    <div className="p-6 space-y-4" data-testid="departments-page">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold">사내 부서·팀</h1>
          <p className="text-sm text-muted-foreground">
            회원가입 / 프로필 편집의 부서·팀 드롭다운에 노출될 옵션을 관리합니다. 비활성 부서는 가입 폼에서 숨겨져요.
          </p>
        </div>
      </header>

      {isError && (
        <div
          role="alert"
          className="rounded-lg bg-[var(--status-danger-bg)] p-3 text-sm text-[var(--status-danger-text)] flex items-center justify-between gap-2"
        >
          <span>부서 목록을 불러오지 못했어요</span>
          <Button size="sm" variant="ghost" onClick={() => refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      <div className="grid grid-cols-[16rem_1fr] gap-4">
        {/* 좌측 — 부서 리스트 */}
        <aside
          className="sticky top-4 self-start rounded-2xl border bg-card p-3 space-y-2"
          aria-label="부서 목록"
        >
          <div className="flex items-center gap-2">
            <Input
              value={newDeptName}
              onChange={(e) => setNewDeptName(e.target.value)}
              placeholder="새 부서 이름"
              aria-label="새 부서 이름"
              onKeyDown={(e) => {
                if (e.key === "Enter" && newDeptName.trim()) {
                  createDepartment.mutate(newDeptName.trim());
                }
              }}
            />
            <Button
              size="icon"
              aria-label="부서 추가"
              onClick={() => {
                if (!newDeptName.trim()) return;
                createDepartment.mutate(newDeptName.trim());
              }}
              disabled={createDepartment.isPending || !newDeptName.trim()}
            >
              <Plus size={16} />
            </Button>
          </div>

          {isLoading ? (
            <div className="text-sm text-muted-foreground p-2">부서 목록을 불러오는 중...</div>
          ) : departments.length === 0 ? (
            <div className="text-sm text-muted-foreground p-2">
              아직 등록된 부서가 없어요. 위 입력창에서 추가하세요.
            </div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDepartmentDragEnd}
            >
              <SortableContext
                items={departments.map((n) => n.department.id)}
                strategy={verticalListSortingStrategy}
              >
                <ul className="space-y-1" aria-label="부서 정렬">
                  {departments.map((node) => (
                    <DepartmentRow
                      key={node.department.id}
                      dept={node.department}
                      selected={selectedDeptId === node.department.id}
                      onSelect={() => setSelectedDeptId(node.department.id)}
                      onRename={(name) =>
                        updateDepartment.mutate({ id: node.department.id, name })
                      }
                      onToggleActive={(isActive) =>
                        toggleDepartmentActive.mutate({ id: node.department.id, isActive })
                      }
                      onDelete={() =>
                        setDeleteTarget({
                          kind: "department",
                          id: node.department.id,
                          name: node.department.name,
                        })
                      }
                    />
                  ))}
                </ul>
              </SortableContext>
            </DndContext>
          )}
        </aside>

        {/* 우측 — 팀 리스트 */}
        <section
          className="rounded-2xl border bg-card p-4 space-y-3"
          aria-label="팀 목록"
        >
          {selectedNode ? (
            <>
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-base font-semibold">
                  {selectedNode.department.name}
                  <span className="ml-2 text-xs text-muted-foreground">팀 {selectedNode.teams.length}개</span>
                </h2>
                <div className="flex items-center gap-2">
                  <Input
                    value={newTeamName}
                    onChange={(e) => setNewTeamName(e.target.value)}
                    placeholder="새 팀 이름"
                    aria-label="새 팀 이름"
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && newTeamName.trim()) {
                        createTeam.mutate({
                          departmentId: selectedNode.department.id,
                          name: newTeamName.trim(),
                        });
                      }
                    }}
                  />
                  <Button
                    size="sm"
                    onClick={() => {
                      if (!newTeamName.trim()) return;
                      createTeam.mutate({
                        departmentId: selectedNode.department.id,
                        name: newTeamName.trim(),
                      });
                    }}
                    disabled={createTeam.isPending || !newTeamName.trim()}
                  >
                    팀 추가
                  </Button>
                </div>
              </div>

              {selectedNode.teams.length === 0 ? (
                <div className="text-sm text-muted-foreground p-3 rounded-lg bg-muted/40">
                  이 부서에는 아직 팀이 없어요.
                </div>
              ) : (
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragEnd={handleTeamDragEnd}
                >
                  <SortableContext
                    items={selectedNode.teams.map((t) => t.id)}
                    strategy={verticalListSortingStrategy}
                  >
                    <ul className="space-y-2" aria-label="팀 정렬">
                      {selectedNode.teams.map((team) => (
                        <TeamRow
                          key={team.id}
                          team={team}
                          allDepartments={departments.map((n) => n.department)}
                          onRename={(name) => updateTeam.mutate({ id: team.id, name })}
                          onMove={(departmentId) =>
                            updateTeam.mutate({ id: team.id, departmentId })
                          }
                          onToggleActive={(isActive) =>
                            toggleTeamActive.mutate({ id: team.id, isActive })
                          }
                          onDelete={() =>
                            setDeleteTarget({ kind: "team", id: team.id, name: team.name })
                          }
                        />
                      ))}
                    </ul>
                  </SortableContext>
                </DndContext>
              )}
            </>
          ) : (
            <div className="text-sm text-muted-foreground p-4">
              왼쪽에서 부서를 선택하면 팀 목록을 볼 수 있어요.
            </div>
          )}
        </section>
      </div>

      <ConfirmModal
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title={deleteTarget?.kind === "department" ? "부서 삭제" : "팀 삭제"}
        description={
          deleteTarget
            ? `"${deleteTarget.name}"을(를) 정말 삭제할까요? 이 동작은 되돌릴 수 없어요. 하위 팀이나 소속 사용자가 있으면 삭제가 거부됩니다.`
            : undefined
        }
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          if (!deleteTarget) return;
          if (deleteTarget.kind === "department") {
            deleteDepartment.mutate(deleteTarget.id);
          } else {
            deleteTeam.mutate(deleteTarget.id);
          }
        }}
      />
    </div>
  );
}

interface DepartmentRowProps {
  dept: AdminDepartment;
  selected: boolean;
  onSelect: () => void;
  onRename: (name: string) => void;
  onToggleActive: (isActive: boolean) => void;
  /** 부서 물리 삭제 요청 — 비활성 상태에서만 호출된다. */
  onDelete: () => void;
}

function DepartmentRow({
  dept,
  selected,
  onSelect,
  onRename,
  onToggleActive,
  onDelete,
}: DepartmentRowProps) {
  const [name, setName] = useState(dept.name);

  useEffect(() => {
    setName(dept.name);
  }, [dept.name]);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: dept.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const commitName = () => {
    const trimmed = name.trim();
    if (trimmed && trimmed !== dept.name) onRename(trimmed);
    else setName(dept.name);
  };

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-lg border px-2 py-2 cursor-pointer transition-colors",
        selected ? "bg-primary/10 border-primary/40" : "bg-background hover:bg-muted/50",
        isDragging && "opacity-60 shadow-lg",
      )}
      onClick={onSelect}
    >
      <div className="flex items-center gap-2">
        <button
          type="button"
          className="flex h-8 w-5 shrink-0 cursor-grab items-center justify-center text-muted-foreground hover:text-foreground active:cursor-grabbing touch-none"
          aria-label={`${dept.name} 순서 이동 핸들`}
          onClick={(e) => e.stopPropagation()}
          {...attributes}
          {...listeners}
        >
          <GripVertical size={14} />
        </button>
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          onBlur={commitName}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.currentTarget.blur();
            }
          }}
          aria-label={`${dept.name} 이름`}
          className="h-8 flex-1"
          onClick={(e) => e.stopPropagation()}
        />
      </div>
      <div className="mt-1.5 flex items-center gap-2">
        {/* 비활성 부서만 삭제 버튼 노출 — 실수 방지 + BE 가드와 일치. */}
        {!dept.isActive && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs text-destructive hover:bg-destructive/10"
            aria-label={`${dept.name} 삭제`}
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 size={12} className="mr-1" />
            삭제
          </Button>
        )}
        <label
          className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground"
          onClick={(e) => e.stopPropagation()}
        >
          활성
          <Switch
            checked={dept.isActive}
            onCheckedChange={(v) => onToggleActive(v)}
            aria-label={`${dept.name} 활성 상태`}
          />
        </label>
      </div>
    </li>
  );
}

interface TeamRowProps {
  team: AdminTeam;
  allDepartments: AdminDepartment[];
  onRename: (name: string) => void;
  onMove: (departmentId: string) => void;
  onToggleActive: (isActive: boolean) => void;
  /** 팀 물리 삭제 요청 — 비활성 상태에서만 호출된다. */
  onDelete: () => void;
}

function TeamRow({
  team,
  allDepartments,
  onRename,
  onMove,
  onToggleActive,
  onDelete,
}: TeamRowProps) {
  const [name, setName] = useState(team.name);

  useEffect(() => {
    setName(team.name);
  }, [team.name]);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: team.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const commitName = () => {
    const trimmed = name.trim();
    if (trimmed && trimmed !== team.name) onRename(trimmed);
    else setName(team.name);
  };

  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-lg border px-3 py-2 bg-background flex flex-wrap items-center gap-2",
        isDragging && "opacity-60 shadow-lg",
      )}
    >
      <button
        type="button"
        className="flex h-8 w-5 shrink-0 cursor-grab items-center justify-center text-muted-foreground hover:text-foreground active:cursor-grabbing touch-none"
        aria-label={`${team.name} 순서 이동 핸들`}
        {...attributes}
        {...listeners}
      >
        <GripVertical size={14} />
      </button>
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        onBlur={commitName}
        onKeyDown={(e) => {
          if (e.key === "Enter") e.currentTarget.blur();
        }}
        aria-label={`${team.name} 이름`}
        className="h-8 flex-1 min-w-[10rem]"
      />
      <label className="flex items-center gap-1 text-xs text-muted-foreground">
        부서 이동
        <Select value={team.departmentId} onValueChange={(id) => onMove(id)}>
          <SelectTrigger
            aria-label={`${team.name} 부서 이동`}
            className="h-7 w-40"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {allDepartments.map((d) => (
              <SelectItem key={d.id} value={d.id}>
                {d.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </label>
      {/* 비활성 팀만 삭제 버튼 노출. */}
      {!team.isActive && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-7 px-2 text-xs text-destructive hover:bg-destructive/10 ml-auto"
          aria-label={`${team.name} 삭제`}
          onClick={onDelete}
        >
          <Trash2 size={12} className="mr-1" />
          삭제
        </Button>
      )}
      <label className={cn("flex items-center gap-1.5 text-xs text-muted-foreground", team.isActive && "ml-auto")}>
        활성
        <Switch
          checked={team.isActive}
          onCheckedChange={(v) => onToggleActive(v)}
          aria-label={`${team.name} 활성 상태`}
        />
      </label>
    </li>
  );
}
