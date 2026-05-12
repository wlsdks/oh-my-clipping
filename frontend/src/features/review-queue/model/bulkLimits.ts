/**
 * 프론트에서 강제하는 일괄 처리 상한.
 * 백엔드는 1~100을 허용하지만(`BulkRequest.validate`), UX/안전 가드레일로 20건으로 제한한다.
 * - 20건 이상은 rubber-stamping 위험이 급격히 증가
 * - 이전 PR #185 크래시와 동일한 대량 액션 부작용 차단
 * - 샘플 확인 카드가 효과적으로 보조할 수 있는 상한
 */
export const MAX_BULK_SELECT = 20;
