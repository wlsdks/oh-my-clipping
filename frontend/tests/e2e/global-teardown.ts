import { request as playwrightRequest } from "@playwright/test";
import { loginAsAdmin, wipeE2eArtifacts } from "./helpers/api";

/**
 * Playwright global teardown — runs once after the entire test run finishes.
 *
 * Sweeps any category/persona whose name starts with an E2E test prefix.
 * Individual specs already clean up their own created rows, but suite
 * failures or early aborts can leave residue; this is the safety net so
 * the local/CI database doesn't accumulate hundreds of "e2e-…" rows.
 *
 * Points at whatever baseURL the test run used; never run against prod.
 */
export default async function globalTeardown() {
  const baseURL = process.env.E2E_BASE_URL || "http://127.0.0.1:8086";
  const context = await playwrightRequest.newContext({ baseURL });
  try {
    await loginAsAdmin(context);
    const { sourcesDeleted, categoriesDeleted, personasDeleted } = await wipeE2eArtifacts(context);
    if (sourcesDeleted > 0 || categoriesDeleted > 0 || personasDeleted > 0) {
      console.log(
        `[globalTeardown] wiped ${sourcesDeleted} E2E sources, ${categoriesDeleted} categories, ${personasDeleted} personas`,
      );
    }
  } catch (e) {
    // Teardown is best-effort; don't fail the run because cleanup had a hiccup.
    console.warn("[globalTeardown] skipped due to error:", e);
  } finally {
    await context.dispose();
  }
}
