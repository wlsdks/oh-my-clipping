package com.ohmyclipping.service.tx

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * TX 내/밖 공용 헬퍼. TX 활성 중이면 afterCommit 콜백으로 action 등록.
 * TX 밖이면 즉시 실행.
 *
 * Use: 트랜잭션 커밋 이후에만 외부 I/O(예: Slack API)를 트리거해야 할 때.
 */
fun publishAfterCommit(action: () -> Unit) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        // TX 활성 중 — 커밋 이후 실행 예약
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() { action() }
        })
    } else {
        // TX 없음 — 즉시 실행
        action()
    }
}
