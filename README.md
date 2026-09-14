
# GiftPlayer

An Android animation and room effects library for gift animations, interactive room widgets, and SVGA/PAG/VAP playback.

GiftPlayer provides a unified player view for multiple animation formats, with remote URL downloading, batch preloading, local assets playback, cache management, download priority scheduling, and simple APIs for gift animation scenarios.

## Features

- Supports SVGA, PAG, and VAP animation playback
- Unified `GiftAnimationPlayerView` API
- Remote URL playback with download and cache support
- Local assets playback
- Batch download and preload support
- Download priority scheduling
- Same-resource download reuse
- Multiple callback merging for the same downloading resource
- Configurable cache directory, cache size, cache age, and concurrent downloads
- Resume download support
- Optional audio playback
- Demo app included

## Install

Add JitPack to your project:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the dependency:

```kotlin
implementation("com.github.KeShiJie.GiftPlayer:giftplayer:1.0.0")
```

Replace `1.0.0` with the GitHub release tag you want to use.

## Initialize

Initialize the library before playing remote animations:

```kotlin
AnimationInitializer.init(applicationContext)
```

Custom download configuration:

```kotlin
AnimationInitializer.init(
    context = applicationContext,
    downloadConfig = AnimationDownloadConfig(
        maxConcurrentDownloads = 2,
        connectTimeoutMillis = 20_000,
        readTimeoutMillis = 30_000,
        downloadTimeoutMillis = 180_000L,
        isLogEnabled = true,
    ),
)
```

## Play Remote URL

```kotlin
playerView.playGift(
    animationUrl = "https://s1.videocc.net/default-img/donate-svga/diamond.svga",
    priority = AnimationDownloadPriority.Highest,
)
```

With callback:

```kotlin
playerView.play(
    AnimationRequest(
        source = AnimationSource.Url(animationUrl, AnimationDownloadPriority.Highest),
    ),
)
```

## Play Local Asset

Put the animation file in your app's `src/main/assets` directory.

```kotlin
playerView.playGift(
    source = AnimationSource.Asset("say_hi.pag"),
    format = AnimationFormat.Pag,
)
```

## Advanced Playback

```kotlin
playerView.play(
    AnimationRequest(
        source = AnimationSource.Asset("xxx.pag"),
        format = AnimationFormat.Pag,
    ),
)
```

## Ready-First Playback Queue

Use `BaseAnimationPlayerView` for single playback that replaces the previous request.
Use `QueuedAnimationPlayerView` to prepare multiple resources concurrently and play
them one at a time in the order they become ready:

```kotlin
queuedPlayerView.enqueue(
    AnimationRequest(
        source = AnimationSource.Url(animationUrl, AnimationDownloadPriority.Highest),
        loopCount = 1,
    ),
)
queuedPlayerView.enqueue(
    AnimationRequest(source = AnimationSource.Asset("say_hi.pag")),
)
```

If A is downloading while B is cached, B can play immediately when the player is
idle. A joins the ready queue after its download completes. A newly ready animation
never interrupts the current animation. Repeated messages remain separate playback
entries even when they share one download.

`clearQueue()` cancels waiting entries without stopping the current animation.
`stop()` stops playback and clears waiting entries. `release()` clears downloads,
timers, callbacks, and cache protection owned by this view. Use finite loop counts
when subsequent animations should play automatically.

## Weak-Network Timeouts

Connection timeout defaults to 20 seconds. Read timeout defaults to 30 seconds of
waiting for network data, not 30 seconds for the whole file. Total download timeout
defaults to 180 seconds and starts when the download starts, excluding scheduler
queue time. All three values must be positive and are configurable through
`AnimationDownloadConfig`.

Playback messages have no expiration by default. Message expiration is separate
from network timeouts: it measures time from enqueue until playback starts and
never expires an animation that is already playing.

## Modules

- `giftplayer`: Android library published for external usage.
- `app`: demo app showing URL and local assets playback.
- `lib_download`: local FileDownloader AAR and initialization.
