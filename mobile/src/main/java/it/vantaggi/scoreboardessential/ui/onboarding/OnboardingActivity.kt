package it.vantaggi.scoreboardessential.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
            (application as ScoreboardEssentialApplication).userPreferencesRepository,
            (application as ScoreboardEssentialApplication).matchSettingsRepository,
            application,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: gli insets di sistema diventano padding del contenitore radice,
        // cosi' i bottoni ancorati in basso restano sopra la barra di navigazione.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

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

        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
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
            },
        )
    }

    private fun finishOnboarding() {
        mainViewModel.onOnboardingFinished()
        finish()
    }
}
