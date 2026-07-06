## Always push finished work

When a unit of work is complete and verified (build + tests green), push it to the
branch where my working files live — normally `main`, or whichever branch is currently
checked out and holds the real source. Concretely: commit the change, then
`git push origin <current-branch>`. Don't leave finished, verified commits sitting
only on my local machine, and don't wait to be told to push each time — this is
standing authorization to push completed work. (Still branch first if I explicitly
ask for a PR, and never force-push or push half-done/failing work.)

## Offloading large, low-value inputs

Before reading a large stack trace, source file, or Java package just to
summarize/explain it, prefer the `offload` MCP tools
(`explain_stack_trace`, `summarize_file`, `summarize_package`,
`explain_compiler_error`, `draft_javadoc`). They run a local model to keep
raw dumps out of context and off the usage cap. If a result comes back with
`fallback: true` or low confidence, do the task yourself.
If the offload tools are deferred, load them before deciding you can't use them.