package com.habitrpg.wearos.habitica.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import androidx.wear.activity.ConfirmationActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.habitrpg.android.habitica.databinding.ActivityWrapperBinding
import com.habitrpg.wearos.habitica.managers.LoadingManager
import com.habitrpg.wearos.habitica.ui.viewmodels.BaseViewModel
import com.habitrpg.wearos.habitica.ui.views.IndeterminateProgressView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseActivity<B: ViewBinding, VM: BaseViewModel> : ComponentActivity() {
    @Inject
    lateinit var loadingManager: LoadingManager

    val messageClient: MessageClient by lazy { Wearable.getMessageClient(this) }
    val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(this) }
    companion object {
        var currentActivityClassName: String? = null
    }
    private lateinit var wrapperBinding: ActivityWrapperBinding
    protected lateinit var binding: B
    abstract val viewModel: VM

    private var progressView: IndeterminateProgressView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        wrapperBinding = ActivityWrapperBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        wrapperBinding.root.addView(binding.root)
        setContentView(wrapperBinding.root)

        viewModel.errorValues.observe(this) {
            val intent = Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, it.title)
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 3000)
            }
            startActivity(intent)
        }

        loadingManager.isLoading.observe(this) {
            if (it) {
                startAnimatingProgress()
            } else {
                stopAnimatingProgress()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentActivityClassName = this.localClassName
    }

    fun startAnimatingProgress() {
        if (progressView == null) {
            progressView = IndeterminateProgressView(this)
            wrapperBinding.root.addView(progressView)
            progressView?.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            progressView?.startAnimation()
        }
    }

    fun stopAnimatingProgress() {
        if (progressView != null) {
            wrapperBinding.root.removeView(progressView)
            progressView = null
        } else {
            wrapperBinding.root.children.forEach {
                if (it is IndeterminateProgressView) {
                    wrapperBinding.root.removeView(it)
                }
            }
        }
    }

    internal fun openRemoteActivity(url: String) {
        sendMessage("open_activity", url, null)
    }

    internal fun sendMessage(
        permission: String,
        url: String,
        data: ByteArray?,
        function: ((Boolean) -> Unit)? = null
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val info = Tasks.await(
                capabilityClient.getCapability(
                    permission,
                    CapabilityClient.FILTER_REACHABLE
                )
            )
            val nodeID = info.nodes.firstOrNull { it.isNearby }
            if (nodeID != null) {
                function?.invoke(true)
                Tasks.await(
                    messageClient.sendMessage(
                        nodeID.id,
                        url,
                        data
                    )
                )
            } else {
                function?.invoke(false)
            }
        }
    }
}