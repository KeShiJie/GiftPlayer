# Animation Player Plugin Contract

## Scope

Applies to the `giftplayer`, `giftplayer-pag`, and `giftplayer-vap` modules and to
all future animation-engine integrations.

## Module Boundaries

- `giftplayer` owns the public playback API, downloads, cache, queue, plugin SPI,
  and the customized built-in SVGA implementation.
- `giftplayer-pag` contains only the libpag adapter and plugin factory.
- `giftplayer-vap` contains only the VAP adapter and plugin factory.
- Optional engine modules use `compileOnly` for their third-party engine. The
  consuming application selects and depends on the engine version explicitly.
- Core source and publication metadata must not reference libpag or VAP classes.

## Registration

- Applications register optional plugins once through `GiftPlayer.initialize`.
- `AnimationFormat.Svga` is always registered by core and cannot be replaced.
- `AnimationFormat.Auto` cannot be registered.
- Duplicate plugins for one concrete format are rejected during initialization.
- A concrete format without a registered plugin reports
  `AnimationError.PlayerPluginMissing`; it must not fall through to reflection or
  fail with a class-loading exception.

## Publication Checks

- Integration POMs include the core `giftplayer` artifact.
- Integration POMs do not include their third-party engine dependencies.
- The core POM and AAR contain no PAG or VAP engine dependency or class reference.
- Every published Android library includes its matching sources JAR.

## Tests Required

Verify built-in SVGA registration, optional plugin lookup, duplicate rejection,
`Auto` rejection, and registry reset between tests. Build the demo with both
optional integrations and inspect generated POMs before release.
