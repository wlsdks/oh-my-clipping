package com.ohmyclipping.store

/**
 * 자동 리포트 설정 저장소.
 * key-value 기반으로 리포트 스케줄 및 포함 항목 설정을 관리한다.
 */
interface ReportSettingsStore {

    /** 전체 설정을 key-value 맵으로 조회한다. */
    fun findAll(): Map<String, String>

    /** 특정 설정 키의 값을 조회한다. 없으면 null. */
    fun findByKey(key: String): String?

    /** 설정 키-값을 저장하거나 갱신한다 (UPSERT). */
    fun upsert(key: String, value: String)
}
