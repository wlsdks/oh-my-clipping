import type { Category } from "../types/admin";
import { formatKoreanDateTime } from "./dateTime";
import { formatSlackDestinationLabel } from "./slackChannel";

function formatCreatedAtLabel(value?: string | null): string {
  const formatted = formatKoreanDateTime(value);
  return formatted === "-" ? "시각 미상" : formatted.slice(0, 16);
}

export function buildCategoryLabelMap(categories: Category[]): Record<string, string> {
  const nameCounts = categories.reduce<Record<string, number>>((acc, category) => {
    acc[category.name] = (acc[category.name] ?? 0) + 1;
    return acc;
  }, {});

  return Object.fromEntries(
    categories.map((category) => [category.id, formatCategoryOptionLabel(category, nameCounts)])
  );
}

export function formatCategoryOptionLabel(category: Category, nameCounts?: Record<string, number>): string {
  const duplicateCount = nameCounts?.[category.name] ?? 1;
  if (duplicateCount <= 1) {
    return category.name;
  }

  const destination = formatSlackDestinationLabel(category.slackChannelId, {
    blankLabel: "Slack DM"
  });
  return `${category.name} · ${destination} · ${formatCreatedAtLabel(category.createdAt)}`;
}

export function formatCategorySummary(category: Category): string {
  return [
    `알림 ${formatSlackDestinationLabel(category.slackChannelId, { blankLabel: "Slack DM" })}`,
    `소스 ${category.sourceCount}개`,
    `최대 ${category.maxItems}건`
  ].join(" · ");
}
