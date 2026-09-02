package com.yks2027.tracker.platform

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Suspend-style access to Activity Result contracts for the shared PlatformFiles /
 * TimerCompletionScheduler contracts. Launchers are registered in MainActivity.onCreate
 * (before STARTED, as the framework requires); one in-flight request per contract.
 */
class ActivityBridge {

    private var openDocument: ActivityResultLauncher<Array<String>>? = null
    private var createDocument: ActivityResultLauncher<String>? = null
    private var openTree: ActivityResultLauncher<Uri?>? = null
    private var permission: ActivityResultLauncher<String>? = null

    private var pendingOpen: CompletableDeferred<Uri?>? = null
    private var pendingCreate: CompletableDeferred<Uri?>? = null
    private var pendingTree: CompletableDeferred<Uri?>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private var createMime: String = "*/*"

    fun attach(activity: ComponentActivity) {
        openDocument = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingOpen?.complete(uri); pendingOpen = null
        }
        createDocument = activity.registerForActivityResult(
            object : ActivityResultContracts.CreateDocument("*/*") {
                override fun createIntent(context: android.content.Context, input: String) =
                    super.createIntent(context, input).setType(createMime)
            },
        ) { uri -> pendingCreate?.complete(uri); pendingCreate = null }
        openTree = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            pendingTree?.complete(uri); pendingTree = null
        }
        permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermission?.complete(granted); pendingPermission = null
        }
    }

    fun detach(activity: ComponentActivity) {
        openDocument = null; createDocument = null; openTree = null; permission = null
        pendingOpen?.complete(null); pendingCreate?.complete(null); pendingTree?.complete(null)
        pendingPermission?.complete(false)
    }

    suspend fun openDocument(mimeTypes: Array<String>): Uri? {
        val launcher = openDocument ?: return null
        val d = CompletableDeferred<Uri?>(); pendingOpen = d
        launcher.launch(mimeTypes)
        return d.await()
    }

    suspend fun createDocument(suggestedName: String, mimeType: String): Uri? {
        val launcher = createDocument ?: return null
        val d = CompletableDeferred<Uri?>(); pendingCreate = d; createMime = mimeType
        launcher.launch(suggestedName)
        return d.await()
    }

    suspend fun openDocumentTree(): Uri? {
        val launcher = openTree ?: return null
        val d = CompletableDeferred<Uri?>(); pendingTree = d
        launcher.launch(null)
        return d.await()
    }

    suspend fun requestPermission(name: String): Boolean {
        val launcher = permission ?: return false
        val d = CompletableDeferred<Boolean>(); pendingPermission = d
        launcher.launch(name)
        return d.await()
    }
}
