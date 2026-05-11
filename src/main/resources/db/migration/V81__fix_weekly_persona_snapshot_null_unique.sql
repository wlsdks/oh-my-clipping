-- Fix NULL-safe unique constraint on weekly_persona_snapshot.
-- PostgreSQL treats NULLs as distinct in UNIQUE, so deleted personas
-- (persona_id = NULL) can produce duplicate rows per week_start.
-- Solution: change FK from ON DELETE SET NULL to ON DELETE CASCADE.
-- This way deleted personas simply remove their snapshot rows,
-- avoiding the NULL-duplicate problem entirely.

-- Drop the existing FK constraint and recreate with CASCADE.
ALTER TABLE weekly_persona_snapshot DROP CONSTRAINT IF EXISTS fk_wps_persona;

ALTER TABLE weekly_persona_snapshot
    ADD CONSTRAINT fk_wps_persona
    FOREIGN KEY (persona_id) REFERENCES clipping_personas(id)
    ON DELETE CASCADE;
