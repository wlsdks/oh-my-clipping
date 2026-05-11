import { getHealthLevel } from "../sourceHelpers";
import type { Source } from "@/types/source";

export interface GroupedSources {
  connectionError: Source[];
  pendingApproval: Source[];
  active: Source[];
  archived: Source[];
}

/** getHealthLevel 기반으로 소스를 4개 그룹으로 분류한다 */
export function groupSources(sources: Source[]): GroupedSources {
  const result: GroupedSources = {
    connectionError: [],
    pendingApproval: [],
    active: [],
    archived: [],
  };

  for (const source of sources) {
    // verificationStatus FAILED는 항상 에러 그룹
    if (source.verificationStatus === "FAILED") {
      result.connectionError.push(source);
      continue;
    }
    const health = getHealthLevel(source);
    switch (health) {
      case "error":
        result.connectionError.push(source);
        break;
      case "pending":
        // 미승인은 승인 대기, 나머지 pending은 active에 표시
        if (!source.crawlApproved) result.pendingApproval.push(source);
        else result.active.push(source);
        break;
      case "healthy":
      case "warning":
        result.active.push(source);
        break;
      case "archived":
        result.archived.push(source);
        break;
    }
  }

  return result;
}
