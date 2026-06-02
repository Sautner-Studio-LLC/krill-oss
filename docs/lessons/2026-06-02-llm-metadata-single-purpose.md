# LLMMetaData aligned to single-purpose, source-invoked model

**Issue:** [krill-oss#112](https://github.com/Sautner-Studio-LLC/krill-oss/issues/112)
**Root cause category:** API design — multi-purpose payload mixing chat-session state with transform-node config
**Module:** `krill-sdk`

## What happened

`LLMMetaData` carried a `chat: List<Message>` field intended to persist
conversation history on the node. This conflated the interactive-chat model
with the single-purpose transform model Krill is moving to: a `Server.LLM`
node is a stateless, source-invoked pipeline step, not a chat session. The
field also had no backend selector or structured output contract, so
`ServerLLMProcessor` and external integrators had no standard way to declare
format expectations or route to OpenAI-compatible endpoints.

## Fix

- Removed `chat: List<Message>` from `LLMMetaData`. Old payloads carrying the
  field still deserialize correctly because the project-wide JSON config uses
  `ignoreUnknownKeys = true`.
- Added `backend: LlmBackend = OLLAMA` (`krill/zone/shared/krillapp/server/llm/LlmBackend.kt`).
- Added `systemPrompt: String = ""` — blank signals the server to apply its
  default persona.
- Added `responseFormat: ResponseFormat = NATURAL_LANGUAGE`
  (`krill/zone/shared/krillapp/server/llm/ResponseFormat.kt`).
- Added `responseInstructions: String = LLMResult.JSON_SCHEMA` — default
  schema string for structured output.
- Added `LLMResult` data class
  (`krill/zone/shared/krillapp/server/llm/LLMResult.kt`) with `summary`,
  `value`, `label`, `confidence`, `detail` fields and a `JSON_SCHEMA`
  companion constant so `ServerLLMProcessor` and observer nodes share one
  definition.
- Bumped `krill-sdk` to `0.0.40`; CI publishes to Maven Central automatically.

## Prevention

- Separate transport types (chat wire protocol) from config types (node
  metadata). `Message` stays in the SDK for the request wire; it must not
  double as a persistence format on a transform node.
- Every new output-contract type should ship a companion schema constant so
  the server and consumers can reference it without redeclaring it.
- When removing a field from a `@Serializable` class, verify `ignoreUnknownKeys`
  is in effect and add a round-trip test with the old payload shape before
  merging.
