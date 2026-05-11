rootProject.name = "oh-my-clipping"

include(
    ":core:domain",
    ":core:error-types",
    ":core:api-models",
    ":core:application-models",
    ":ports:workflow",
    ":ports:persistence",
    ":adapters:persistence",
    ":adapters:notification",
    ":modules:digest-policy",
    ":modules:collection",
    ":modules:source",
    ":modules:digest",
    ":modules:user",
    ":modules:analytics",
)
