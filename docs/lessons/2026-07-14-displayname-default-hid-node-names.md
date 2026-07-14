---
issue: Sautner-Studio-LLC/krill-oss#195
<<<<<<< HEAD
pr: Sautner-Studio-LLC/krill-oss#196
=======
pr: Sautner-Studio-LLC/krill-oss#197
>>>>>>> refs/remotes/origin/agents
date: 2026-07-14
module: krill-sdk
category: api-design
---

## What happened

Sautner-Studio-LLC/krill#847 reported that the swarm canvas labels nodes with their *type*
instead of their *name*: a HighThreshold named `Wake` renders as `HighThreshold`, and two
different LLM nodes both render as `LLM`. It was visible throughout the "Local LLM Nodes"
demo video — the two starring nodes were indistinguishable.

It was filed against `krill` as a canvas bug. The canvas was innocent. `NodeItem.kt` just
calls `node.name()`, which is SDK code:

```kotlin
fun Node.name(): String = this.meta.displayName().ifEmpty { this.type.toString() }
```

## Root cause

`NodeMetaData.displayName()` shipped with a **default implementation returning `""`**, and a
KDoc contract saying "concrete types that carry a human-readable name field override this."

Twelve concrete metas carried a `name` field and never wrote the override:
`FilterMetaData`, `ExecutorMetaData`, `CalculationEngineNodeMetaData`, `WebHookOutMetaData`,
`SerialDeviceTargetMetaData`, `SpacerMetaData`, `TriggerMetaData`, `ButtonMetaData`,
`ColorTriggerMetaData`, `CronMetaData`, `TimerMetaData`, `IncomingWebHookMetaData`.

Every one of them inherited `""`, so `name()` fell through to the type string. `TriggerMetaData.name`
even *defaults to `"Trigger"`* — the value was being stored and simply never read. Nothing
failed; the label was just quietly wrong, on every canvas, for as long as those types existed.

Two further metas had no `name` field at all — `LLMMetaData` and `LambdaMetaData` — so those
nodes could not be named even in principle. `LambdaMetaData` papered over it by displaying
`filename.removeSuffix(".py")`, which is why the demo's gate Lambda read `coop_gate`.

The sharpest detail: an existing test **asserted the bug was correct**.

```kotlin
@Test
fun `displayName returns empty for types with no dedicated name field such as LLMMetaData`() {
    assertEquals("", LLMMetaData().displayName())
}
```

That test passes before and after the fix (an unnamed LLM node still displays nothing). It was
written to describe the interface's default, and in doing so it froze "the LLM node has no name"
into the spec, where it read as intent rather than omission.

## Fix

- `NodeMetaData.displayName()` is now **abstract**, exactly like `withError()` on the same
  interface — which was made abstract for precisely this reason, and whose KDoc says so:
  *"Making it abstract ensures a compile error rather than a silent no-op when a new subtype is
  introduced."* The compiler then enumerated all sixteen missing implementations for us.
- Added `override fun displayName() = name` to the twelve metas that already carried a name.
- Added `val name: String = ""` to `LLMMetaData` and `LambdaMetaData`. Lambda falls back to the
  script filename when unnamed (`name.ifEmpty { filename.removeSuffix(".py") }`), so every
  Lambda serialized before this field keeps its current label.
- `MqttMetaData`, `SMTPMetaData`, `ComputeMetaData` genuinely have no human-readable name and
  now return `""` **explicitly**.
- Rewrote the test that had codified the gap; added regressions for each newly-surfaced type.

## Prevention

- **A defaulted interface method is a silent-failure seam.** `withError()` and `displayName()`
  sat side by side on the same interface with the same "every subtype must handle this" contract;
  the abstract one was honoured by all 30 subtypes and the defaulted one was skipped by 16. The
  default didn't make the API convenient, it made the omission invisible. When "every subtype
  must supply this" is the actual contract, encode it in the type system — a KDoc sentence saying
  "concrete types override this" is documentation, not enforcement.
- **A default that is a *plausible* value is worse than one that crashes.** `""` for a display
  name looks like data, flows through `ifEmpty { type.toString() }`, and renders as a
  legitimate-looking label. Nothing anywhere had to fail for the product to be wrong.
- **Watch for tests that pin down an omission as a spec.** The `LLMMetaData` test still passes
  post-fix; it never protected anything, it just made the gap look deliberate. When writing a
  test that asserts "X is empty / absent / unsupported", ask whether that's a *decision* or
  merely *the current state* — and if it's the latter, don't write it.
- **Route by where the broken code is, not where the symptom appeared.** This landed as a
  `composeApp` canvas bug in the wrong repo. The canvas was three call-frames downstream of the
  defect.
