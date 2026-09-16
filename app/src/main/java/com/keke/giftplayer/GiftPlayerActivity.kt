package com.keke.giftplayer

import android.annotation.SuppressLint
import android.os.Bundle
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
import com.keke.giftplayer.animation.widget.GiftAnimationPlayerView

/**
 * Created by keke on 2026/7/14.
 * Desc:
 */
class GiftPlayerActivity : AppCompatActivity() {
    private lateinit var playerView: GiftAnimationPlayerView
    private lateinit var urlInput: EditText
    private lateinit var assetInput: EditText
    private lateinit var formatSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gift_player)
        bindViews()
        bindPlayerCallback()
        bindActions()

        assetInput.setText("test1.mp4")
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
    }

    private fun bindActions() {
        findViewById<Button>(R.id.playButton).setOnClickListener {
            playFromRemoteUrl()
        }
        findViewById<Button>(R.id.playAssetButton).setOnClickListener {
            playFromAssetFile()
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
            playerView.stop(clear = true)
            progressBar.progress = 0
            showStatus("Stopped")
        }
    }

    private fun bindPlayerCallback() {
        playerView.setCallback(object : AnimationCallback {
            override fun onLoadStart(request: AnimationRequest) {
                progressBar.progress = 0
                showStatus("Loading ${request.source.label()} as ${request.format.name}")
            }

            override fun onStart(request: AnimationRequest) {
                showStatus("Playing")
            }

            override fun onProgress(request: AnimationRequest, progress: Float) {
                progressBar.progress = (progress.coerceIn(0f, 1f) * progressBar.max).toInt()
            }

            override fun onComplete(request: AnimationRequest) {
                progressBar.progress = progressBar.max
                showStatus("Complete")
            }

            override fun onCancel(request: AnimationRequest) {
                showStatus("Cancelled")
            }

            override fun onError(request: AnimationRequest, error: AnimationError) {
                showStatus("Error: ${error.message()}")
            }
        })
    }

    private fun playFromRemoteUrl() {
//        val animationUrl = "https://xxxx/xxx/xxx.svga"
        var animationUrl = urlInput.text?.toString()?.trim().orEmpty()
        if (animationUrl.isBlank()) {
            animationUrl = "https://s1.videocc.net/default-img/donate-svga/diamond.svga"
        }
        playerView.play(
            AnimationRequest(
                source = AnimationSource.Url(
                    url = animationUrl,
                    downloadPriority = DemoDownloadPriority.ImmediateGift.level,
                ),
                format = AnimationFormat.Auto,
                loopCount = Int.MAX_VALUE,
                scaleType = AnimationScaleType.FitCenter,
                fillMode = AnimationFillMode.Clear,
                enableAudio = true,
                pauseWhenInvisible = false,
            )
        )

    }

    private fun playFromAssetFile() {
        val assetFileName = assetInput.text?.toString()?.trim().orEmpty()
        if (assetFileName.isBlank()) {
            showStatus("Please enter an asset file name first.")
            return
        }
        playerView.play(
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

    private fun selectedFormat(): AnimationFormat {
        return when (formatSpinner.selectedItemPosition) {
            1 -> AnimationFormat.Svga
            2 -> AnimationFormat.Pag
            3 -> AnimationFormat.Vap
            else -> AnimationFormat.Auto
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
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
            is AnimationError.PlayerPluginMissing -> "Player plugin missing: $format"
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
