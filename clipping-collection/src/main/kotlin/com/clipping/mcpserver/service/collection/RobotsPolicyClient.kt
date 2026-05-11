package com.clipping.mcpserver.service.collection

import java.net.URI

interface RobotsPolicyClient {
    fun isAllowed(targetUri: URI): Boolean
}
