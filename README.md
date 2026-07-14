# GiftPlayer

Android animation player library for SVGA, PAG, and VAP resources.

## Install

Add JitPack to the consuming project:

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

Add the library dependency:

```kotlin
implementation("com.github.<github-user>.GiftPlayer:giftplayer:1.0.0")
```

Replace `<github-user>` and `1.0.0` with your GitHub username and release tag.

## Initialize

```kotlin
AnimationInitializer.init(applicationContext)
```

Custom download config:

```kotlin
AnimationInitializer.init(
    context = applicationContext,
    downloadConfig = AnimationDownloadConfig(
        maxConcurrentDownloads = 2,
        isLogEnabled = true,
    ),
)
```

## Play URL

```kotlin
playerView.playGift(
    animationUrl = animationUrl,
    priority = AnimationDownloadPriority.Highest,
)
```

## Play Asset

Put the animation file in `src/main/assets`, then play it:

```kotlin
playerView.playGift(
    source = AnimationSource.Asset("xxx.pag"),
    format = AnimationFormat.Pag,
)
```

## Modules

- `giftplayer`: Android library published for external usage.
- `app`: demo app showing URL and local assets playback.
