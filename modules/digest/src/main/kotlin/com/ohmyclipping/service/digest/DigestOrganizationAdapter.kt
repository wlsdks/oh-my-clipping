package com.ohmyclipping.service.digest

import com.ohmyclipping.model.Organization

fun Organization.toDigestOrganization(): DigestOrganization =
    DigestOrganization(name = name, aliases = aliases)

fun List<Organization>.toDigestOrganizations(): List<DigestOrganization> =
    map { it.toDigestOrganization() }
