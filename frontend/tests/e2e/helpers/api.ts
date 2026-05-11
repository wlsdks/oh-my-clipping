import type { APIRequestContext } from "@playwright/test";

// ── Test Data Generators ──────────────────────────────────────

const LABEL_PREFIX = ["새벽", "잔잔한", "푸른", "다온", "은빛", "맑은", "단단한", "고른"];
const LABEL_MIDDLE = ["협업", "공급망", "브랜드", "보안", "운영", "시장", "전략", "물류"];
const LABEL_SUFFIX = ["브리핑", "모니터링", "리포트", "다이제스트", "체크", "인사이트", "요약", "업데이트"];
let labelSequence = Date.now() % 1000;

/** Generate a unique, human-readable label for test data */
export function buildTestLabel(prefix: string) {
  labelSequence += 1;
  const first = LABEL_PREFIX[labelSequence % LABEL_PREFIX.length];
  const second = LABEL_MIDDLE[Math.floor(labelSequence / LABEL_PREFIX.length) % LABEL_MIDDLE.length];
  const third =
    LABEL_SUFFIX[Math.floor(labelSequence / (LABEL_PREFIX.length * LABEL_MIDDLE.length)) % LABEL_SUFFIX.length];
  return `${prefix} ${first} ${second} ${third}`;
}

/** Convert a label to a URL-safe query token */
export function buildQueryToken(label: string) {
  return encodeURIComponent(label.toLowerCase().replace(/\s+/g, "-"));
}

// ── Login Helpers ─────────────────────────────────────────────

export async function loginAsAdmin(request: APIRequestContext) {
  const shortcuts = await request.get("/api/public/dev/login-shortcuts");
  const data = (await shortcuts.json()) as {
    shortcuts: Array<{ scope: string; username: string; password: string }>;
  };
  const admin = data.shortcuts.find((s) => s.scope === "admin");
  if (!admin) throw new Error("No admin shortcut found");
  await request.post("/login", {
    form: { username: admin.username, password: admin.password },
  });
}

export async function loginAsUser(request: APIRequestContext) {
  const shortcuts = await request.get("/api/public/dev/login-shortcuts");
  const data = (await shortcuts.json()) as {
    shortcuts: Array<{ scope: string; username: string; password: string }>;
  };
  const user = data.shortcuts.find((s) => s.scope === "user");
  if (!user) throw new Error("No user shortcut found");
  await request.post("/login", {
    form: { username: user.username, password: user.password },
  });
}

// ── CRUD Helpers ──────────────────────────────────────────────

export async function createSource(
  request: APIRequestContext,
  data: { name: string; url: string; categoryId?: string },
) {
  const res = await request.post("/api/admin/sources", { data });
  if (!res.ok()) throw new Error(`Failed to create source: ${res.status()}`);
  return res.json() as Promise<{ id: string; name: string }>;
}

export async function deleteSource(request: APIRequestContext, id: string) {
  await request.delete(`/api/admin/sources/${encodeURIComponent(id)}`);
}

export async function createCategory(
  request: APIRequestContext,
  data: { name: string; description?: string; slackChannelId?: string; maxItems?: number },
) {
  const res = await request.post("/api/admin/categories", { data });
  if (!res.ok()) throw new Error(`Failed to create category: ${res.status()}`);
  return res.json() as Promise<{ id: string; name: string }>;
}

export async function deleteCategory(request: APIRequestContext, id: string) {
  await request.delete(`/api/admin/categories/${encodeURIComponent(id)}`);
}

export async function createPersona(
  request: APIRequestContext,
  data: { name: string; systemPrompt: string; maxItems?: number },
) {
  const res = await request.post("/api/admin/personas", { data });
  if (!res.ok()) throw new Error(`Failed to create persona: ${res.status()}`);
  return res.json() as Promise<{ id: string; name: string }>;
}

export async function deletePersona(request: APIRequestContext, id: string) {
  await request.delete(`/api/admin/personas/${encodeURIComponent(id)}`);
}

export async function updatePersona(
  request: APIRequestContext,
  id: string,
  data: { systemPrompt?: string; name?: string; description?: string },
) {
  const res = await request.put(`/api/admin/personas/${encodeURIComponent(id)}`, { data });
  if (!res.ok()) throw new Error(`Failed to update persona: ${res.status()}`);
  return res.json() as Promise<{ id: string; name: string; systemPrompt: string }>;
}

export async function listPersonas(request: APIRequestContext) {
  const res = await request.get("/api/admin/personas");
  if (!res.ok()) return [];
  return (await res.json()) as Array<{ id: string; name: string; systemPrompt?: string }>;
}

export async function listCategories(request: APIRequestContext) {
  const res = await request.get("/api/admin/categories?size=200");
  if (!res.ok()) return [];
  const body = (await res.json()) as
    | Array<{ id: string; name: string }>
    | { content?: Array<{ id: string; name: string }>; items?: Array<{ id: string; name: string }> };
  if (Array.isArray(body)) return body;
  return body.content ?? body.items ?? [];
}

export async function listSources(request: APIRequestContext) {
  const res = await request.get("/api/admin/sources?size=200");
  if (!res.ok()) return [];
  const body = (await res.json()) as
    | Array<{ id: string; name: string }>
    | { content?: Array<{ id: string; name: string }>; items?: Array<{ id: string; name: string }> };
  if (Array.isArray(body)) return body;
  return body.content ?? body.items ?? [];
}

// ── Global Test Artifact Cleanup ──────────────────────────────

/**
 * Delete every category and persona whose name matches a known E2E test prefix.
 *
 * Best-effort — individual delete failures (FK conflicts, already gone) do
 * not abort the sweep. Intended for local/CI databases; never run this
 * against production, where test-labeled rows should not exist in the first
 * place.
 */
export async function wipeE2eArtifacts(request: APIRequestContext) {
  const matchesE2ePrefix = (name: string) => {
    const lower = name.toLowerCase();
    return lower.startsWith("e2e-") || lower.startsWith("e2e ");
  };

  let sourcesDeleted = 0;
  let categoriesDeleted = 0;
  let personasDeleted = 0;

  // Delete sources first — rss_sources has a FK to batch_categories without CASCADE,
  // so a category delete fails while its sources still point at it.
  for (const src of await listSources(request)) {
    if (matchesE2ePrefix(src.name)) {
      const res = await request.delete(`/api/admin/sources/${encodeURIComponent(src.id)}`);
      if (res.ok()) sourcesDeleted += 1;
    }
  }

  for (const cat of await listCategories(request)) {
    if (matchesE2ePrefix(cat.name)) {
      const res = await request.delete(`/api/admin/categories/${encodeURIComponent(cat.id)}`);
      if (res.ok()) categoriesDeleted += 1;
    }
  }

  for (const persona of await listPersonas(request)) {
    if (matchesE2ePrefix(persona.name)) {
      const res = await request.delete(`/api/admin/personas/${encodeURIComponent(persona.id)}`);
      if (res.ok()) personasDeleted += 1;
    }
  }

  return { sourcesDeleted, categoriesDeleted, personasDeleted };
}

export async function listCompetitors(request: APIRequestContext) {
  const res = await request.get("/api/admin/competitors");
  if (!res.ok()) return [];
  const body = (await res.json()) as
    | Array<{ id: string; name: string }>
    | { content?: Array<{ id: string; name: string }>; items?: Array<{ id: string; name: string }> };
  if (Array.isArray(body)) return body;
  return body.content ?? body.items ?? [];
}

export async function deleteCompetitor(request: APIRequestContext, id: string) {
  await request.delete(`/api/admin/competitors/${encodeURIComponent(id)}`);
}

export async function wipeE2eCompetitors(request: APIRequestContext) {
  const competitors = await listCompetitors(request);
  let deleted = 0;
  for (const c of competitors) {
    if (c.name.startsWith("E2E경쟁사")) {
      const res = await request.delete(`/api/admin/competitors/${encodeURIComponent(c.id)}`);
      if (res.ok()) deleted += 1;
    }
  }
  return deleted;
}

export async function createUserRequest(
  request: APIRequestContext,
  data: {
    requestName: string;
    sourceName: string;
    sourceUrl: string;
    slackChannelId?: string;
    personaName?: string;
    personaPrompt?: string;
  },
) {
  const res = await request.post("/api/user/requests", {
    data: {
      slackChannelId: "C0123456789",
      personaName: "핵심 요약",
      personaPrompt: "핵심만 쉽고 짧게 정리해줘",
      ...data,
    },
  });
  return { ok: res.ok(), json: res.ok() ? ((await res.json()) as { id: string }) : null };
}
