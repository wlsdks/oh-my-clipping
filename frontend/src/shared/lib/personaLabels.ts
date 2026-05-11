import type { Persona } from "../types/admin";
import { formatKoreanDateTime } from "./dateTime";

const GENERATED_SUFFIX_PATTERN = /-[0-9a-f]{8}$/i;

function formatCreatedAtLabel(value?: string | null): string {
  const formatted = formatKoreanDateTime(value);
  return formatted === "-" ? "시각 미상" : formatted.slice(0, 16);
}

/**
 * 운영 승인 과정에서 붙는 내부 suffix를 사용자 표시용 이름에서 제거한다.
 */
export function stripGeneratedPersonaSuffix(name?: string | null): string {
  const trimmed = name?.trim() ?? "";
  if (!trimmed) return "";
  return trimmed.replace(GENERATED_SUFFIX_PATTERN, "").trim();
}

/**
 * 동일한 표시 이름이 여러 개일 때만 생성 시각을 붙여 구분 라벨을 만든다.
 */
export function buildPersonaLabelMap(personas: Persona[]): Record<string, string> {
  const nameCounts = personas.reduce<Record<string, number>>((acc, persona) => {
    const displayName = stripGeneratedPersonaSuffix(persona.name);
    acc[displayName] = (acc[displayName] ?? 0) + 1;
    return acc;
  }, {});

  return Object.fromEntries(personas.map((persona) => [persona.id, formatPersonaDisplayName(persona, nameCounts)]));
}

/**
 * 관리자/사용자 화면에 공통으로 쓸 페르소나 표시 이름을 만든다.
 */
export function formatPersonaDisplayName(
  persona: Pick<Persona, "name" | "createdAt">,
  nameCounts?: Record<string, number>
): string {
  const displayName = stripGeneratedPersonaSuffix(persona.name);
  const duplicateCount = nameCounts?.[displayName] ?? 1;
  if (duplicateCount <= 1) {
    return displayName || persona.name;
  }
  return `${displayName} · ${formatCreatedAtLabel(persona.createdAt)}`;
}
