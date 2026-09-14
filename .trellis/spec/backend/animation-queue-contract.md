# Playback Queue and Download Timeout Contract

## Scope

Applies to `animation/widget/QueuedAnimationPlayerView.kt`, the resource manager,
and `lib_download` initialization. Single-play views retain replacement semantics.

## Signatures

- `enqueue(AnimationRequest)` prepares one independent playback entry.
- `clearQueue()` clears waiting/prepared entries, preserving the active animation.
- `stop(clear)` stops the active animation and clears waiting entries.
- `release()` cancels owned work and releases protected files and callbacks.
- Download config exposes `connectTimeoutMillis`, `readTimeoutMillis`, and
  `downloadTimeoutMillis` with 20/30/180-second defaults.
- Queue config exposes nullable `messageTtlMillis`; null disables expiration.

## Contracts

Ready order determines playback order. Download priorities only order queued network
work. Cache hits and local resources must not wait behind another resource download.
Duplicate messages remain independent even when they share a download. Original
requests are preserved in external callbacks. Cache protection covers the interval
from resource readiness through playback cleanup. Internal scheduling must continue
when external callbacks are changed or throw.

Network total timeout starts at actual download activation, excludes queue delay,
and releases the occupied slot. Late callbacks must identify the original download
instance, not only the resource key. Shared cancellation only removes that caller.

## Validation and Errors

| Condition | Result |
| --- | --- |
| Non-positive timeout or configured TTL | Reject configuration |
| Network timeout | Failure callback and next network task scheduled |
| Waiting message TTL expires | Cancel that message, preserve other consumers |
| Download/decode/render error | Report failure and advance ready queue |
| Release followed by late completion | Ignore stale playback work |

## Cases

- Base: A downloads and then plays.
- Good: A downloads slowly; cached B plays first; A waits if B is still playing.
- Bad: enqueue delegates immediately to parent `play(Url)` and cancels A.

## Tests Required

Verify ready ordering, duplicate preservation, failure advancement, expiration,
pause/resume, clear/release reentrancy, cache protection balance, positive timeout
validation, and stale task callbacks after timeout/replacement.

## Wrong vs Correct

Wrong: interpret a 30-second read timeout as the total allowed file download time.
Correct: configure inactivity and total deadlines independently for slow networks.

Wrong: use a single caller callback as the queue's internal completion observer.
Correct: keep internal queue progression independent and forward external events.
