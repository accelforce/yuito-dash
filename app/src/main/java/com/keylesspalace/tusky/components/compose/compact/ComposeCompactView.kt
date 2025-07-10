package com.keylesspalace.tusky.components.compose.compact

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.statusLength
import com.keylesspalace.tusky.components.compose.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.components.compose.ComposeTokenizer
import com.keylesspalace.tusky.components.compose.ComposeViewModel
import com.keylesspalace.tusky.components.compose.MediaUploader
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.databinding.ViewComposeCompactBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.defaultFinders
import com.keylesspalace.tusky.util.highlightSpans
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ComposeCompactView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs),
    ComposeAutoCompleteAdapter.AutocompletionProvider {
    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mediaUploader: MediaUploader

    @Inject
    lateinit var serviceClient: ServiceClient

    @Inject
    lateinit var draftHelper: DraftHelper

    @Inject
    lateinit var instanceInfoRepo: InstanceInfoRepository

    @Inject
    lateinit var preferences: SharedPreferences

    private val activeAccount = accountManager.activeAccount!!

    private val lifecycleScope by lazy {
        findViewTreeLifecycleOwner()!!.lifecycleScope
    }
    private var viewModelScope: CoroutineScope? = null

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ComposeViewModel

    private var maximumTootCharacters = InstanceInfoRepository.DEFAULT_CHARACTER_LIMIT
    private var charactersReservedPerUrl = InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL

    var onHeightChangeListener: () -> Unit = {}

    private val binding = ViewComposeCompactBinding.inflate(
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
        this,
    )

    fun init(savedStateHandle: SavedStateHandle) {
        this.savedStateHandle = savedStateHandle
        setupView()
        setupViewModel()
    }

    private fun setupView() {
        binding.composeTootButton.setOnClickListener {
            onSendClicked()
        }

        binding.composeEditField.setOnKeyListener { _, keyCode, event ->
            this.onKeyDown(keyCode, event)
        }

        binding.composeEditField.setAdapter(
            ComposeAutoCompleteAdapter(
                this,
                animateAvatar = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
                animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
                showBotBadge = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            ),
        )

        binding.composeEditField.setTokenizer(ComposeTokenizer())

        // work around Android platform bug -> https://issuetracker.google.com/issues/67102093
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
        ) {
            binding.composeEditField.setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun setupViewModel() {
        viewModelScope?.cancel()

        viewModelScope = CoroutineScope(SupervisorJob(lifecycleScope.coroutineContext.job))
        viewModel = ComposeViewModel(
            api,
            accountManager,
            mediaUploader,
            serviceClient,
            draftHelper,
            savedStateHandle,
            null,
            instanceInfoRepo,
        )

        viewModelScope!!.launch {
            viewModel.instanceInfo.collect { instanceData ->
                maximumTootCharacters = instanceData.maxChars
                charactersReservedPerUrl = instanceData.charactersReservedPerUrl
            }
        }

        binding.composeEditField.setText(viewModel.startingText)
        binding.composeEditField.setSelection(binding.composeEditField.length())

        val mentionColour = binding.composeEditField.linkTextColors.defaultColor
        binding.composeEditField.text.highlightSpans(mentionColour, defaultFinders)
        binding.composeEditField.doAfterTextChanged { editable ->
            editable!!.highlightSpans(mentionColour, defaultFinders)
            viewModel.updateContent(editable.toString())
        }
    }

    fun updateButtons(enabled: Boolean) {
        binding.composeTootButton.isEnabled = enabled
    }

    private fun onSendClicked() {
        sendStatus()
    }

    private fun sendStatus() {
        updateButtons(false)
        val contentText = binding.composeEditField.text.toString()
        val characterCount = calculateTextLength()
        if ((characterCount <= 0 || contentText.isBlank()) && viewModel.media.value.isEmpty()) {
            binding.composeEditField.error = context.getString(R.string.error_empty)
            updateButtons(true)
        } else if (characterCount <= maximumTootCharacters) {
            findViewTreeLifecycleOwner()!!.lifecycleScope.launch {
                viewModel.sendStatus(contentText, "", activeAccount.id)
                deleteDraftAndReset()
            }
        } else {
            binding.composeEditField.error = context.getString(R.string.error_compose_character_limit)
            updateButtons(true)
        }
    }

    private fun deleteDraftAndReset() {
        viewModel.deleteDraft()
        setupViewModel()

        updateButtons(true)
    }

    private fun calculateTextLength(): Int {
        return statusLength(
            binding.composeEditField.text,
            null,
            charactersReservedPerUrl,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (h != oldh) {
            onHeightChangeListener.invoke()
        }
    }

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
    }
}
