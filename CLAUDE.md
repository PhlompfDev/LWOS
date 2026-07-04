## Offloading large, low-value inputs

Before reading a large stack trace, source file, or Java package just to
summarize/explain it, prefer the `offload` MCP tools
(`explain_stack_trace`, `summarize_file`, `summarize_package`,
`explain_compiler_error`, `draft_javadoc`). They run a local model to keep
raw dumps out of context and off the usage cap. If a result comes back with
`fallback: true` or low confidence, do the task yourself.
If the offload tools are deferred, load them before deciding you can't use them.