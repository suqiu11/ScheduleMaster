package com.timedapplauncher.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.timedapplauncher.databinding.ActivityAppPickerBinding
import com.timedapplauncher.databinding.ItemAppBinding

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "选择 App"

        allApps = loadLaunchableApps()
        adapter = AppAdapter { app ->
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_PACKAGE, app.packageName)
                putExtra(EXTRA_ACTIVITY_CLASS, app.activityClassName)
                putExtra(EXTRA_LABEL, app.label)
            })
            finish()
        }
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter
        adapter.submitList(allApps)

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString().orEmpty())
            }
        })
    }

    /**
     * 列出所有 LAUNCHER Activity（同一包多个桌面图标会分别显示）。
     */
    private fun loadLaunchableApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return resolveList
            .map { ri ->
                val ai = ri.activityInfo
                AppInfo(
                    packageName = ai.packageName,
                    activityClassName = ai.name,
                    label = ri.loadLabel(pm).toString().ifBlank { ai.loadLabel(pm).toString() },
                    icon = ai.loadIcon(pm)
                )
            }
            .distinctBy { "${it.packageName}/${it.activityClassName}" }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) allApps
        else allApps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                it.activityClassName.contains(query, ignoreCase = true)
        }
        adapter.submitList(filtered)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    data class AppInfo(
        val packageName: String,
        val activityClassName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable
    )

    class AppAdapter(private val onClick: (AppInfo) -> Unit) :
        ListAdapter<AppInfo, AppAdapter.VH>(object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) =
                a.packageName == b.packageName && a.activityClassName == b.activityClassName

            override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a == b
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(app: AppInfo) {
                b.imageIcon.setImageDrawable(app.icon)
                b.textLabel.text = app.label
                b.textPackage.text = app.packageName
                b.root.setOnClickListener { onClick(app) }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_ACTIVITY_CLASS = "extra_activity_class"
        const val EXTRA_LABEL = "extra_label"
    }
}
