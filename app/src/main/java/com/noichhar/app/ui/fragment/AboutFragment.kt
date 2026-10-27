package com.noichhar.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.noichhar.app.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val b get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false); return b.root
    }
    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, state: Bundle?) {
        val version = runCatching { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName }.getOrNull()
        b.tvVersion.text = version?.let { "v$it" } ?: "Version unavailable"
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
