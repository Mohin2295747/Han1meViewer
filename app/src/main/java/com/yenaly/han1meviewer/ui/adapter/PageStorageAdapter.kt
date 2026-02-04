package com.yenaly.han1meviewer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.ItemPageStorageBinding
import com.yenaly.han1meviewer.logic.model.PageVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PageStorageAdapter(
    private val onItemClick: (String) -> Unit,
    private val onRetranslateClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<PageVersion, PageStorageAdapter.ViewHolder>(DIFF_CALLBACK) {
    
    class ViewHolder(
        private val binding: ItemPageStorageBinding,
        private val onItemClick: (String) -> Unit,
        private val onRetranslateClick: (String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(pageVersion: PageVersion) {
            binding.apply {
                // Extract domain from URL for display
                val domain = try {
                    java.net.URL(pageVersion.url).host
                } catch (e: Exception) {
                    pageVersion.url.take(50) + (if (pageVersion.url.length > 50) "..." else "")
                }
                
                tvDomain.text = domain
                tvTitle.text = pageVersion.parsedData.title.ifEmpty { "No title extracted" }
                tvLastChecked.text = "Last checked: ${formatDateTime(pageVersion.lastChecked)}"
                tvChecksum.text = "Checksum: ${pageVersion.checksum.take(8)}..."
                
                // Set status with icon and color - USING EXISTING ICONS
                when (pageVersion.translationStatus) {
                    PageVersion.TranslationStatus.TRANSLATED -> {
                        ivStatus.setImageResource(R.drawable.ic_baseline_check_circle_24)
                        tvStatus.text = "✓ Translated"
                        tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.green))
                        root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.translated_bg))
                    }
                    PageVersion.TranslationStatus.FAILED -> {
                        ivStatus.setImageResource(R.drawable.baseline_error_outline_24) // Changed to baseline_
                        tvStatus.text = "✗ Failed"
                        tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.red))
                        root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.failed_bg))
                    }
                    PageVersion.TranslationStatus.STALE -> {
                        ivStatus.setImageResource(R.drawable.baseline_warning_24) // Changed to baseline_
                        tvStatus.text = "↻ Needs Update"
                        tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.orange))
                        root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.stale_bg))
                    }
                    PageVersion.TranslationStatus.UNCHANGED -> {
                        ivStatus.setImageResource(R.drawable.ic_baseline_check_circle_24)
                        tvStatus.text = "✓ Unchanged"
                        tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.blue))
                        root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.unchanged_bg))
                    }
                    else -> {
                        ivStatus.setImageResource(R.drawable.ic_baseline_access_time_24)
                        tvStatus.text = "⏳ Pending"
                        tvStatus.setTextColor(ContextCompat.getColor(root.context, R.color.gray))
                        root.setBackgroundColor(ContextCompat.getColor(root.context, R.color.pending_bg))
                    }
                }
                
                // Set click listeners
                root.setOnClickListener { onItemClick(pageVersion.url) }
                btnRetranslate.setOnClickListener { onRetranslateClick(pageVersion.url) }
                btnDelete.setOnClickListener { onDeleteClick(pageVersion.url) }
                
                // Show/hide retranslate button based on status
                btnRetranslate.visibility = when (pageVersion.translationStatus) {
                    PageVersion.TranslationStatus.FAILED,
                    PageVersion.TranslationStatus.STALE -> View.VISIBLE
                    else -> View.GONE
                }
            }
        }
        
        private fun formatDateTime(timestamp: Long): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPageStorageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick, onRetranslateClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PageVersion>() {
            override fun areItemsTheSame(oldItem: PageVersion, newItem: PageVersion): Boolean {
                return oldItem.url == newItem.url
            }
            
            override fun areContentsTheSame(oldItem: PageVersion, newItem: PageVersion): Boolean {
                return oldItem == newItem
            }
        }
    }
}