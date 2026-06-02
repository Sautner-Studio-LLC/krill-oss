# Dead agentic-LLM model classes removed from krill-sdk

**Issue:** [krill-oss#113](https://github.com/Sautner-Studio-LLC/krill-oss/issues/113)
**Root cause category:** Dead code accumulation — abandoned feature experiment
**Module:** `krill-sdk`

## What happened

The agentic-LLM experiment (LLM proposing nodes/actions/links and holding
conversations) was abandoned, but ~15 model classes (~550 lines) were left in
`krill-sdk/src/commonMain/kotlin/krill/zone/shared/krillapp/server/llm/`. They
were dead weight: no references outside the cluster in live code. The sweep
revealed that `Logprob` and `TopLogprob` were still referenced by the live
`Chat` type (used in `ServerLLMProcessor`), so they were retained. `ToolCall`,
`Function`, and `Arguments` were also still referenced from `Message.toolCalls`,
requiring a field removal in addition to the file deletions.

## Fix

- Deleted 13 files from `krill-sdk/.../llm/`:
  `LLMProposedAction`, `LLMProposedActionType`, `LLMProposedLink`,
  `LLMNewNodeProposal`, `LLMNodeReview`, `LLMResponse`, `LLMResponseStatus`,
  `ToolCall`, `Function`, `Arguments`, `LLMContextPayload`, `LLMFeatureSummary`,
  `LLMConnectionHintsSummary`.
- Removed the `toolCalls: List<ToolCall>` field from `Message.kt` (was the only
  live reference into the dead cluster).
- Retained `Logprob.kt` and `TopLogprob.kt` — both used by `Chat.kt`, which is
  actively used by the krill server's `ServerLLMProcessor`.
- Bumped `krill-sdk` patch version `0.0.39` → `0.0.40`.

## Prevention

- Before deleting a class cluster, always sweep both product repos for source
  references (`grep -rl … --include="*.kt" | grep -v /build/`). Build artifacts
  contain the names too — filter them out or you'll get false positives.
- Cross-check each class in the "delete" list individually: the issue author may
  not have traced every indirect reference (e.g. `Logprob` via `Chat.logprobs`).
- When a field in a **kept** class references a **deleted** type, the field must
  be removed in the same PR — don't leave a broken import.
