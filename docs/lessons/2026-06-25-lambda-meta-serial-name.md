# Lambda create_node failed: meta serial name LambdaSourceMetaData vs server's LambdaMetaData

**Issue:** [krill-oss#165](https://github.com/Sautner-Studio-LLC/krill-oss/issues/165)
**Root cause category:** SDK skew — class rename not propagated to MCP registry
**Module:** `krill-mcp`

## What happened

`create_node` for `KrillApp.Executor.Lambda` failed with HTTP 400 against any
krill server running krill-sdk ≥ 0.0.56. The MCP stamped the polymorphic
`meta.type` discriminator as `...LambdaSourceMetaData` — the old class name from
krill-sdk 0.0.48. The server registered the class as `LambdaMetaData` (renamed
in 0.0.56), so kotlinx.serialization couldn't deserialize the node body. The
error only surfaced at runtime because the MCP builds against krill-sdk 0.0.48
and the server uses a newer published artifact.

## Fix

- `KrillNodeTypes.kt`: change Lambda `metaFqn` and the first argument of its
  `sdkMeta(...)` call from
  `krill.zone.shared.krillapp.executor.lambda.LambdaSourceMetaData` to
  `krill.zone.shared.krillapp.executor.lambda.LambdaMetaData`.
- Remove the stale `tags` entry from `metaFieldHints` (the field was dropped in
  0.0.56; the server ignores unknown keys so it's harmless but misleading).
- Two regression tests in `CreateNodeToolTest` assert both `metaFqn` and
  `defaultMeta["type"]` equal the correct FQN.

## Prevention

- The `metaFqn` / `defaultMeta.type` strings are hand-written constants —
  if the SDK renames a MetaData class, every occurrence in `KrillNodeTypes.kt`
  must be updated manually. Add a test for each type that compares `metaFqn`
  against a known-good string; a rename in the SDK fails the build instead of
  silently shipping broken node creation.
- Before bumping the krill-sdk pin in krill-mcp, run `list_node_types` against a
  live server and spot-check the `metaFqn` values for newly renamed types.
- Blocked Lambda creation was caught by the kraken demo pipeline; the
  `running-python-lambdas` demo used a direct POST workaround pointing back to
  this issue. Once this PR lands, that workaround can be removed.
