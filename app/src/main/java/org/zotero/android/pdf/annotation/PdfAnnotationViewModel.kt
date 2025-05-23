package org.zotero.android.pdf.annotation

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.ScreenArguments
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.database.objects.AnnotationsConfig
import org.zotero.android.pdf.annotation.data.PdfAnnotationArgs
import org.zotero.android.pdf.annotation.data.PdfAnnotationColorResult
import org.zotero.android.pdf.annotation.data.PdfAnnotationCommentResult
import org.zotero.android.pdf.annotation.data.PdfAnnotationDeleteResult
import org.zotero.android.pdf.annotation.data.PdfAnnotationFontSizeResult
import org.zotero.android.pdf.annotation.data.PdfAnnotationSizeResult
import org.zotero.android.pdf.data.PdfReaderCurrentThemeEventStream
import org.zotero.android.pdf.data.PdfReaderThemeDecider
import org.zotero.android.screens.tagpicker.data.TagPickerArgs
import org.zotero.android.screens.tagpicker.data.TagPickerResult
import org.zotero.android.sync.Tag
import javax.inject.Inject

@HiltViewModel
internal class PdfAnnotationViewModel @Inject constructor(
    private val pdfReaderCurrentThemeEventStream: PdfReaderCurrentThemeEventStream,
    private val pdfReaderThemeDecider: PdfReaderThemeDecider,
) : BaseViewModel2<PdfAnnotationViewState, PdfAnnotationViewEffect>(PdfAnnotationViewState()) {

    private var isDeletingAnnotation = false
    private lateinit var args: PdfAnnotationArgs
    private var pdfReaderThemeCancellable: Job? = null
    private var isTablet: Boolean = false

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(tagPickerResult: TagPickerResult) {
        if (tagPickerResult.callPoint == TagPickerResult.CallPoint.PdfReaderAnnotationScreen) {
            updateState {
                copy(tags = tagPickerResult.tags)
            }
            EventBus.getDefault().post(TagPickerResult(tagPickerResult.tags, TagPickerResult.CallPoint.PdfReaderScreen))
        }
    }


    fun init(args: PdfAnnotationArgs, isTablet: Boolean) = initOnce {
        this.args = args
        this.isTablet = isTablet
        EventBus.getDefault().register(this)
        updateState {
            copy(isDark = pdfReaderCurrentThemeEventStream.currentValue()!!.isDark)
        }
        startObservingTheme()

        val annotation = args.selectedAnnotation!!

        val colors = AnnotationsConfig.colors(annotation.type)
        val selectedColor = annotation.color
        updateState {
            copy(
                color = selectedColor,
                colors = colors,
                annotation = annotation,
                tags = args.selectedAnnotation.tags,
                commentFocusText = annotation.comment,
                size = annotation.lineWidth ?: 1.0f,
                fontSize = annotation.fontSize ?: 12.0f
            )
        }
    }

    fun setOsTheme(isDark: Boolean) {
        pdfReaderThemeDecider.setCurrentOsTheme(isOsThemeDark = isDark)
    }

    private fun startObservingTheme() {
        this.pdfReaderThemeCancellable = pdfReaderCurrentThemeEventStream.flow()
            .onEach { data ->
                updateState {
                    copy(isDark = data!!.isDark)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onTagsClicked() {
//        if (!annotation.isAuthor(viewState.userId)) {
//            return
//        }

        val selected = viewState.tags.map { it.name }.toSet()
        ScreenArguments.tagPickerArgs = TagPickerArgs(
            libraryId = args.library.identifier,
            selectedTags = selected,
            tags = emptyList(),
            callPoint = TagPickerResult.CallPoint.PdfReaderAnnotationScreen,
        )

        triggerEffect(PdfAnnotationViewEffect.NavigateToTagPickerScreen)
    }

    override fun onCleared() {
        if (!isDeletingAnnotation) {
            postAnnotationCommentResult()
        }
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    private fun postAnnotationCommentResult() {
        EventBus.getDefault().post(
            PdfAnnotationCommentResult(
                annotationKey = viewState.annotation!!.key,
                comment = viewState.commentFocusText
            )
        )
    }

    fun onCommentTextChange(comment: String) {
        updateState {
            copy(commentFocusText = comment)
        }
    }

    fun onColorSelected(color: String) {
        updateState {
            copy(color = color)
        }
        EventBus.getDefault().post(
            PdfAnnotationColorResult(
                annotationKey = viewState.annotation!!.key,
                color = color
            )
        )
    }

    fun onSizeChanged(newSize: Float) {
        updateState {
            copy(size = newSize)
        }
        EventBus.getDefault().post(
            PdfAnnotationSizeResult(
                key = viewState.annotation!!.key,
                size = newSize
            )
        )
    }

    fun onDeleteAnnotation() {
        isDeletingAnnotation = true
        EventBus.getDefault().post(
            PdfAnnotationDeleteResult(
                key = viewState.annotation!!.readerKey,
            )
        )
        triggerEffect(PdfAnnotationViewEffect.Back)
    }

    fun onFontSizeDecrease() {
        updateState {
            copy(fontSize = viewState.fontSize - 0.5f)
        }
        postFontSizeChangeUpdate()
    }

    fun onFontSizeIncrease() {
        updateState {
            copy(fontSize = viewState.fontSize + 0.5f)
        }
        postFontSizeChangeUpdate()
    }

    private fun postFontSizeChangeUpdate() {
        EventBus.getDefault().post(
            PdfAnnotationFontSizeResult(
                key = viewState.annotation!!.key,
                size = viewState.fontSize
            )
        )
    }

    fun onDone() {
        if (!isTablet) {
            postAnnotationCommentResult()
        }
        triggerEffect(PdfAnnotationViewEffect.Back)
    }

}

internal data class PdfAnnotationViewState(
    val isDark: Boolean = false,
    val annotation: org.zotero.android.pdf.data.PDFAnnotation? = null,
    val tags: List<Tag> = emptyList(),
    val commentFocusText: String = "",
    val color: String = "",
    val colors: List<String> = emptyList(),
    val fontSize: Float = 12f,
    val size: Float = 1.0f,
) : ViewState

internal sealed class PdfAnnotationViewEffect : ViewEffect {
    object NavigateToTagPickerScreen: PdfAnnotationViewEffect()
    object Back: PdfAnnotationViewEffect()
}
