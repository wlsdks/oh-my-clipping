package com.clipping.mcpserver.model

/**
 * 조직 origin 값 상수 모음.
 *
 * DB column `organizations.origin` 에 저장되는 문자열이며,
 * 프론트엔드 TypeScript `OrgOrigin` 타입과 1:1 매칭된다.
 *
 * 새 origin 값 추가 시:
 *  1. 이 object 에 const 추가
 *  2. 프론트엔드 `OrgOrigin` 타입 갱신
 *  3. DB CHECK 제약이 있으면 마이그레이션 추가
 */
object OrganizationOrigins {
    /** 사용자가 위자드를 통해 직접 생성한 조직. */
    const val USER_WIZARD = "user_wizard"

    /** 관리자가 어드민 UI 에서 직접 생성한 조직. */
    const val ADMIN_CREATED = "admin_created"

    /** 경쟁사 목록에서 자동으로 mirror 된 조직. */
    const val COMPETITOR_MIRROR = "competitor_mirror"

    /** RSS 소스 분석 결과를 기반으로 자동 backfill 된 조직. */
    const val BACKFILL = "backfill"

    /** V131 이전에 생성된 레거시 조직. origin 컬럼이 없던 시절의 데이터. */
    const val LEGACY = "legacy"
}
