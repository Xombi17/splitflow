package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ui.motion.SpringButton
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ai.CactusLocalModelManager
import com.example.ai.LocalSplitAiService
import com.example.data.GuestLedgerRepository
import com.example.data.GuestSessionStore
import com.example.data.OnboardingPreferenceStore
import com.example.data.SplitFlowRepository
import com.example.data.ThemePreferenceStore
import com.example.data.UpiQrStore
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.onGoogleSignInClicked
import com.example.firebase.signOutFromSplitFlow
import com.example.model.DebtTransfer
import com.example.model.DietPreference
import com.example.ui.components.ContactImportSheet
import com.example.ui.components.CreateGroupSheet
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.GroupCategory
import com.example.model.SettlementRecord
import com.example.ui.components.AiBadge
import com.example.ui.components.BatchReceiptImporterSheet
import com.example.ui.components.DiningDrinksSplitSheet
import com.example.ui.components.DockStaggeredEntrance
import com.example.ui.components.EditorialCard
import com.example.ui.components.ExpenseLensSheet
import com.example.ui.components.FeatureCoachmarkOverlay
import com.example.ui.components.LaunchSplashOverlay
import com.example.ui.components.LocalEntryRevealEpoch
import com.example.ui.components.LocalEntryRevealReady
import com.example.ui.components.LogRecurringBillDialog
import com.example.ui.components.MyUpiQrSheet
import com.example.ui.components.PaymentNudgeSheet
import com.example.ui.components.PhotoProofViewerSheet
import com.example.ui.components.ReceiptViewerSheet
import com.example.ui.components.SettleUpSheet
import com.example.ui.components.SharedSplitSheet
import com.example.ui.components.SmartSplitSheet
import com.example.ui.components.SpendCoachSheet
import com.example.ui.components.SplitFlowMotion
import com.example.ui.components.TripWrapUpSheet
import com.example.ui.components.VoiceCommandSheet
import com.example.ui.components.tactileClickable
import com.example.ui.motion.SpringNavHostContainer
import com.example.ui.motion.SpringPhysics
import com.example.ui.motion.SpringPreset
import com.example.ui.motion.SpringTransitionStyle
import com.example.ui.motion.springClickable
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.LocalPrivacyBlur
import com.example.ui.theme.SplitFlowTheme
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.viewmodel.LedgerUiState
import com.example.viewmodel.SplitFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check & initialize Cactus local AI runtime
        configureAppCheck(this, intent)
        CactusLocalModelManager.initContext(this)

        // STEP 2: Enable Edge-to-Edge rendering
        enableEdgeToEdge()

        setContent {
            SplitFlowRoot()
        }
    }
}

private enum class SplitFlowTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    GROUPS("Groups", Icons.Rounded.Groups, Icons.Outlined.Groups),
    ACTIVITY("Activity", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    PEOPLE("People", Icons.Rounded.People, Icons.Outlined.PeopleOutline)
}

@Composable
private fun SplitFlowRoot() {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val guestSessionStore = remember(context) { GuestSessionStore(context) }
    val themePreferenceStore = remember(context) { ThemePreferenceStore(context) }
    val onboardingStore = remember(context) { OnboardingPreferenceStore(context) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var isGuestMode by rememberSaveable { mutableStateOf(guestSessionStore.isGuestSessionActive()) }
    var guestSessionId by rememberSaveable { mutableStateOf(guestSessionStore.getOrCreateGuestSessionId()) }
    var isDarkMode by rememberSaveable { mutableStateOf(themePreferenceStore.isDarkMode()) }
    var hasSeenOnboarding by rememberSaveable { mutableStateOf(onboardingStore.hasCompletedOnboarding()) }
    var isReplayingOnboarding by rememberSaveable { mutableStateOf(false) }
    var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
    var entryRevealReady by rememberSaveable { mutableStateOf(false) }
    var entryRevealEpoch by rememberSaveable { mutableIntStateOf(0) }

    val triggerReplayLaunchAnimation: () -> Unit = {
        entryRevealReady = false
        entryRevealEpoch++
        showLaunchSplash = true
    }

    val updateDarkMode: (Boolean) -> Unit = { dark ->
        isDarkMode = dark
        themePreferenceStore.setDarkMode(dark)
    }

    val onboardingAiState by CactusLocalModelManager.state.collectAsStateWithLifecycle()
    val rootScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        CactusLocalModelManager.refreshCatalogAndHydrate(context)
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null && isGuestMode) {
            guestSessionStore.endGuestSession()
            isGuestMode = false
        }
    }

    SplitFlowTheme(darkTheme = isDarkMode) {
        CompositionLocalProvider(
            LocalEntryRevealReady provides entryRevealReady,
            LocalEntryRevealEpoch provides entryRevealEpoch
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val user = currentUser
                val rootStage = when {
                    !hasSeenOnboarding || isReplayingOnboarding -> "onboarding"
                    user != null -> "auth_${user.uid}"
                    isGuestMode -> "guest_$guestSessionId"
                    else -> "auth_gate"
                }
                SpringNavHostContainer(
                    targetState = rootStage,
                    style = SpringTransitionStyle.SHARED_SCALE,
                    label = "root_flow_spring"
                ) { stage ->
                    when {
                        stage == "onboarding" -> {
                            OnboardingScreen(
                                isReplay = isReplayingOnboarding,
                                localAiState = onboardingAiState,
                                onDownloadVoiceModel = { slug ->
                                    rootScope.launch {
                                        CactusLocalModelManager.prepareWhistle(context, slug)
                                    }
                                },
                                onDownloadVisionModel = { slug ->
                                    rootScope.launch {
                                        CactusLocalModelManager.prepareMirage(context, slug)
                                    }
                                },
                                onDownloadAllModels = {
                                    rootScope.launch {
                                        CactusLocalModelManager.prepareAllModels(context)
                                    }
                                },
                                onComplete = {
                                    if (!hasSeenOnboarding) {
                                        hasSeenOnboarding = true
                                        onboardingStore.setOnboardingCompleted(true)
                                    }
                                    isReplayingOnboarding = false
                                    // Trigger a crisp staggered Home entrance right after onboarding closes
                                    entryRevealEpoch++
                                    entryRevealReady = true
                                }
                            )
                        }

                        stage.startsWith("auth_") && user != null -> {
                            key(user.uid) {
                                AuthenticatedSplitFlowShell(
                                    currentUserId = user.uid,
                                    isGuestMode = false,
                                    isDarkMode = isDarkMode,
                                    guestSessionStore = guestSessionStore,
                                    onboardingStore = onboardingStore,
                                    onSetDarkMode = updateDarkMode,
                                    onReplayOnboarding = { isReplayingOnboarding = true },
                                    onReplayLaunchAnimation = triggerReplayLaunchAnimation,
                                    onExitGuestMode = {}
                                )
                            }
                        }

                        stage.startsWith("guest_") -> {
                            key(guestSessionId) {
                                AuthenticatedSplitFlowShell(
                                    currentUserId = guestSessionId,
                                    isGuestMode = true,
                                    isDarkMode = isDarkMode,
                                    guestSessionStore = guestSessionStore,
                                    onboardingStore = onboardingStore,
                                    onSetDarkMode = updateDarkMode,
                                    onReplayOnboarding = { isReplayingOnboarding = true },
                                    onReplayLaunchAnimation = triggerReplayLaunchAnimation,
                                    onExitGuestMode = {
                                        guestSessionStore.endGuestSession()
                                        isGuestMode = false
                                    }
                                )
                            }
                        }

                        else -> {
                            AuthScreen(
                                onAuthSuccess = {
                                    guestSessionStore.endGuestSession()
                                    isGuestMode = false
                                    entryRevealEpoch++
                                    entryRevealReady = true
                                },
                                onContinueAsGuest = {
                                    guestSessionId = guestSessionStore.startGuestSession()
                                    isGuestMode = true
                                    entryRevealEpoch++
                                    entryRevealReady = true
                                },
                                onReplayOnboarding = { isReplayingOnboarding = true }
                            )
                        }
                    }
                }

                if (showLaunchSplash) {
                    key(entryRevealEpoch) {
                        LaunchSplashOverlay(
                            onStartRevealHandoff = { entryRevealReady = true },
                            onFinished = {
                                entryRevealReady = true
                                showLaunchSplash = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedSplitFlowShell(
    currentUserId: String,
    isGuestMode: Boolean,
    isDarkMode: Boolean,
    guestSessionStore: GuestSessionStore,
    onboardingStore: OnboardingPreferenceStore,
    onSetDarkMode: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit,
    onReplayLaunchAnimation: () -> Unit,
    onExitGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    val viewModel: SplitFlowViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val repo = if (isGuestMode) {
                    GuestLedgerRepository(app)
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    SplitFlowRepository(db)
                }
                SplitFlowViewModel(
                    repository = repo,
                    localAiService = LocalSplitAiService(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    // Automatically migrate any local Guest Mode data into Cloud Firestore upon Google Sign-In
    LaunchedEffect(currentUserId, isGuestMode) {
        if (!isGuestMode) {
            viewModel.migrateGuestDataIfPresent(guestSessionStore)
        }
    }

    val triggerGoogleUpgrade: () -> Unit = {
        isGoogleSigningIn = true
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { isGoogleSigningIn = false },
            onAuthError = { msg ->
                isGoogleSigningIn = false
                viewModel.showToast(msg, isError = true)
            },
            scope = scope,
            onAuthCancelled = { isGoogleSigningIn = false }
        )
    }

    // Observe Android network connectivity for offline banner
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun checkOnline(): Boolean {
            val net = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        viewModel.updateOfflineStatus(!checkOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.updateOfflineStatus(false)
            }

            override fun onLost(network: Network) {
                viewModel.updateOfflineStatus(!checkOnline())
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { cm?.unregisterNetworkCallback(callback) }
        }
    }

    val uiState by viewModel.ledgerState.collectAsStateWithLifecycle()
    val toastEvent by viewModel.toastEvent.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isPrivacyBlurEnabled by viewModel.isPrivacyBlurEnabled.collectAsStateWithLifecycle()
    val receiptLensState by viewModel.receiptLensState.collectAsStateWithLifecycle()
    val voiceSplitState by viewModel.voiceSplitState.collectAsStateWithLifecycle()
    val batchItems by viewModel.batchItems.collectAsStateWithLifecycle()
    val tripStoryState by viewModel.tripStoryState.collectAsStateWithLifecycle()
    val spendCoachState by viewModel.spendCoachState.collectAsStateWithLifecycle()
    val coachFollowUpAnswer by viewModel.coachFollowUpAnswer.collectAsStateWithLifecycle()
    val isCoachStreaming by viewModel.isCoachStreaming.collectAsStateWithLifecycle()
    val localAiEngineState by viewModel.localAiEngineState.collectAsStateWithLifecycle()

    val toggleDarkMode: () -> Unit = {
        val next = !isDarkMode
        onSetDarkMode(next)
        val profile = (uiState as? LedgerUiState.Ready)?.snapshot?.profile
        if (profile != null) {
            viewModel.updateProfile(
                displayName = profile.displayName,
                upiVpa = profile.upiVpa,
                defaultCurrency = profile.currencyEnum,
                darkMode = next
            )
        }
    }

    CompositionLocalProvider(LocalPrivacyBlur provides isPrivacyBlurEnabled) {
    SplitFlowTheme(darkTheme = isDarkMode) {
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(toastEvent?.id) {
            val ev = toastEvent ?: return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = ev.message,
                actionLabel = ev.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && ev.actionLabel == "UNDO") {
                viewModel.undoLastDelete()
            }
            viewModel.dismissToast()
        }

        var currentTab by remember { mutableStateOf(SplitFlowTab.HOME) }
        var selectedGroupId by remember { mutableStateOf<String?>(null) }

        // Modal sheet & interactive tour states
        var showQuickAddChooser by remember { mutableStateOf(false) }
        var showCreateGroupSheet by remember { mutableStateOf(false) }
        var createGroupInitialCategory by remember { mutableStateOf(GroupCategory.TRIP) }
        var createGroupInitialName by remember { mutableStateOf<String?>(null) }
        var showContactImportSheet by remember { mutableStateOf(false) }
        var openManualAddFriendSignal by remember { mutableIntStateOf(0) }

        val openCreateGroup: (GroupCategory, String?) -> Unit = { cat, suggested ->
            createGroupInitialCategory = cat
            createGroupInitialName = suggested
            showCreateGroupSheet = true
        }
        var showProfileSheet by remember { mutableStateOf(false) }
        var showMyUpiQrSheet by remember { mutableStateOf(false) }
        var showHomeCoachmarks by rememberSaveable { mutableStateOf(!onboardingStore.hasCompletedCoachmarks()) }
        var customUpiQrPath by remember(context) { mutableStateOf(UpiQrStore.getCustomUpiQrPath(context)) }
        var showExpenseLens by remember { mutableStateOf(false) }
        var showBatchImporter by remember { mutableStateOf(false) }
        var showVoiceSplitter by remember { mutableStateOf(false) }
        var showDiningStudio by remember { mutableStateOf(false) }
        var showSmartSplitEditor by remember { mutableStateOf(false) }
        var showSpendCoach by remember { mutableStateOf(false) }
        var coachGroupScopeId by remember { mutableStateOf<String?>(null) }
        var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
        var editorInitialGroupId by remember { mutableStateOf<String?>(null) }
        var editorInitialFriendId by remember { mutableStateOf<String?>(null) }
        var settleUpTransfer by remember { mutableStateOf<DebtTransfer?>(null) }
        var nudgeTransfer by remember { mutableStateOf<DebtTransfer?>(null) }
        var sharedSplitId by remember { mutableStateOf<String?>(null) }
        var viewingReceiptExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
        var viewingSettlementProof by remember { mutableStateOf<SettlementRecord?>(null) }
        var tripStoryGroup by remember { mutableStateOf<ExpenseGroup?>(null) }
        var loggingRecurringExpense by remember { mutableStateOf<ExpenseRecord?>(null) }

        if (currentTab != SplitFlowTab.HOME && selectedGroupId == null) {
            BackHandler { currentTab = SplitFlowTab.HOME }
        }

        val readySnapshot = (uiState as? LedgerUiState.Ready)?.snapshot

        val openSpendCoach: (String?) -> Unit = { groupId ->
            coachGroupScopeId = groupId
            viewModel.resetSpendCoach()
            showSpendCoach = true
        }

        val tokens = SplitFlowThemeTokens.colors
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isExpanded = maxWidth >= 720.dp

            // Subtle atmospheric editorial top canvas gradient wash
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(tokens.canvasGlowTop, Color.Transparent)
                        )
                    )
            )

            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = tokens.cardSurface,
                        header = {
                            FloatingActionButton(
                                onClick = { showQuickAddChooser = true },
                                containerColor = tokens.dockAddBg,
                                contentColor = tokens.dockAddIcon,
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .testTag("rail_quick_add_fab")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Quick add split")
                            }
                        }
                    ) {
                        SplitFlowTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = {
                                    currentTab = tab
                                    if (tab != SplitFlowTab.GROUPS) selectedGroupId = null
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            SplitFlowTabBody(
                                uiState = uiState,
                                currentTab = currentTab,
                                selectedGroupId = selectedGroupId,
                                isOffline = isOffline,
                                isGuestMode = isGuestMode,
                                isDarkMode = isDarkMode,
                                isPrivacyBlurEnabled = isPrivacyBlurEnabled,
                                hasCustomUpiQr = !customUpiQrPath.isNullOrBlank(),
                                viewModel = viewModel,
                                onToggleDarkMode = toggleDarkMode,
                                onTogglePrivacyBlur = { viewModel.togglePrivacyBlur() },
                                onSelectTab = { currentTab = it },
                                onSelectGroup = { selectedGroupId = it },
                                onOpenProfile = { showProfileSheet = true },
                                onOpenMyUpiQr = { showMyUpiQrSheet = true },
                                onOpenAiLens = {
                                    viewModel.resetReceiptLens()
                                    showExpenseLens = true
                                },
                                onOpenBatchImporter = {
                                    viewModel.resetBatchImporter()
                                    showBatchImporter = true
                                },
                                onOpenVoiceSplitter = {
                                    viewModel.resetVoiceSplit()
                                    showVoiceSplitter = true
                                },
                                onOpenSpendCoach = openSpendCoach,
                                onOpenDiningStudio = { showDiningStudio = true },
                                onOpenTripStory = { grp ->
                                    viewModel.resetTripStory()
                                    tripStoryGroup = grp
                                },
                                onOpenManualSplit = { grpId, friendId, edit ->
                                    editingExpense = edit
                                    editorInitialGroupId = grpId
                                    editorInitialFriendId = friendId
                                    showSmartSplitEditor = true
                                },
                                onOpenSettleUp = { settleUpTransfer = it },
                                onOpenNudge = { nudgeTransfer = it },
                                onOpenSharedSplit = { sharedSplitId = it.id },
                                onViewReceipt = { viewingReceiptExpense = it },
                                onViewSettlementProof = { viewingSettlementProof = it },
                                onLogRecurringCycle = { loggingRecurringExpense = it },
                                onOpenImportContacts = { showContactImportSheet = true },
                                onOpenCreateGroup = openCreateGroup,
                                openManualAddFriendSignal = openManualAddFriendSignal,
                                modifier = Modifier.widthIn(max = 860.dp)
                            )
                        }
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 86.dp)
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(innerPadding)
                    ) {
                        SplitFlowTabBody(
                            uiState = uiState,
                            currentTab = currentTab,
                            selectedGroupId = selectedGroupId,
                            isOffline = isOffline,
                            isGuestMode = isGuestMode,
                            isDarkMode = isDarkMode,
                            isPrivacyBlurEnabled = isPrivacyBlurEnabled,
                            hasCustomUpiQr = !customUpiQrPath.isNullOrBlank(),
                            viewModel = viewModel,
                            onToggleDarkMode = toggleDarkMode,
                            onTogglePrivacyBlur = { viewModel.togglePrivacyBlur() },
                            onSelectTab = { currentTab = it },
                            onSelectGroup = { selectedGroupId = it },
                            onOpenProfile = { showProfileSheet = true },
                            onOpenMyUpiQr = { showMyUpiQrSheet = true },
                            onOpenAiLens = {
                                viewModel.resetReceiptLens()
                                showExpenseLens = true
                            },
                            onOpenBatchImporter = {
                                viewModel.resetBatchImporter()
                                showBatchImporter = true
                            },
                            onOpenVoiceSplitter = {
                                viewModel.resetVoiceSplit()
                                showVoiceSplitter = true
                            },
                            onOpenSpendCoach = openSpendCoach,
                            onOpenDiningStudio = { showDiningStudio = true },
                            onOpenTripStory = { grp ->
                                viewModel.resetTripStory()
                                tripStoryGroup = grp
                            },
                            onOpenManualSplit = { grpId, friendId, edit ->
                                editingExpense = edit
                                editorInitialGroupId = grpId
                                editorInitialFriendId = friendId
                                showSmartSplitEditor = true
                            },
                            onOpenSettleUp = { settleUpTransfer = it },
                            onOpenNudge = { nudgeTransfer = it },
                            onOpenSharedSplit = { sharedSplitId = it.id },
                            onViewReceipt = { viewingReceiptExpense = it },
                            onViewSettlementProof = { viewingSettlementProof = it },
                            onLogRecurringCycle = { loggingRecurringExpense = it },
                            onOpenImportContacts = { showContactImportSheet = true },
                            onOpenCreateGroup = openCreateGroup,
                            openManualAddFriendSignal = openManualAddFriendSignal
                        )

                        // Floating Frosted Capsule Navigation Dock with Synchronized Bottom-Up Spring Entrance
                        DockStaggeredEntrance(
                            delayMs = 210L,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            FloatingFrostedNavDock(
                                currentTab = currentTab,
                                isQuickAddOpen = showQuickAddChooser,
                                onSelectTab = { tab ->
                                    currentTab = tab
                                    if (tab != SplitFlowTab.GROUPS) selectedGroupId = null
                                },
                                onQuickAddClick = { showQuickAddChooser = true }
                            )
                        }
                    }
                }
            }

            // Wait until the staggered Home entrance settles before presenting interactive coachmarks
            val entryRevealReadyNow = LocalEntryRevealReady.current
            var coachmarkGateOpen by remember { mutableStateOf(false) }
            LaunchedEffect(entryRevealReadyNow, showHomeCoachmarks) {
                if (entryRevealReadyNow && showHomeCoachmarks) {
                    kotlinx.coroutines.delay(540L)
                    coachmarkGateOpen = true
                } else {
                    coachmarkGateOpen = false
                }
            }

            // Interactive Spotlight Feature Coachmarks Overlay on Home (first launch or replayed from Profile)
            if (showHomeCoachmarks && coachmarkGateOpen && readySnapshot != null && currentTab == SplitFlowTab.HOME && !showProfileSheet) {
                FeatureCoachmarkOverlay(
                    onDismiss = {
                        showHomeCoachmarks = false
                        onboardingStore.setCoachmarksCompleted(true)
                    }
                )
            }

            // --- MODAL WORKBENCHES ---

            if (showProfileSheet && readySnapshot != null) {
                ModalBottomSheet(
                    onDismissRequest = { showProfileSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                    ) {
                        ProfileScreen(
                            snapshot = readySnapshot,
                            isGuestMode = isGuestMode,
                            isDarkMode = isDarkMode,
                            isGoogleSigningIn = isGoogleSigningIn,
                            isPrivacyBlurEnabled = isPrivacyBlurEnabled,
                            customUpiQrPath = customUpiQrPath,
                            onCustomUpiQrChanged = { newPath ->
                                customUpiQrPath = newPath
                                viewModel.showToast(
                                    if (newPath != null) "Personal UPI QR saved" else "Custom UPI QR removed"
                                )
                            },
                            onOpenMyUpiQr = {
                                showProfileSheet = false
                                showMyUpiQrSheet = true
                            },
                            onToggleDarkMode = { dark ->
                                onSetDarkMode(dark)
                            },
                            onTogglePrivacyBlur = { viewModel.togglePrivacyBlur() },
                            onSaveProfile = { name, upi, curr, dark ->
                                onSetDarkMode(dark)
                                viewModel.updateProfile(name, upi, curr, dark)
                            },
                            onClearAllLedgerData = { viewModel.clearAllLedgerData() },
                            onReplayOnboarding = {
                                showProfileSheet = false
                                onReplayOnboarding()
                            },
                            onReplayCoachmarks = {
                                showProfileSheet = false
                                currentTab = SplitFlowTab.HOME
                                selectedGroupId = null
                                showHomeCoachmarks = true
                            },
                            onReplayLaunchAnimation = {
                                showProfileSheet = false
                                currentTab = SplitFlowTab.HOME
                                selectedGroupId = null
                                onReplayLaunchAnimation()
                            },
                            onSignInWithGoogle = triggerGoogleUpgrade,
                            onSignOut = {
                                showProfileSheet = false
                                if (isGuestMode) {
                                    onExitGuestMode()
                                } else {
                                    signOutFromSplitFlow(credentialManager, scope)
                                }
                            },
                            onClose = { showProfileSheet = false }
                        )
                    }
                }
            }

            if (showMyUpiQrSheet && readySnapshot != null) {
                MyUpiQrSheet(
                    snapshot = readySnapshot,
                    customUpiQrPath = customUpiQrPath,
                    onCustomUpiQrChanged = { newPath -> customUpiQrPath = newPath },
                    onOpenProfileToEditVpa = {
                        showMyUpiQrSheet = false
                        showProfileSheet = true
                    },
                    onFeedback = { msg -> viewModel.showToast(msg) },
                    onDismiss = { showMyUpiQrSheet = false }
                )
            }

            if (showQuickAddChooser) {
                QuickAddChooserSheet(
                    onQuickSendCommand = { command ->
                        showQuickAddChooser = false
                        val scopeGroupId = if (currentTab == SplitFlowTab.GROUPS) selectedGroupId else null
                        viewModel.resetVoiceSplit()
                        showVoiceSplitter = true
                        viewModel.parseNaturalLanguageSplit(
                            transcript = command,
                            targetGroupId = scopeGroupId,
                            autoApply = true,
                            fromDeviceSpeechFallback = false
                        )
                    },
                    onSelectCreateGroup = {
                        showQuickAddChooser = false
                        openCreateGroup(GroupCategory.TRIP, null)
                    },
                    onSelectDiningStudio = {
                        showQuickAddChooser = false
                        showDiningStudio = true
                    },
                    onSelectAiLens = {
                        showQuickAddChooser = false
                        viewModel.resetReceiptLens()
                        showExpenseLens = true
                    },
                    onSelectBatchImport = {
                        showQuickAddChooser = false
                        viewModel.resetBatchImporter()
                        showBatchImporter = true
                    },
                    onSelectVoice = {
                        showQuickAddChooser = false
                        viewModel.resetVoiceSplit()
                        showVoiceSplitter = true
                    },
                    onSelectSpendCoach = {
                        showQuickAddChooser = false
                        openSpendCoach(selectedGroupId)
                    },
                    onSelectManual = {
                        showQuickAddChooser = false
                        editingExpense = null
                        editorInitialGroupId = selectedGroupId
                        editorInitialFriendId = null
                        showSmartSplitEditor = true
                    },
                    onDismiss = { showQuickAddChooser = false }
                )
            }

            if (showCreateGroupSheet && readySnapshot != null) {
                CreateGroupSheet(
                    snapshot = readySnapshot,
                    isGuestMode = isGuestMode,
                    initialCategory = createGroupInitialCategory,
                    initialSuggestedName = createGroupInitialName,
                    onQuickAddMember = { friendName, onAdded ->
                        val palette = listOf("#0E6F4E", "#2C4C7E", "#B45333", "#C5962C", "#5B4B8A", "#1F6E78")
                        val colorHex = palette[kotlin.math.abs(friendName.hashCode()) % palette.size]
                        viewModel.addMember(
                            name = friendName,
                            email = null,
                            upiVpa = null,
                            isGuest = isGuestMode,
                            avatarColorHex = colorHex,
                            dietPreference = DietPreference.NON_VEG,
                            drinksAlcohol = true,
                            onComplete = onAdded
                        )
                    },
                    onOpenImportContacts = { showContactImportSheet = true },
                    onCreateGroup = { name, cat, curr, members, accent, budgetMinor ->
                        viewModel.createGroup(name, cat, curr, members, accent, budgetMinor) {
                            showCreateGroupSheet = false
                            currentTab = SplitFlowTab.GROUPS
                        }
                    },
                    onDismiss = { showCreateGroupSheet = false }
                )
            }

            if (showContactImportSheet && readySnapshot != null) {
                ContactImportSheet(
                    existingMembers = readySnapshot.members,
                    onImportContacts = { pickedContacts ->
                        viewModel.importDeviceContacts(pickedContacts) {
                            showContactImportSheet = false
                        }
                    },
                    onAddManuallyInstead = {
                        showContactImportSheet = false
                        showCreateGroupSheet = false
                        currentTab = SplitFlowTab.PEOPLE
                        openManualAddFriendSignal++
                    },
                    onDismiss = { showContactImportSheet = false }
                )
            }

            if (showDiningStudio && readySnapshot != null) {
                DiningDrinksSplitSheet(
                    snapshot = readySnapshot,
                    initialGroupId = selectedGroupId,
                    todayIso = viewModel.todayIso(),
                    onSaveExpense = { record ->
                        viewModel.saveExpense(record) {
                            showDiningStudio = false
                        }
                    },
                    onDismiss = { showDiningStudio = false }
                )
            }

            if (showBatchImporter && readySnapshot != null) {
                BatchReceiptImporterSheet(
                    snapshot = readySnapshot,
                    batchItems = batchItems,
                    initialGroupId = selectedGroupId,
                    onStartBatch = { items, ctx -> viewModel.startBatchReceiptExtraction(items, ctx) },
                    onApproveItem = { itemId, grpId, payerId -> viewModel.approveBatchItem(itemId, grpId, payerId) },
                    onSkipItem = { itemId -> viewModel.skipBatchItem(itemId) },
                    onReset = { viewModel.resetBatchImporter() },
                    onDismiss = { showBatchImporter = false }
                )
            }

            if (showExpenseLens && readySnapshot != null) {
                ExpenseLensSheet(
                    snapshot = readySnapshot,
                    lensState = receiptLensState,
                    localAiState = localAiEngineState,
                    initialGroupId = selectedGroupId,
                    onAnalyzeBitmap = { viewModel.analyzeReceiptBitmap(it) },
                    onInstallMirage = { slug -> viewModel.installMirageModel(slug) },
                    onResetLens = { viewModel.resetReceiptLens() },
                    onSaveExpense = { record ->
                        viewModel.saveExpense(record) {
                            showExpenseLens = false
                            viewModel.resetReceiptLens()
                        }
                    },
                    onDismiss = { showExpenseLens = false }
                )
            }

            if (showVoiceSplitter && readySnapshot != null) {
                VoiceCommandSheet(
                    snapshot = readySnapshot,
                    voiceState = voiceSplitState,
                    localAiState = localAiEngineState,
                    initialGroupId = if (currentTab == SplitFlowTab.GROUPS) selectedGroupId else null,
                    onInstallWhistle = { slug -> viewModel.installWhistleModel(slug) },
                    onProcessAudio = { wavBytes, grpId, autoApply ->
                        viewModel.processVoiceAudio(wavBytes, grpId, autoApply)
                    },
                    onParseCommand = { cmd, grpId, autoApply, fromFallback ->
                        viewModel.parseNaturalLanguageSplit(cmd, grpId, autoApply, fromFallback)
                    },
                    onCommitPending = { res, title, grpId ->
                        viewModel.commitPendingVoiceResult(res, title, grpId)
                    },
                    onUndoApplied = { res ->
                        viewModel.undoVoiceExecution(res)
                    },
                    onNavigateToActivity = {
                        showVoiceSplitter = false
                        viewModel.resetVoiceSplit()
                        currentTab = SplitFlowTab.ACTIVITY
                    },
                    onResetVoice = { viewModel.resetVoiceSplit() },
                    onDismiss = {
                        showVoiceSplitter = false
                        viewModel.resetVoiceSplit()
                    }
                )
            }

            if (showSmartSplitEditor && readySnapshot != null) {
                SmartSplitSheet(
                    snapshot = readySnapshot,
                    editingExpense = editingExpense,
                    initialGroupId = editorInitialGroupId,
                    initialPersonalFriendId = editorInitialFriendId,
                    todayIso = viewModel.todayIso(),
                    onSaveExpense = { record ->
                        viewModel.saveExpense(record) {
                            showSmartSplitEditor = false
                            editingExpense = null
                        }
                    },
                    onDismiss = {
                        showSmartSplitEditor = false
                        editingExpense = null
                    },
                    onOpenDiningStudio = {
                        showSmartSplitEditor = false
                        editingExpense = null
                        showDiningStudio = true
                    }
                )
            }

            if (showSpendCoach && readySnapshot != null) {
                val scopeLabel = coachGroupScopeId?.let { gid ->
                    readySnapshot.groups.firstOrNull { it.id == gid }?.name
                } ?: "All Groups & Personal Ledger"
                SpendCoachSheet(
                    scopeLabel = scopeLabel,
                    coachState = spendCoachState,
                    followUpAnswer = coachFollowUpAnswer,
                    isStreaming = isCoachStreaming,
                    onGenerateReport = { viewModel.generateSpendCoachReport(coachGroupScopeId) },
                    onAskFollowUp = { q -> viewModel.askSpendCoachFollowUp(q, coachGroupScopeId) },
                    onDismiss = { showSpendCoach = false }
                )
            }

            settleUpTransfer?.let { transfer ->
                if (readySnapshot != null) {
                    SettleUpSheet(
                        transfer = transfer,
                        snapshot = readySnapshot,
                        customUpiQrPath = customUpiQrPath,
                        onCustomUpiQrChanged = { newPath ->
                            customUpiQrPath = newPath
                            if (newPath != null) {
                                viewModel.showToast("Personal UPI QR saved for settlements")
                            }
                        },
                        onConfirmSettlement = { fromId, toId, amtMinor, method, note, proofPath ->
                            viewModel.recordSettlement(
                                fromMemberId = fromId,
                                toMemberId = toId,
                                amountMinorUnits = amtMinor,
                                currency = transfer.currency,
                                paymentMethod = method,
                                groupId = transfer.groupId,
                                note = note,
                                paymentProofPath = proofPath
                            ) {
                                settleUpTransfer = null
                            }
                        },
                        onDismiss = { settleUpTransfer = null }
                    )
                }
            }

            nudgeTransfer?.let { transfer ->
                if (readySnapshot != null) {
                    val memberMap = readySnapshot.members.associateBy { it.id }
                    val groupName = transfer.groupId?.let { gid ->
                        readySnapshot.groups.firstOrNull { it.id == gid }?.name
                    }
                    PaymentNudgeSheet(
                        transfer = transfer,
                        fromMember = memberMap[transfer.fromMemberId],
                        toMember = memberMap[transfer.toMemberId],
                        groupName = groupName,
                        currency = transfer.currency,
                        onDismiss = { nudgeTransfer = null },
                        onFeedback = { msg -> viewModel.showToast(msg) }
                    )
                }
            }

            viewingReceiptExpense?.let { exp ->
                if (readySnapshot != null) {
                    ReceiptViewerSheet(
                        expense = exp,
                        defaultCurrency = readySnapshot.defaultCurrency,
                        onInspectSplit = { sharedSplitId = exp.id },
                        onDismiss = { viewingReceiptExpense = null }
                    )
                }
            }

            viewingSettlementProof?.let { settlement ->
                if (readySnapshot != null && !settlement.paymentProofPath.isNullOrBlank()) {
                    val memberMap = readySnapshot.members.associateBy { it.id }
                    val fromName = memberMap[settlement.fromMemberId]?.name ?: "Member"
                    val toName = memberMap[settlement.toMemberId]?.name ?: "Member"
                    PhotoProofViewerSheet(
                        title = "$fromName paid $toName",
                        subtitle = "${settlement.paymentMethodEnum.label} · ${settlement.dateLabel}" +
                            (settlement.note?.let { " · $it" } ?: ""),
                        photoPath = settlement.paymentProofPath,
                        amountMinor = settlement.amountMinorUnits,
                        currency = settlement.currencyEnum,
                        onDismiss = { viewingSettlementProof = null }
                    )
                }
            }

            tripStoryGroup?.let { grp ->
                if (readySnapshot != null) {
                    TripWrapUpSheet(
                        group = grp,
                        snapshot = readySnapshot,
                        storyState = tripStoryState,
                        onGenerateStory = { groupId -> viewModel.generateTripStory(groupId) },
                        onDismiss = { tripStoryGroup = null }
                    )
                }
            }

            loggingRecurringExpense?.let { recurring ->
                LogRecurringBillDialog(
                    expense = recurring,
                    onConfirm = { overrideAmountMinor ->
                        viewModel.logNextRecurringCycle(recurring, overrideAmountMinor)
                        loggingRecurringExpense = null
                    },
                    onDismiss = { loggingRecurringExpense = null }
                )
            }

            sharedSplitId?.let { id ->
                val liveExpense = readySnapshot?.expenses?.firstOrNull { it.id == id }
                if (liveExpense != null && readySnapshot != null) {
                    SharedSplitSheet(
                        expense = liveExpense,
                        snapshot = readySnapshot,
                        isGuestMode = isGuestMode,
                        onToggleLineItemClaim = { itemId, memberId ->
                            viewModel.toggleLineItemClaim(liveExpense, itemId, memberId)
                        },
                        onClaimGuestProfile = { mId, name, email, upi ->
                            viewModel.claimGuestProfile(mId, name, email, upi)
                        },
                        onEditExpense = { exp ->
                            sharedSplitId = null
                            editingExpense = exp
                            editorInitialGroupId = exp.groupId
                            editorInitialFriendId = null
                            showSmartSplitEditor = true
                        },
                        onDismiss = { sharedSplitId = null }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SplitFlowTabBody(
    uiState: LedgerUiState,
    currentTab: SplitFlowTab,
    selectedGroupId: String?,
    isOffline: Boolean,
    isGuestMode: Boolean,
    isDarkMode: Boolean,
    isPrivacyBlurEnabled: Boolean,
    hasCustomUpiQr: Boolean,
    viewModel: SplitFlowViewModel,
    onToggleDarkMode: () -> Unit,
    onTogglePrivacyBlur: () -> Unit,
    onSelectTab: (SplitFlowTab) -> Unit,
    onSelectGroup: (String?) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMyUpiQr: () -> Unit,
    onOpenAiLens: () -> Unit,
    onOpenBatchImporter: () -> Unit,
    onOpenVoiceSplitter: () -> Unit,
    onOpenSpendCoach: (groupId: String?) -> Unit,
    onOpenDiningStudio: () -> Unit,
    onOpenTripStory: (ExpenseGroup) -> Unit,
    onOpenManualSplit: (groupId: String?, personalFriendId: String?, editing: ExpenseRecord?) -> Unit,
    onOpenSettleUp: (DebtTransfer) -> Unit,
    onOpenNudge: (DebtTransfer) -> Unit,
    onOpenSharedSplit: (ExpenseRecord) -> Unit,
    onViewReceipt: (ExpenseRecord) -> Unit,
    onViewSettlementProof: (SettlementRecord) -> Unit,
    onLogRecurringCycle: (ExpenseRecord) -> Unit,
    onOpenImportContacts: () -> Unit,
    onOpenCreateGroup: (GroupCategory, String?) -> Unit,
    openManualAddFriendSignal: Int,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is LedgerUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = SplitFlowThemeTokens.colors.positive)
                    Text(
                        "Syncing SplitFlow Ledger...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is LedgerUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ledger Sync Error", style = MaterialTheme.typography.titleMedium, color = SplitFlowThemeTokens.colors.negative)
                        Text(uiState.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        is LedgerUiState.Ready -> {
            val snapshot = uiState.snapshot
            SpringNavHostContainer(
                targetState = currentTab,
                style = SpringTransitionStyle.SPATIAL_TAB,
                isForward = { initial, target -> target.ordinal >= initial.ordinal },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    SplitFlowTab.HOME -> DashboardScreen(
                        snapshot = snapshot,
                        isOffline = isOffline,
                        isGuestMode = isGuestMode,
                        isDarkMode = isDarkMode,
                        isPrivacyBlurEnabled = isPrivacyBlurEnabled,
                        hasCustomUpiQr = hasCustomUpiQr,
                        todayIso = viewModel.todayIso(),
                        onToggleDarkMode = onToggleDarkMode,
                        onTogglePrivacyBlur = onTogglePrivacyBlur,
                        onOpenProfile = onOpenProfile,
                        onOpenMyUpiQr = onOpenMyUpiQr,
                        onOpenAiLens = onOpenAiLens,
                        onOpenBatchImporter = onOpenBatchImporter,
                        onOpenVoiceSplitter = onOpenVoiceSplitter,
                        onOpenSpendCoach = { onOpenSpendCoach(null) },
                        onOpenDiningStudio = onOpenDiningStudio,
                        onOpenManualSplit = { onOpenManualSplit(null, null, null) },
                        onOpenGroup = { grp ->
                            onSelectGroup(grp.id)
                            onSelectTab(SplitFlowTab.GROUPS)
                        },
                        onOpenSettleUp = onOpenSettleUp,
                        onOpenNudge = onOpenNudge,
                        onLogRecurringCycle = onLogRecurringCycle,
                        onOpenSharedSplit = onOpenSharedSplit,
                        onViewReceipt = onViewReceipt,
                        onViewAllExpenses = { onSelectTab(SplitFlowTab.ACTIVITY) },
                        onOpenGroupsTab = { onOpenCreateGroup(GroupCategory.TRIP, null) },
                        onOpenPeopleTab = { onSelectTab(SplitFlowTab.PEOPLE) },
                        onOpenImportContacts = onOpenImportContacts,
                        modifier = modifier
                    )

                    SplitFlowTab.GROUPS -> GroupsScreen(
                        snapshot = snapshot,
                        isGuestMode = isGuestMode,
                        selectedGroupId = selectedGroupId,
                        onOpenProfile = onOpenProfile,
                        onSelectGroup = onSelectGroup,
                        onOpenCreateGroup = onOpenCreateGroup,
                        onUpdateGroupBudget = { grpId, cap ->
                            viewModel.updateGroupBudget(grpId, cap)
                        },
                        onUpdateGroupMemory = { grpId, notes ->
                            viewModel.updateGroupMemory(grpId, notes)
                        },
                        onOpenTripStory = onOpenTripStory,
                        onAddExpenseToGroup = { grp -> onOpenManualSplit(grp.id, null, null) },
                        onOpenSettleUp = onOpenSettleUp,
                        onOpenNudge = onOpenNudge,
                        onOpenSpendCoachForGroup = { grpId -> onOpenSpendCoach(grpId) },
                        onOpenSharedSplit = onOpenSharedSplit,
                        modifier = modifier
                    )

                    SplitFlowTab.ACTIVITY -> LedgerScreen(
                        snapshot = snapshot,
                        isGuestMode = isGuestMode,
                        onOpenProfile = onOpenProfile,
                        onOpenSharedSplit = onOpenSharedSplit,
                        onViewReceipt = onViewReceipt,
                        onViewSettlementProof = onViewSettlementProof,
                        onEditExpense = { exp -> onOpenManualSplit(exp.groupId, null, exp) },
                        onDeleteExpense = { exp -> viewModel.deleteExpenseWithUndo(exp) },
                        onToggleSettled = { exp -> viewModel.toggleExpenseSettled(exp) },
                        onLogRecurringCycle = onLogRecurringCycle,
                        modifier = modifier
                    )

                    SplitFlowTab.PEOPLE -> FriendsScreen(
                        snapshot = snapshot,
                        isGuestMode = isGuestMode,
                        onOpenProfile = onOpenProfile,
                        onAddMember = { name, email, upi, isGuest, colorHex, diet, drinks ->
                            viewModel.addMember(name, email, upi, isGuest, colorHex, diet, drinks)
                        },
                        onUpdateMemberPreferences = { memberId, diet, drinks ->
                            viewModel.updateMemberPreferences(memberId, diet, drinks)
                        },
                        onClaimGuestProfile = { memberId, name, email, upi ->
                            viewModel.claimGuestProfile(memberId, name, email, upi)
                        },
                        onOpenSettleUp = onOpenSettleUp,
                        onOpenNudge = onOpenNudge,
                        onOpenPersonalSplit = { friend -> onOpenManualSplit(null, friend.id, null) },
                        onOpenImportContacts = onOpenImportContacts,
                        openManualAddSignal = openManualAddFriendSignal,
                        modifier = modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddChooserSheet(
    onQuickSendCommand: (String) -> Unit,
    onSelectCreateGroup: () -> Unit,
    onSelectDiningStudio: () -> Unit,
    onSelectAiLens: () -> Unit,
    onSelectBatchImport: () -> Unit,
    onSelectVoice: () -> Unit,
    onSelectSpendCoach: () -> Unit,
    onSelectManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var quickCommand by remember { mutableStateOf("") }
    var quickHint by remember { mutableStateOf<String?>(null) }

    val submitQuickCommand: () -> Unit = {
        val cleaned = quickCommand.trim()
        if (cleaned.isBlank()) {
            quickHint = "Type a split like \"Tanmay owes me 500\" first."
        } else {
            quickHint = null
            keyboardController?.hide()
            focusManager.clearFocus()
            onQuickSendCommand(cleaned)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("quick_add_chooser_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quick Actions", style = MaterialTheme.typography.titleLarge)

            // Instant Quick Split Command Bar with responsive Send button inside the main menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickCommand,
                    onValueChange = {
                        quickCommand = it
                        if (quickHint != null) quickHint = null
                    },
                    placeholder = {
                        Text(
                            text = "Quick split: \"Tanmay owes me 500\"",
                            color = tokens.inkMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitQuickCommand() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.elevatedSurface,
                        unfocusedContainerColor = tokens.elevatedSurface,
                        focusedBorderColor = if (tokens.isDark) tokens.acidLime else tokens.obsidian,
                        unfocusedBorderColor = tokens.hairline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_menu_command_input")
                )
                SpringButton(
                    onClick = submitQuickCommand,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (quickCommand.isNotBlank()) tokens.acidLime else tokens.elevatedSurface,
                        contentColor = if (quickCommand.isNotBlank()) tokens.onAcidLime else tokens.inkMuted
                    ),
                    border = if (quickCommand.isNotBlank()) null else BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("quick_menu_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send quick split command",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            if (quickHint != null) {
                Text(
                    text = quickHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.warning
                )
            }

            QuickAddOptionCard(
                title = "Create New Group",
                subtitle = "1-tap trip, flatmates, or dinner group — add friends & contacts inline",
                icon = Icons.Rounded.GroupAdd,
                accentColor = tokens.positive,
                showAiBadge = false,
                onClick = onSelectCreateGroup,
                modifier = Modifier.testTag("quick_add_create_group")
            )

            QuickAddOptionCard(
                title = "Table & Bar Split (Veg / Non-Veg & Drinks)",
                subtitle = "Veg/Non-Veg food buckets, drinks by glass/peg & separate Food GST vs. Bar VAT",
                icon = Icons.Rounded.LocalBar,
                accentColor = tokens.positive,
                showAiBadge = false,
                onClick = onSelectDiningStudio,
                modifier = Modifier.testTag("quick_add_dining_studio")
            )

            QuickAddOptionCard(
                title = "AI Receipt Lens",
                subtitle = "Scan bill photo -> itemized split with auto-saved receipt & prorated tax",
                icon = Icons.Rounded.DocumentScanner,
                accentColor = tokens.aiAccent,
                showAiBadge = true,
                onClick = onSelectAiLens,
                modifier = Modifier.testTag("quick_add_ai_lens")
            )

            QuickAddOptionCard(
                title = "Batch Multi-Receipt Import",
                subtitle = "Drop up to 8 trip bills -> parallel on-device Mirage OCR & approval queue",
                icon = Icons.Rounded.DynamicFeed,
                accentColor = tokens.aiAccent,
                showAiBadge = true,
                onClick = onSelectBatchImport,
                modifier = Modifier.testTag("quick_add_batch_import")
            )

            QuickAddOptionCard(
                title = "Voice & Natural Language Split",
                subtitle = "Local Cactus Whistle transcription & instant ledger updates",
                icon = Icons.Rounded.Mic,
                accentColor = tokens.aiAccent,
                showAiBadge = true,
                onClick = onSelectVoice,
                modifier = Modifier.testTag("quick_add_voice")
            )

            QuickAddOptionCard(
                title = "On-Device Spend Coach",
                subtitle = "Analyze category burn, budget caps & recurring bills locally",
                icon = Icons.Rounded.AutoAwesome,
                accentColor = tokens.aiAccent,
                showAiBadge = true,
                onClick = onSelectSpendCoach,
                modifier = Modifier.testTag("quick_add_spend_coach")
            )

            QuickAddOptionCard(
                title = "Smart Manual Split",
                subtitle = "Multi-currency FX, recurring schedule, receipt photo & 5 split modes",
                icon = Icons.Rounded.EditNote,
                accentColor = tokens.positive,
                showAiBadge = false,
                onClick = onSelectManual,
                modifier = Modifier.testTag("quick_add_manual")
            )
        }
    }
}

@Composable
private fun FloatingFrostedNavDock(
    currentTab: SplitFlowTab,
    isQuickAddOpen: Boolean,
    onSelectTab: (SplitFlowTab) -> Unit,
    onQuickAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val plusRotation by animateFloatAsState(
        targetValue = if (isQuickAddOpen) 45f else 0f,
        animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY),
        label = "quick_add_rotation"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(999.dp),
        color = tokens.cardSurface.copy(alpha = if (tokens.isDark) 0.92f else 0.95f),
        border = BorderStroke(1.dp, tokens.hairline),
        shadowElevation = if (tokens.isDark) 0.dp else 12.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DockNavItem(
                tab = SplitFlowTab.HOME,
                selected = currentTab == SplitFlowTab.HOME,
                onClick = { onSelectTab(SplitFlowTab.HOME) },
                modifier = Modifier.weight(1f)
            )
            DockNavItem(
                tab = SplitFlowTab.GROUPS,
                selected = currentTab == SplitFlowTab.GROUPS,
                onClick = { onSelectTab(SplitFlowTab.GROUPS) },
                modifier = Modifier.weight(1f)
            )

            // Centered Circular Quick Add Button with spring press + morph rotation
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = tokens.dockAddIcon,
                shadowElevation = if (tokens.isDark) 0.dp else 4.dp,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .springClickable(
                        pressedScale = 0.88f,
                        preset = SpringPreset.BOUNCY,
                        onClick = onQuickAddClick
                    )
                    .testTag("quick_add_fab")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tokens.dockAddGradientStart, tokens.dockAddGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Quick add split",
                        tint = tokens.dockAddIcon,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = plusRotation }
                    )
                }
            }

            DockNavItem(
                tab = SplitFlowTab.ACTIVITY,
                selected = currentTab == SplitFlowTab.ACTIVITY,
                onClick = { onSelectTab(SplitFlowTab.ACTIVITY) },
                modifier = Modifier.weight(1f)
            )
            DockNavItem(
                tab = SplitFlowTab.PEOPLE,
                selected = currentTab == SplitFlowTab.PEOPLE,
                onClick = { onSelectTab(SplitFlowTab.PEOPLE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DockNavItem(
    tab: SplitFlowTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else tokens.inkMuted,
        animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
        label = "dock_ink_${tab.name}"
    )
    val pillBg by animateColorAsState(
        targetValue = if (selected) tokens.elevatedSurface else Color.Transparent,
        animationSpec = SpringPhysics.colorSpec(SpringPreset.DETENT),
        label = "dock_bg_${tab.name}"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1f,
        animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY),
        label = "dock_icon_scale_${tab.name}"
    )
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(pillBg)
            .springClickable(
                pressedScale = 0.92f,
                preset = SpringPreset.TACTILE_PRESS,
                onClick = onClick
            )
            .testTag("nav_tab_${tab.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickAddOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    showAiBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .tactileClickable(pressedScale = 0.975f, onClick = onClick),
        shape = shape,
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tokens.hairline),
        shadowElevation = if (tokens.isDark) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (showAiBadge) tokens.acidLime else tokens.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (showAiBadge) tokens.onAcidLime else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (showAiBadge) AiBadge("AI")
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
