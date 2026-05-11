package com.ohmyclipping.error

/**
 * 필수 설정값(환경변수, 런타임 설정 등)이 누락된 경우 발생하는 예외.
 *
 * 서비스 초기화 또는 실행 시 필수 설정이 없을 때 사용한다.
 */
class ConfigurationMissingException(message: String) : RuntimeException(message)
