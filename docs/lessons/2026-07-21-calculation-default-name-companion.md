---
issue: Sautner-Studio-LLC/krill-oss#210
pr: Sautner-Studio-LLC/krill-oss#212
date: 2026-07-21
module: krill-sdk
category: serialization
---

## What happened

Every brand-new `Calculation` node created from the app was labelled **`Companion`**
instead of `Calculation`. The label is `node.name()` → `meta.displayName()` → the meta's
`name` field, and `CalculationEngineNodeMetaData` declared its default as:

```kotlin
@Serializable
data class CalculationEngineNodeMetaData(
    ...
    val name: String = this::class.simpleName!!,   // intended: "CalculationEngineNodeMetaData"
    ...
)
```

The creation path is `KrillApp.meta()` in the `krill` repo, whose Calculation arm calls
`CalculationEngineNodeMetaData()` with no arguments — so `name` always took its default.

## Root cause

`this::class.simpleName` does **not** resolve to the data class inside a `@Serializable`
primary-constructor default value. Because the class is `@Serializable`, kotlinx.serialization
generates a `Companion` object (holding the serializer), and the compiler lowers the
default-value initializer into a synthetic context associated with that `Companion`. `this`
there is the `Companion` object, so `this::class.simpleName` evaluates to the literal string
`"Companion"`.

Nothing failed — `"Companion"` is a perfectly valid `String`, it serialized, persisted, and
rendered as a legitimate-looking (wrong) label. This was the *only* class in the SDK using
`this::class.simpleName` as a default value (`grep` confirms), so the blast radius was exactly
one node type.

The neighbouring bug fixed in #197 (`displayName()` made abstract, `override fun displayName() =
name` added to twelve metas — including this one) is what made the wrong `name` *visible*:
before that, `displayName()` returned `""` and the type string was shown instead, masking the
bad default. Fixing the display path uncovered that the stored value itself was wrong.

The pre-existing regression test never protected this:

```kotlin
assertEquals("Average", CalculationEngineNodeMetaData(name = "Average").displayName())
```

It passes an explicit `name`, so it exercises `displayName()` but never the *default* — the only
place the bug lived.

## Fix

- `krill-sdk/.../calculation/CalculationEngineNodeMetaData.kt` — default is now the literal
  `val name: String = "Calculation"`. Independent of the class name and of the serialization
  machinery.
- `krill-sdk/.../node/NodeMetaDataInterfaceTest.kt` — added a regression that asserts the
  **default**: `CalculationEngineNodeMetaData().displayName() == "Calculation"`.
- Bumped `krill-sdk` `0.0.59 → 0.0.60` so CI republishes to Maven Central; the app picks up the
  fix once `krill`'s `krill-sdk` pin is bumped to `0.0.60`.
- Deliberately did **not** rename the class to `CalculationMetaData` (an in-progress rename was
  reverted): it has no `@SerialName`, so its polymorphic discriminator is its fully-qualified
  name — renaming would break deserialization of already-persisted Calculation nodes and break
  the `krill` build, none of which is needed to fix the label.

## Prevention

- **`this::class` in a `@Serializable` constructor default resolves to `Companion`, not the
  data class.** Never derive a default field value from `this::class.simpleName` on a
  `@Serializable` type. Use a literal, or compute the name at the call site with an explicit
  class reference (`Foo::class.simpleName`, as `KrillAppMeta` already does for `CronTimer`/`Timer`).
- **Test the default, not just the explicit value.** A `displayName()` test that always passes an
  explicit `name` can never catch a wrong default. When a field has a meaningful default, assert
  the zero-arg construction path directly.
- **A wrong-but-plausible value hides longer than a crash.** `"Companion"` is a valid string that
  serialized and rendered fine; only a human reading the canvas caught it. Prefer defaults that
  are either obviously correct or loudly absent over ones that merely look like data.
- **A serial discriminator is a wire contract.** Renaming a `@Serializable` polymorphic subtype
  without a `@SerialName` pin silently changes its on-the-wire type tag and breaks every payload
  already persisted under the old name — keep renames and behaviour fixes separate.
