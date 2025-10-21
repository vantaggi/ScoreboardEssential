package it.vantaggi.scoreboardessential.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import it.vantaggi.scoreboardessential.R

class OnboardingPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0 ->
                OnboardingStepFragment.newInstance(
                    R.string.onboarding_score_title,
                    R.string.onboarding_score_description,
                    R.drawable.ic_stats,
                )
            1 ->
                OnboardingStepFragment.newInstance(
                    R.string.onboarding_timers_title,
                    R.string.onboarding_timers_description,
                    R.drawable.ic_person_add,
                )
            2 ->
                OnboardingStepFragment.newInstance(
                    R.string.onboarding_wear_title,
                    R.string.onboarding_wear_description,
                    R.drawable.ic_watch_connected,
                )
            else -> throw IllegalStateException("Invalid position: $position")
        }
}
