package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.FragmentPageStorageBinding
import com.yenaly.han1meviewer.logic.PageStorageManager
import com.yenaly.han1meviewer.logic.TranslationManager
import com.yenaly.han1meviewer.ui.adapter.PageStorageAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PageStorageFragment : Fragment() {
    
    private var _binding: FragmentPageStorageBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PageStorageAdapter
    private val scope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPageStorageBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        setupRecyclerView()
        loadPages()
        
        binding.swipeRefresh.setOnRefreshListener {
            loadPages()
        }
        
        binding.btnClearAll.setOnClickListener {
            showClearConfirmation()
        }
        
        // Initialize storage
        PageStorageManager.initialize()
    }
    
    private fun setupRecyclerView() {
        adapter = PageStorageAdapter(
            onItemClick = { url ->
                openPageDetail(url)
            },
            onRetranslateClick = { url ->
                retranslatePage(url)
            },
            onDeleteClick = { url ->
                deletePage(url)
            }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadPages() {
        scope.launch {
            val pages = kotlin.runCatching {
                PageStorageManager.getAllPageVersions()
                    .sortedByDescending { it.lastChecked }
            }.getOrElse { emptyList() }
            
            adapter.submitList(pages)
            binding.swipeRefresh.isRefreshing = false
            
            updateStats(pages)
            
            if (pages.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun updateStats(pages: List<com.yenaly.han1meviewer.logic.model.PageVersion>) {
        val stats = PageStorageManager.getStorageStats()
        
        binding.statsText.text = getString(
            R.string.page_storage_stats,
            stats.totalPages,
            stats.translated,
            stats.failed,
            stats.stale,
            stats.unchanged
        )
        
        // Update status counts with colors
        binding.translatedCount.text = stats.translated.toString()
        binding.failedCount.text = stats.failed.toString()
        binding.staleCount.text = stats.stale.toString()
        binding.unchangedCount.text = stats.unchanged.toString()
        
        // Set text colors based on status
        binding.translatedCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        binding.failedCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
        binding.staleCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
        binding.unchangedCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
    }
    
    private fun openPageDetail(url: String) {
        showToast("Page: $url")
    }
    
    private fun retranslatePage(url: String) {
        scope.launch {
            val success = kotlin.runCatching {
                TranslationManager.forceRetranslate(url)
            }.getOrElse { false }
            
            if (success) {
                showToast("Retranslation started for: $url")
                loadPages()
            } else {
                showToast("Failed to retranslate: $url")
            }
        }
    }
    
    private fun deletePage(url: String) {
        PageStorageManager.deletePageData(url)
        loadPages()
        showToast("Deleted: $url")
    }
    
    private fun showClearConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_all_pages)
            .setMessage(R.string.clear_all_pages_confirmation)
            .setPositiveButton(R.string.clear) { dialog: AlertDialog, _ ->
                PageStorageManager.clearAll()
                loadPages()
                showToast(getString(R.string.all_pages_cleared))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog: AlertDialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}