package com.keke.giftplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keke.giftplayer.animation.core.AnimationCallback
import com.keke.giftplayer.animation.core.AnimationError
import com.keke.giftplayer.animation.core.AnimationFillMode
import com.keke.giftplayer.animation.core.AnimationFormat
import com.keke.giftplayer.animation.core.AnimationRequest
import com.keke.giftplayer.animation.core.AnimationScaleType
import com.keke.giftplayer.animation.core.AnimationSource
import com.keke.giftplayer.animation.download.AnimationDownloadConfig
import com.keke.giftplayer.animation.download.AnimationDownloadPriority
import com.keke.giftplayer.animation.download.AnimationResourceManager
import com.keke.giftplayer.animation.widget.QueuedAnimationPlayerView

/**
 * Created by keke on 2026/7/14.
 * Desc:
 */
class GiftQueuePlayerActivity : AppCompatActivity() {
    private lateinit var playerView: QueuedAnimationPlayerView
    private lateinit var urlInput: EditText
    private lateinit var assetInput: EditText
    private lateinit var formatSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val statusHistory = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gift_queue_player)
        AnimationResourceManager.init(
            context = applicationContext,
            config = AnimationDownloadConfig(),
        )
        bindViews()
        bindPlayerCallback()
        bindActions()
    }

    override fun onDestroy() {
        playerView.release()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        urlInput = findViewById(R.id.urlInput)

        assetInput = findViewById(R.id.assetInput)
        formatSpinner = findViewById(R.id.formatSpinner)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)

        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.demo_format_options,
            android.R.layout.simple_spinner_item,
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatSpinner.adapter = adapter

        assetInput.setText("test1.mp4")
    }

    private fun bindActions() {
        findViewById<Button>(R.id.playButton).setOnClickListener {
            enqueueFromRemoteUrl()
        }
        findViewById<Button>(R.id.playAssetButton).setOnClickListener {
            enqueueFromAssetFile()
        }
        findViewById<Button>(R.id.pauseButton).setOnClickListener {
            playerView.pause()
            showStatus("Paused")
        }
        findViewById<Button>(R.id.resumeButton).setOnClickListener {
            playerView.resume()
            showStatus("Resumed")
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            playerView.stop()
            progressBar.progress = 0
            showStatus("Stopped; queue cleared")
        }
    }

    private fun bindPlayerCallback() {
        playerView.setCallback(object : AnimationCallback {
            override fun onLoadStart(request: AnimationRequest) {
                showStatus("Loading ${request.source.label()} as ${request.format.name}")
            }

            override fun onStart(request: AnimationRequest) {
                progressBar.progress = 0
                showStatus("Playing ${request.source.label()} as ${request.format.name}")
            }

            override fun onProgress(request: AnimationRequest, progress: Float) {
                progressBar.progress = (progress.coerceIn(0f, 1f) * progressBar.max).toInt()
            }

            override fun onComplete(request: AnimationRequest) {
                progressBar.progress = progressBar.max
                showStatus("Complete: ${request.source.label()}")
            }

            override fun onCancel(request: AnimationRequest) {
                showStatus("Cancelled: ${request.source.label()}")
            }

            override fun onError(request: AnimationRequest, error: AnimationError) {
                showStatus("Error (${request.source.label()}): ${error.message()}")
            }
        })
    }

    private fun enqueueFromRemoteUrl() {
        val list = listOf(
            "https://res.joyachat.com/gift/prod/1784259395913-hpzdoneiths.mp4?md5=2d518b80932cf710b739d66f2e6993cf",
            "https://s1.videocc.net/default-img/donate-svga/diamond.svga",
            "https://res.joyachat.com/gift/prod/1784258494360-t2xmmswicy.mp4?md5=2e431b864cc0801afe519e84af1122b9",
            "https://res.joyachat.com/gift/prod/1784258494360-t2xmmswicy.mp4?md5=2e431b864cc0801afe519e84af1122b9",
        )
        list.forEach {
            enqueue(
                AnimationRequest(
                    source = AnimationSource.Url(it, AnimationDownloadPriority.Highest),
                    format = selectedFormat(),
                    loopCount = 1,
                    scaleType = AnimationScaleType.FitCenter,
                    fillMode = AnimationFillMode.Clear,
                    enableAudio = true,
                    pauseWhenInvisible = false,
                )
            )
        }


    }

    private fun enqueueFromAssetFile() {
        val assetFileName = assetInput.text?.toString()?.trim().orEmpty()
        if (assetFileName.isBlank()) {
            showStatus("Please enter an asset file name first.")
            return
        }
        enqueue(
            AnimationRequest(
                source = AnimationSource.Asset(assetFileName),
                format = selectedFormat(),
                loopCount = 1,
                autoPlay = true,
                scaleType = AnimationScaleType.FitCenter,
                fillMode = AnimationFillMode.Forward,
                enableAudio = true,
                pauseWhenInvisible = true,
            ),
        )
    }

    private fun enqueue(request: AnimationRequest) {
        showStatus("Enqueued ${request.source.label()} as ${request.format.name}")
        playerView.enqueue(request)
    }

    private fun selectedFormat(): AnimationFormat {
        return when (formatSpinner.selectedItemPosition) {
            1 -> AnimationFormat.Svga
            2 -> AnimationFormat.Pag
            3 -> AnimationFormat.Vap
            else -> AnimationFormat.Auto
        }
    }

    private fun showStatus(message: String) {
        Log.i("GiftPlayerQueueDemo", message)
        statusHistory.addLast(message)
        while (statusHistory.size > 4) {
            statusHistory.removeFirst()
        }
        statusText.text = statusHistory.joinToString("\n")
    }

    private fun AnimationSource.label(): String {
        return when (this) {
            is AnimationSource.Asset -> "asset $name"
            is AnimationSource.FilePath -> "file $path"
            is AnimationSource.Url -> "URL"
        }
    }

    private fun AnimationError.message(): String {
        return when (this) {
            is AnimationError.InvalidSource -> reason
            is AnimationError.UnsupportedFormat -> "Unsupported format"
            is AnimationError.DownloadFailed -> {
                val type = cause?.javaClass?.simpleName ?: "Unknown"
                val message = cause?.message.orEmpty()
                if (message.isBlank()) "Download failed: $type" else "Download failed: $type, $message"
            }
            is AnimationError.FileNotFound -> "File not found: $path"
            is AnimationError.DecodeFailed -> "Decode failed: ${cause?.message.orEmpty()}"
            is AnimationError.RenderFailed -> "Render failed: ${cause?.message.orEmpty()}"
            is AnimationError.Cancelled -> reason
        }
    }
}
