-- V110: clipping_personas.addons 유령 컬럼 제거
-- V54에서 추가됐으나 서비스 로직/AI 프롬프트/렌더링 어디서도 사용되지 않는 dead column.
-- 관리자/사용자 페르소나 DTO에만 read/write 경로가 있었고 실 데이터 활용 없음.
-- seed 데이터에도 addons 컬럼을 채우는 구문이 없어 운영 데이터는 전부 NULL이다.
-- PostgreSQL/H2 모두 `DROP COLUMN IF EXISTS` 구문을 지원한다.
ALTER TABLE clipping_personas DROP COLUMN IF EXISTS addons;
