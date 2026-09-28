package it.vantaggi.scoreboardessential.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import it.vantaggi.scoreboardessential.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var pagerAdapter: OnboardingPagerAdapter

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

    /**
     * Scrive la preferenza da qui, nelle stesse preferenze e con la stessa chiave che
     * MainViewModel legge all'avvio. Prima passava da un MainViewModel costruito apposta, che
     * nel suo init mandava 0-0 all'orologio mentre quello della partita era vivo sotto.
     */
    private fun finishOnboarding() {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit { putBoolean("onboarding_completed", true) }
        finish()
    }
}
