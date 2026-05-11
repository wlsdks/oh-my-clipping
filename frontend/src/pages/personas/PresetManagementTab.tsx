import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { personaKeys } from "@/queries/personaKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { personaService } from "@/services/personaService";
import { categoryService } from "@/services/categoryService";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { PresetCard } from "./PresetCard";
import { PresetDetailModal } from "./PresetDetailModal";
import type { Persona } from "@/types/persona";

export function PresetManagementTab() {
  const [selectedPersona, setSelectedPersona] = useState<Persona | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [isCreateMode, setIsCreateMode] = useState(false);

  const { data: personas = [], isLoading } = useQuery({
    queryKey: personaKeys.lists(),
    queryFn: () => personaService.getAll()
  });

  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll()
  });

  // 구독 수 집계
  const subscriptionCountByPersona: Record<string, number> = {};
  for (const cat of categories) {
    if (!cat.personaId) continue;
    subscriptionCountByPersona[cat.personaId] = (subscriptionCountByPersona[cat.personaId] || 0) + 1;
  }

  const presets = personas.filter((p) => p.isPreset);
  const activeCount = presets.filter((p) => p.isActive).length;

  function handleCardClick(persona: Persona) {
    setSelectedPersona(persona);
    setIsCreateMode(false);
    setModalOpen(true);
  }

  function handleCreate() {
    setSelectedPersona(null);
    setIsCreateMode(true);
    setModalOpen(true);
  }

  if (isLoading) {
    return <div className="py-8 text-sm text-muted-foreground">불러오는 중...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          총 {presets.length}개 템플릿 &middot; 활성 {activeCount}개
        </p>
        <Button size="sm" onClick={handleCreate}>
          <Plus className="w-4 h-4 mr-1" />새 템플릿
        </Button>
      </div>

      {presets.length === 0 ? (
        <EmptyState title="아직 템플릿이 없어요" description="첫 번째 템플릿을 추가하세요" />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {presets.map((persona) => (
            <PresetCard
              key={persona.id}
              persona={persona}
              subscriptionCount={subscriptionCountByPersona[persona.id] || 0}
              onClick={() => handleCardClick(persona)}
            />
          ))}
        </div>
      )}

      <PresetDetailModal
        persona={isCreateMode ? null : selectedPersona}
        subscriptionCount={selectedPersona ? subscriptionCountByPersona[selectedPersona.id] || 0 : 0}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </div>
  );
}
