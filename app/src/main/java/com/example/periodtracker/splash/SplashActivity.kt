package com.example.periodtracker.splash

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.periodtracker.databinding.ActivitySplashBinding
import com.example.periodtracker.onboarding.OnboardingActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var progress = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Full-screen gradient background
        binding.splashRoot.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#F0E6FA"),
                Color.parseColor("#FDE8F3"),
                Color.parseColor("#FFFFFF")
            )
        )

        // Clip logo to circle
        binding.logoCircle.clipToOutline = true
        binding.logoCircle.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        startProgress()
    }

    private fun startProgress() {
        val runnable = object : Runnable {
            override fun run() {
                if (progress <= 100) {
                    binding.progressBar.progress = progress

                    binding.tvLoading.text = when {
                        progress < 30 -> "Loading..."
                        progress < 60 -> "Setting things up..."
                        progress < 85 -> "Almost ready..."
                        else          -> "Just a moment..."
                    }

                    progress += 2
                    handler.postDelayed(this, 100) // 50ms × 50 steps ≈ 2.5 seconds
                } else {
                    goToNextScreen()
                }
            }
        }

        handler.post(runnable)
    }

    private fun goToNextScreen() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}