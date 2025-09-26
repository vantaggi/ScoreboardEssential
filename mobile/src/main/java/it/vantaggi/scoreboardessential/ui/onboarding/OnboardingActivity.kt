package it.vantaggi.scoreboardessential.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import it.vantaggi.scoreboardessential.MainViewModel
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var pagerAdapter: OnboardingPagerAdapter
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.MainViewModelFactory(
            (application as ScoreboardEssentialApplication).matchRepository,
            application
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        setupListeners()
    }

    private fun setupListeners() {
        binding.skipButton.setOnClickListener {
            finishOnboarding()
        }

        binding.finishButton.setOnClickListener {
            finishOnboarding()
        }

        binding.nextButton.setOnClickListener {
            if (binding.viewPager.currentItem < pagerAdapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == pagerAdapter.itemCount - 1) {
                    binding.nextButton.visibility = View.GONE
                    binding.finishButton.visibility = View.VISIBLE
                    binding.skipButton.visibility = View.INVISIBLE
                } else {
                    binding.nextButton.visibility = View.VISIBLE
                    binding.finishButton.visibility = View.GONE
                    binding.skipButton.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun finishOnboarding() {
        mainViewModel.onOnboardingFinished()
        finish()
    }
}