package com.keke.giftplayer.gift.svga.utils;

import android.graphics.Bitmap;
import android.util.Log;

import com.keke.giftplayer.animation.core.AnimationLog;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by HHJ on 2023/3/7 16:22
 * Desc:
 */
public class BitmapCache {

    // Bitmap resource.
    public Bitmap bitmap;
    // Reference list.
    private final CopyOnWriteArraySet<String> references = new CopyOnWriteArraySet<>();

    public boolean isEmpty() {
        return references.isEmpty();
    }

    /**
     * Adds a reference.
     */
    public void addQuote(String relatedKey) {
        this.references.add(relatedKey);
    }

    /**
     * Adds a reference.
     */
    public void addQuote(String relatedKey, Bitmap bitmap) {
        this.bitmap = bitmap;
        this.references.add(relatedKey);
    }

    /**
     * Returns whether the reference exists.
     */
    public boolean isContainsQuote(String relatedKey) {
        return references.contains(relatedKey);
    }

    /**
     * Removes a reference.
     */
    public void removeQuote(String relatedKey) {
        references.remove(relatedKey);
        // Release bitmap resource when no references remain.
        if (references.isEmpty()) {
            if (AnimationLog.isEnabled()) {
                Log.d("SVGA-clear","release svga bitmap");
            }
            bitmap = null;
        }
    }
}
