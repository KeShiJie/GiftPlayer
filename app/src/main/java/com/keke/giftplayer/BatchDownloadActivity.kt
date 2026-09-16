package com.keke.giftplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keke.giftplayer.animation.download.AnimationDownloadCallback
import com.keke.giftplayer.animation.download.AnimationDownloadPriority
import com.keke.giftplayer.animation.download.AnimationDownloadTask
import com.keke.giftplayer.animation.download.AnimationResource
import java.io.File

/**
 * Created by keke on 2026/09/15.
 * Desc: 批量预下载示例，展示逐项字节进度、缓存命中与取消。
 */
@SuppressLint("SetTextI18n")
class BatchDownloadActivity : AppCompatActivity() {
    private lateinit var urlsInput: EditText
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var clearCacheButton: Button
    private lateinit var summary: TextView
    private lateinit var batchProgress: ProgressBar
    private lateinit var rowsContainer: LinearLayout
    private val rows = linkedMapOf<String, DownloadRow>()
    private var tasks = emptyList<AnimationDownloadTask>()
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "批量下载示例"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        urlsInput = EditText(this).apply {
            hint = "资源 URL（每行一条）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setText(
                "https://s1.videocc.net/default-img/donate-svga/diamond.svga\n" +
                    "https://res.joyachat.com/gift/prod/1784259395913-hpzdoneiths.mp4\n" +
                    "https://res.joyachat.com/gift/prod/1784259951686-avjmk717xx.mp4\n" +
                    "https://res.joyachat.com/gift/prod/1784258494360-t2xmmswicy.mp4"
            )
        }
        content.addView(urlsInput)
        startButton = Button(this).apply {
            text = "开始批量下载"
            setOnClickListener { startDownloads() }
        }
        content.addView(startButton)
        cancelButton = Button(this).apply {
            text = "取消下载"
            isEnabled = false
            setOnClickListener { cancelDownloads() }
        }
        content.addView(cancelButton)
        clearCacheButton = Button(this).apply {
            text = "清除缓存"
            setOnClickListener { clearCache() }
        }
        content.addView(clearCacheButton)
        summary = TextView(this).apply { text = "尚未开始" }
        content.addView(summary)
        batchProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        content.addView(batchProgress)
        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rowsContainer)
    }

    private fun startDownloads() {
        val urls = urlsInput.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()
        if (urls.isEmpty()) {
            urlsInput.error = "请输入至少一个 URL"
            return
        }
        val currentGeneration = ++generation
        tasks.forEach { it.cancel() }
        tasks = emptyList()
        rows.clear()
        rowsContainer.removeAllViews()
        val resources = urls.map { AnimationResource(url = it, priority = AnimationDownloadPriority.Low) }
        resources.forEachIndexed { index, resource ->
            val label = TextView(this).apply {
                text = "${index + 1}. ${resource.url}"
                setPadding(0, 24, 0, 8)
            }
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            val status = TextView(this).apply { text = "等待下载" }
            rowsContainer.addView(label)
            rowsContainer.addView(progress)
            rowsContainer.addView(status)
            rows[resource.url] = DownloadRow(progress, status, GiftPlayer.isCached(resource))
        }
        startButton.isEnabled = false
        clearCacheButton.isEnabled = false
        urlsInput.isEnabled = false
        cancelButton.isEnabled = true
        updateSummary()
        tasks = GiftPlayer.preload(resources, object : AnimationDownloadCallback {
            override fun onProgress(resource: AnimationResource, downloadedBytes: Long, totalBytes: Long) {
                if (generation != currentGeneration) return
                val row = rows[resource.url] ?: return
                if (row.finished) return
                row.progress.isIndeterminate = totalBytes <= 0
                val downloaded = Formatter.formatShortFileSize(this@BatchDownloadActivity, downloadedBytes)
                if (totalBytes > 0) {
                    val percent = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                    row.progress.progress = percent
                    row.status.text = "$percent% · $downloaded / ${Formatter.formatShortFileSize(this@BatchDownloadActivity, totalBytes)}"
                } else {
                    row.status.text = "已下载 $downloaded · 总大小未知"
                }
            }

            override fun onSuccess(resource: AnimationResource, file: File) {
                if (generation != currentGeneration) return
                val row = rows[resource.url] ?: return
                row.finished = true
                row.success = true
                row.progress.isIndeterminate = false
                row.progress.progress = 100
                row.status.text = (if (row.cached) "缓存命中" else "下载完成") +
                    " · ${Formatter.formatShortFileSize(this@BatchDownloadActivity, file.length())}"
                updateSummary()
            }

            override fun onError(resource: AnimationResource, error: Throwable?) {
                if (generation != currentGeneration) return
                val row = rows[resource.url] ?: return
                row.finished = true
                row.progress.isIndeterminate = false
                row.status.text = "失败：${error?.message ?: "下载中断"}"
                updateSummary()
            }
        })
    }

    private fun updateSummary() {
        val completed = rows.values.count { it.finished }
        val success = rows.values.count { it.success }
        summary.text = "已处理 $completed / ${rows.size} · 成功 $success · 失败 ${completed - success}"
        batchProgress.max = rows.size
        batchProgress.progress = completed
        if (completed == rows.size) {
            startButton.isEnabled = true
            clearCacheButton.isEnabled = true
            urlsInput.isEnabled = true
            cancelButton.isEnabled = false
        }
    }

    private fun cancelDownloads() {
        generation++
        tasks.forEach { it.cancel() }
        tasks = emptyList()
        rows.values.filter { !it.finished }.forEach {
            it.progress.isIndeterminate = false
            it.status.text = "已取消"
        }
        summary.text = "批次已取消 · 已成功 ${rows.values.count { it.success }} / ${rows.size}"
        startButton.isEnabled = true
        clearCacheButton.isEnabled = true
        urlsInput.isEnabled = true
        cancelButton.isEnabled = false
    }

    private fun clearCache() {
        val currentGeneration = ++generation
        startButton.isEnabled = false
        clearCacheButton.isEnabled = false
        urlsInput.isEnabled = false
        summary.text = "正在清理缓存…"
        Thread({
            val result = runCatching {
                GiftPlayer.clearCache()
                GiftPlayer.getCacheSize()
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != currentGeneration) return@runOnUiThread
                rows.clear()
                rowsContainer.removeAllViews()
                tasks = emptyList()
                batchProgress.progress = 0
                summary.text = result.fold(
                    onSuccess = { remaining ->
                        if (remaining == 0L) "缓存已清空" else
                            "清理完成，剩余 ${Formatter.formatShortFileSize(this, remaining)}（使用中的资源会保留）"
                    },
                    onFailure = { "缓存清理失败：${it.message ?: it.javaClass.simpleName}" },
                )
                startButton.isEnabled = true
                clearCacheButton.isEnabled = true
                urlsInput.isEnabled = true
            }
        }, "demo-clear-cache").start()
    }

    override fun onDestroy() {
        generation++
        tasks.forEach { it.cancel() }
        tasks = emptyList()
        super.onDestroy()
    }

    private data class DownloadRow(
        val progress: ProgressBar,
        val status: TextView,
        val cached: Boolean,
        var finished: Boolean = false,
        var success: Boolean = false,
    )
}
