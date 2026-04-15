package com.nobodyz.han1meviewer.ui.fragment

interface ToolbarHost {
    fun setupToolbar(title: CharSequence, canNavigateBack: Boolean = true)
    fun hideToolbar()
    fun showToolbar()
}
