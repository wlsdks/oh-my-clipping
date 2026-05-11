package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.model.Organization

fun Organization.toDigestOrganization(): DigestOrganization =
    DigestOrganization(name = name, aliases = aliases)

fun List<Organization>.toDigestOrganizations(): List<DigestOrganization> =
    map { it.toDigestOrganization() }
