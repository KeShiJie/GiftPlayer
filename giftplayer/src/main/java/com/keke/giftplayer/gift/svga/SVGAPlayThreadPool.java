package com.keke.giftplayer.gift.svga;

import java.util.concurrent.*;

/**
 * Created by HHY on 2022/03/31 14:40
 * SVGA playback thread pool.
 * Maximum playback count is 15.
 * Idle timeout is 20 seconds.
 */
public class SVGAPlayThreadPool extends ThreadPoolExecutor {

    private LinkedBlockingQueue<Runnable> waitQueue = null;

    public SVGAPlayThreadPool(ThreadFactory threadFactory,LinkedBlockingQueue waitQueue) {

        super(0, 15, 20, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory, (r, executor) -> {
            if (waitQueue != null) {
                waitQueue.add(r);
            }
        });
        this.waitQueue = waitQueue;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (waitQueue == null || waitQueue.isEmpty()) {
            return;
        }
        Runnable runnable = waitQueue.poll();
        if (runnable != null) {
            // Execute the next task from the waiting queue.
            execute(runnable);
        }
    }
}
