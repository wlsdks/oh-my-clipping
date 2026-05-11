package com.ohmyclipping.repository

import com.ohmyclipping.entity.UserClippingRequestEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * 사용자 클리핑 구독 요청 JPA 리포지토리.
 * clipping_user_requests 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface UserClippingRequestRepository : JpaRepository<UserClippingRequestEntity, String> {

    /** 요청자 사용자 ID로 요청 목록을 조회한다. */
    fun findByRequesterUserId(requesterUserId: String): List<UserClippingRequestEntity>

    /** 요청자와 상태 목록으로 요청 수를 계산한다. */
    fun countByRequesterUserIdAndStatusIn(requesterUserId: String, status: List<String>): Int

    /** 요청자, 생성 시각, 상태 목록으로 요청 수를 계산한다. */
    fun countByRequesterUserIdAndCreatedAtAfterAndStatusIn(
        requesterUserId: String,
        createdAt: Instant,
        status: List<String>
    ): Int

    /** 요청자, 승인 카테고리, 상태 조합 존재 여부를 확인한다. */
    fun existsByRequesterUserIdAndApprovedCategoryIdAndStatus(
        requesterUserId: String,
        approvedCategoryId: String,
        status: String
    ): Boolean

    /** 상태별 요청 목록을 조회한다. */
    fun findByStatus(status: String): List<UserClippingRequestEntity>

    /** 상태별 요청 목록을 최신 생성순으로 제한 조회한다. */
    fun findByStatusOrderByCreatedAtDesc(
        status: String,
        pageable: Pageable,
    ): List<UserClippingRequestEntity>

    /** 전체 요청 목록을 최신 생성순으로 제한 조회한다. */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<UserClippingRequestEntity>

    /**
     * 지정 상태이면서 createdAt 이 cutoff 이전인 요청 목록을 조회한다.
     * SLA 에스컬레이션 스케줄러에서 `status='PENDING'` 조합으로 사용한다.
     */
    fun findByStatusAndCreatedAtBefore(status: String, cutoff: Instant): List<UserClippingRequestEntity>

    /** APPROVED 상태의 구독자 수를 카테고리별로 그룹 카운트한다. */
    @Query(
        """SELECT r.approvedCategoryId, COUNT(r) FROM UserClippingRequestEntity r
           WHERE r.status = 'APPROVED' AND r.approvedCategoryId IS NOT NULL
           GROUP BY r.approvedCategoryId"""
    )
    fun countApprovedGroupByCategoryId(): List<Array<Any>>

    /** APPROVED 상태의 구독 수를 요청자(userId)별로 그룹 카운트한다. */
    @Query(
        """SELECT r.requesterUserId, COUNT(r) FROM UserClippingRequestEntity r
           WHERE r.status = 'APPROVED'
           GROUP BY r.requesterUserId"""
    )
    fun countApprovedGroupByRequester(): List<Array<Any>>

    /** 지정 카테고리 중 APPROVED 구독을 가진 요청자 ID를 중복 없이 조회한다. */
    @Query(
        """SELECT DISTINCT r.requesterUserId FROM UserClippingRequestEntity r
           WHERE r.status = 'APPROVED' AND r.approvedCategoryId IN :categoryIds"""
    )
    fun findApprovedRequesterIdsByCategoryIds(categoryIds: Set<String>): List<String>

    /** 지정 사용자들이 APPROVED 상태로 구독 중인 카테고리 ID를 중복 없이 조회한다. */
    @Query(
        """SELECT DISTINCT r.approvedCategoryId FROM UserClippingRequestEntity r
           WHERE r.status = 'APPROVED'
             AND r.requesterUserId IN :requesterUserIds
             AND r.approvedCategoryId IS NOT NULL"""
    )
    fun findApprovedCategoryIdsByRequesterIds(requesterUserIds: Set<String>): List<String>

    /** 지정 카테고리의 APPROVED 구독 요청을 최신 생성순으로 조회한다. */
    fun findByStatusAndApprovedCategoryIdOrderByCreatedAtDesc(
        status: String,
        approvedCategoryId: String
    ): List<UserClippingRequestEntity>

    /** 지정 채널 ID 에 해당 상태 중 하나인 요청이 존재하면 true를 반환한다. */
    fun existsBySlackChannelIdAndStatusIn(slackChannelId: String, status: List<String>): Boolean

    /** 여러 구독 요청의 상태를 일괄 변경한다. */
    @Modifying
    @Query(
        """UPDATE UserClippingRequestEntity r
           SET r.status = :status, r.reviewNote = :reviewNote,
               r.reviewedByUserId = :reviewedByUserId, r.reviewedAt = :now, r.updatedAt = :now
           WHERE r.id IN :ids"""
    )
    fun updateStatusBulk(
        ids: List<String>,
        status: String,
        reviewNote: String?,
        reviewedByUserId: String?,
        now: Instant
    ): Int
}
