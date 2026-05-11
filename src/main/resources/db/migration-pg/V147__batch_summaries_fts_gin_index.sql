-- PostgreSQL-only FTS index for BatchSummaryStore search paths.
--
-- H2 tests load only db/migration, so this file is skipped there. The expression
-- must stay aligned with JpaBatchSummaryStore/JdbcBatchSummaryStore FTS queries.

CREATE INDEX IF NOT EXISTS idx_batch_summaries_fts
  ON batch_summaries
  USING GIN (
    to_tsvector(
      'simple',
      coalesce(original_title, '') || ' ' ||
      coalesce(translated_title, '') || ' ' ||
      coalesce(summary, '') || ' ' ||
      coalesce(keywords, '')
    )
  );
