package com.ghadirb.yadavar.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.databinding.FragmentSettingsSubscriptionBinding
import com.ghadirb.yadavar.ui.subscription.SubscriptionActivity
import com.ghadirb.yadavar.utils.SubscriptionManager

class SettingsSubscriptionFragment : Fragment() {

    private var _binding: FragmentSettingsSubscriptionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsSubscriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderStatus()
        binding.buttonSubscription.setOnClickListener {
            startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun renderStatus() {
        val status = SubscriptionManager.premiumExpiryLabel(requireContext())
            ?: if (SubscriptionManager.hasPersonalKey(requireContext())) {
                getString(R.string.subscription_personal_key)
            } else {
                getString(
                    R.string.subscription_free_left,
                    SubscriptionManager.remainingFreeLifetime(requireContext()),
                    SubscriptionManager.FREE_AI_LIFETIME_LIMIT
                )
            }
        binding.textSubscriptionStatus.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
