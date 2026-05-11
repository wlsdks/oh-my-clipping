package com.clipping.mcpserver.service.dto

/**
 * 하위 호환을 위한 type alias.
 * 실제 정의는 model 패키지로 이동했다. store 계층에서 service.dto를 참조하던
 * 아키텍처 위반을 해소하기 위함이다.
 */
typealias PersonaVersionSummary = com.clipping.mcpserver.model.PersonaVersionSummary
typealias PersonaVersionDetail = com.clipping.mcpserver.model.PersonaVersionDetail
