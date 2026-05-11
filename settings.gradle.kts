rootProject.name = "oh-my-clipping"

include(
    ":core:domain",
    ":core:error-types",
    ":core:api-models",
    ":ports:workflow",
    ":ports:persistence",
    ":adapters:persistence",
    ":adapters:notification",
    ":modules:admin",
    ":modules:digest-policy",
    ":modules:collection",
    ":modules:source",
    ":modules:digest",
    ":modules:user",
    ":modules:analytics",
)
