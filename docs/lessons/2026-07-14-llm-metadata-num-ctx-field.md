---
issue: Sautner-Studio-LLC/krill-oss#205
pr: Sautner-Studio-LLC/krill-oss#206
date: 2026-07-14
module: krill-sdk
category: api-design
---

## What happened

Sautner-Studio-LLC/krill#883 reported that an LLM node pointed at `qwen2.5vl:32b` on a
2×RTX 5090 box OOMed the GPU and thrashed in an unbounded degrade-and-retry loop for
minutes, with no error surfaced on the node. Root cause: `ServerLLMProcessor` sent Ollama
no `options.num_ctx`, so Ollama fell back to the model's Modelfile default (128K for the
Qwen-VL family), whose KV cache alone needs ~50 GB.

`krill#883` fixed the immediate OOM by hardcoding `options.num_ctx: 8192` on every Ollama
request. That stops the crash unconditionally but removes any way to tune the context
window per node — a node genuinely running a small model on modest hardware, or one that
needs more headroom than 8192 tokens, has no way to say so, because `LLMMetaData` (the SDK
model this repo owns) carried no field for it.

## Fix

- Added three fields to `LLMMetaData`
  (`krill-sdk/src/commonMain/kotlin/krill/zone/shared/krillapp/server/llm/LLMMetaData.kt`):
  `numCtx: Int = 8192` (matches `krill#883`'s hardcoded safe default), `temperature: Double? = null`,
  and `keepAlive: String? = null` — all Ollama `options.*` values a consumer needs and had no
  way to read.
  - All three default such that existing serialized `LLMMetaData` payloads round-trip
    unchanged, per the class's file-level convention.
- Bumped `krill-sdk/build.gradle.kts` patch version (`0.0.59` → `0.0.60`) per the SDK
  versioning rule.
- Added regression tests to `LLMMetaDataTest` covering the new defaults, back-compat
  deserialization of a payload missing the new fields, and a full round-trip with them set.
- Did **not** touch `ServerLLMProcessor` — that's `krill`'s file. A follow-up `krill` issue
  wires `buildRequestBody`'s `options` block to read these fields in place of the current
  hardcoded constant.

## Prevention

- **A hardcoded safe default that fixes an incident is not the same as a tunable setting.**
  `krill#883`'s fix was correct triage — stop the OOM now — but it silently removed operator
  control that never technically existed either (the field wasn't there before). When a
  hardcoded constant papers over a missing model field, file the follow-up immediately
  rather than letting "safe for everyone" quietly become "no better than a fixed default was
  before."
- **SDK model fields are the seam between repos.** `krill-oss` owns `LLMMetaData`; `krill`
  owns the processor that reads it. A field consumers need but can't reach isn't a `krill`
  bug — it's an SDK gap, and the fix belongs here even though the pain was felt downstream.
