package com.keke.giftplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * Created by keke on 2026/07/14.
 * Desc:
 */
class MainActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.bt_gift).setOnClickListener {
            startActivity(Intent(this, GiftPlayerActivity::class.java))
        }
    }
}