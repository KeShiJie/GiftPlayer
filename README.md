````md
# GiftPlayer

An Android animation and room effects library for gift animations, interactive room widgets, and SVGA/PAG/VAP playback.

GiftPlayer provides a unified player view for multiple animation formats, with remote URL downloading, local assets playback, cache management, download priority scheduling, and simple APIs for gift animation scenarios.

## Features

- Supports SVGA, PAG, and VAP animation playback
- Unified `GiftAnimationPlayerView` API
- Remote URL playback with download and cache support
- Local assets playback
- Download priority scheduling
- Configurable cache directory, cache size, cache age, and concurrent downloads
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
        maxCacheSizeBytes = 300L * 1024L * 1024L,
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
playerView.playGift(
    animationUrl = animationUrl,
    priority = AnimationDownloadPriority.Highest,
    callback = object : AnimationCallback {
        override fun onComplete(request: AnimationRequest) {
            // Animation completed.
        }

        override fun onError(request: AnimationRequest, error: AnimationError) {
            // Handle playback or download error.
        }
    },
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
playerView.playGift(
    source = AnimationSource.Url(
        url = animationUrl,
        priority = AnimationDownloadPriority.High,
    ),
    format = AnimationFormat.Auto,
    loopCount = 1,
    isAudioEnabled = true,
)
```

## Download Priority

GiftPlayer supports download priority scheduling for URL resources:

```kotlin
enum class AnimationDownloadPriority {
    Highest,
    High,
    Medium,
    Low
}
```

Priority only affects queued download tasks. It does not interrupt downloads that are already running.

If the same URL resource is requested multiple times while downloading, GiftPlayer reuses the existing download task and merges callbacks.

## Supported Sources

```kotlin
AnimationSource.Url(url, priority)
AnimationSource.Asset(name)
AnimationSource.FilePath(path)
```

Only `AnimationSource.Url` uses download priority.

## Supported Formats

```kotlin
AnimationFormat.Auto
AnimationFormat.Svga
AnimationFormat.Pag
AnimationFormat.Vap
```

Use `AnimationFormat.Auto` when the format can be detected automatically from the file.

## Modules

- `giftplayer`: Android library module for external usage
- `app`: Demo app showing URL playback and assets playback

## License

This project is licensed under the MIT License.
````
