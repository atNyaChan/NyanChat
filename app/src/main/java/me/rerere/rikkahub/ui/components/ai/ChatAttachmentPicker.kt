package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.ui.sketch.SketchDialog
import me.rerere.ui.sketch.SketchResult
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

/**
 * 聊天输入框的附件选择动作：拍照、相册、视频、音频、文件和画板。
 */
internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onPickAudio: () -> Unit,
    val onPickFile: () -> Unit,
    val onSketch: () -> Unit,
)

/**
 * 注册聊天输入框附件选择所需的 launcher、权限和裁剪流程，返回一组供 UI 调用的动作。
 * 每次附件添加成功后调用 [onAttachmentAdded]。
 */
@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    onAttachmentAdded: () -> Unit,
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    // 新增附件前按内容 sha256 去重，避免同一个输入框里出现两个相同内容的附件
    fun addUniqueAttachments(candidates: List<Uri>, add: (List<Uri>) -> Unit) {
        if (candidates.isEmpty()) return
        val selection = filesManager.dedupeTempAttachments(candidates, inputState.attachmentUrls())
        if (selection.accepted.isNotEmpty()) add(selection.accepted)
        if (selection.duplicates > 0) {
            toaster.show(
                context.getString(R.string.chat_input_attachment_already_added),
                type = ToastType.Warning,
            )
        }
    }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            addUniqueAttachments(filesManager.createChatFilesByContents(listOf(croppedUri))) {
                inputState.addImages(it)
            }
            onAttachmentAdded()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                addUniqueAttachments(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!))) {
                    inputState.addImages(it)
                }
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                onAttachmentAdded()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            addUniqueAttachments(filesManager.createChatFilesByContents(listOf(croppedUri))) {
                inputState.addImages(it)
            }
            onAttachmentAdded()
        },
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (!setting.displaySetting.skipCropImage && selectedUris.size == 1) {
                    launchImageCrop(selectedUris.first())
                } else {
                    addUniqueAttachments(filesManager.createChatFilesByContents(selectedUris)) {
                        inputState.addImages(it)
                    }
                    onAttachmentAdded()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                addUniqueAttachments(filesManager.createChatFilesByContents(selectedUris)) {
                    inputState.addVideos(it)
                }
                onAttachmentAdded()
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                addUniqueAttachments(filesManager.createChatFilesByContents(selectedUris)) {
                    inputState.addAudios(it)
                }
                onAttachmentAdded()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                    val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                    if (isAllowedFileType(fileName, mime)) {
                        val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            ?: run {
                                toaster.show(
                                    context.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                                return@mapNotNull null
                            }
                        UIMessagePart.Document(url = localUri.toString(), fileName = fileName, mime = mime)
                    } else {
                        toaster.show(
                            context.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        null
                    }
                }
                if (documents.isNotEmpty()) {
                    val selection = filesManager.dedupeTempAttachments(
                        candidates = documents.map { it.url.toUri() },
                        existingUrls = inputState.attachmentUrls(),
                    )
                    val acceptedUrls = selection.accepted.mapTo(mutableSetOf()) { it.toString() }
                    val uniqueDocuments = documents.filter { it.url in acceptedUrls }
                    if (uniqueDocuments.isNotEmpty()) inputState.addFiles(uniqueDocuments)
                    if (selection.duplicates > 0) {
                        toaster.show(
                            context.getString(R.string.chat_input_attachment_already_added),
                            type = ToastType.Warning,
                        )
                    }
                    onAttachmentAdded()
                }
            }
        }

    val scope = rememberCoroutineScope()
    var showSketch by remember { mutableStateOf(false) }
    if (showSketch) {
        SketchDialog(
            onDismiss = { showSketch = false },
            onConfirm = { result ->
                showSketch = false
                scope.launch {
                    addUniqueAttachments(listOfNotNull(filesManager.createChatFileBySketch(result))) {
                        inputState.addImages(it)
                    }
                    onAttachmentAdded()
                }
            },
        )
    }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        onSketch = { showSketch = true },
    )
}

/** 把画板的结果存成聊天附件。 */
internal suspend fun FilesManager.createChatFileBySketch(result: SketchResult): Uri? = withContext(Dispatchers.IO) {
    createChatFilesByByteArrays(
        byteArrays = listOf(result.encode()),
        mimeType = result.mimeType,
    ).firstOrNull()
}
