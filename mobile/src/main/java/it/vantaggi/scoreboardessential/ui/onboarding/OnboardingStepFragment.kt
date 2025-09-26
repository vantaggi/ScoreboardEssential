package it.vantaggi.scoreboardessential.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import it.vantaggi.scoreboardessential.databinding.FragmentOnboardingStepBinding

// Using open to allow extension
open class OnboardingStepFragment : Fragment() {

    private var _binding: FragmentOnboardingStepBinding? = null
    private val binding get() = _binding!!

    private var titleRes: Int = 0
    private var descriptionRes: Int = 0
    private var iconRes: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            titleRes = it.getInt(ARG_TITLE)
            descriptionRes = it.getInt(ARG_DESCRIPTION)
            iconRes = it.getInt(ARG_ICON)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.onboardingTitle.setText(titleRes)
        binding.onboardingDescription.setText(descriptionRes)
        binding.onboardingIcon.setImageResource(iconRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val ARG_ICON = "arg_icon"

        fun newInstance(
            @StringRes titleRes: Int,
            @StringRes descriptionRes: Int,
            @DrawableRes iconRes: Int
        ): OnboardingStepFragment {
            val fragment = OnboardingStepFragment()
            val args = Bundle()
            args.putInt(ARG_TITLE, titleRes)
            args.putInt(ARG_DESCRIPTION, descriptionRes)
            args.putInt(ARG_ICON, iconRes)
            fragment.arguments = args
            return fragment
        }
    }
}