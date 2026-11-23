package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.AppVisibilityTracker
import com.example.core.InAppVideoCompletionAlert
import com.example.core.LocalizationStrings
import com.example.core.MubaNotificationHelper
import com.example.core.configureAppCheck
import com.example.data.model.Creation
import com.example.data.model.UiState
import com.example.data.repository.UserRepository
import com.example.ui.components.InAppVideoAlertBanner
import com.example.ui.components.MubaLogoMark
import com.example.ui.components.OnboardingTutorialOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreationsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageGeneratorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoGeneratorScreen
import com.example.ui.theme.MubaAiTheme
import com.example.ui.theme.MubaPurple
import com.example.ui.viewmodel.BatchVideoViewModel
import com.example.ui.viewmodel.ImageGenViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.VideoGenViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val ROUTE_HOME = "home"
const val ROUTE_VIDEO_GEN = "video_gen"
const val ROUTE_IMAGE_GEN = "image_gen"
const val ROUTE_CREATIONS = "creations"
const val ROUTE_SETTINGS = "settings"

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private data class NotificationNavRequest(
    val route: String,
    val isBatchMode: Boolean,
    val token: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private var pendingNotificationNav by mutableStateOf<NotificationNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)
        MubaNotificationHelper.ensureChannels(this)
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val userRepo = remember(context) { UserRepository(context) }
            val localDarkMode by userRepo.localDarkMode.collectAsStateWithLifecycle(initialValue = true)
            val localLanguage by userRepo.localLanguage.collectAsStateWithLifecycle(initialValue = "en")
            val onboardingCompleted by userRepo.onboardingCompleted.collectAsStateWithLifecycle(initialValue = true)

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> AppVisibilityTracker.setAppForeground(true)
                        Lifecycle.Event.ON_STOP -> AppVisibilityTracker.setAppForeground(false)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MubaAiTheme(darkTheme = localDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MubaAppRoot(
                        userRepository = userRepo,
                        localDarkMode = localDarkMode,
                        localLanguage = localLanguage,
                        onboardingCompleted = onboardingCompleted,
                        pendingNotificationNav = pendingNotificationNav,
                        onNotificationNavHandled = { pendingNotificationNav = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val targetRoute = intent?.getStringExtra(MubaNotificationHelper.EXTRA_NAV_ROUTE) ?: return
        val isBatch = intent.getBooleanExtra(MubaNotificationHelper.EXTRA_IS_BATCH_MODE, false)
        pendingNotificationNav = NotificationNavRequest(
            route = targetRoute,
            isBatchMode = isBatch
        )
        intent.removeExtra(MubaNotificationHelper.EXTRA_NAV_ROUTE)
        intent.removeExtra(MubaNotificationHelper.EXTRA_IS_BATCH_MODE)
    }
}

@Composable
private fun MubaAppRoot(
    userRepository: UserRepository,
    localDarkMode: Boolean,
    localLanguage: String,
    onboardingCompleted: Boolean,
    pendingNotificationNav: NotificationNavRequest?,
    onNotificationNavHandled: () -> Unit
) {
    var authReady by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos {}
        currentUser = Firebase.auth.currentUser
        authReady = true
    }

    if (authReady) {
        DisposableEffect(Unit) {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentUser = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }
    }

    if (!authReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MubaLogoMark(size = 56.dp)
        }
    } else {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        } else {
            key(user.uid) {
                MubaAuthenticatedStudio(
                    firebaseUser = user,
                    userRepository = userRepository,
                    fallbackDarkMode = localDarkMode,
                    fallbackLanguage = localLanguage,
                    onboardingCompleted = onboardingCompleted,
                    pendingNotificationNav = pendingNotificationNav,
                    onNotificationNavHandled = onNotificationNavHandled,
                    onSignedOut = { currentUser = null }
                )
            }
        }
    }
}

@Composable
private fun MubaAuthenticatedStudio(
    firebaseUser: FirebaseUser,
    userRepository: UserRepository,
    fallbackDarkMode: Boolean,
    fallbackLanguage: String,
    onboardingCompleted: Boolean,
    pendingNotificationNav: NotificationNavRequest?,
    onNotificationNavHandled: () -> Unit,
    onSignedOut: () -> Unit
) {
    val uid = firebaseUser.uid
    val scope = rememberCoroutineScope()

    val mainViewModel: MainViewModel = viewModel(
        key = "main_$uid",
        factory = MainViewModel.provideFactory(uid)
    )
    val videoGenViewModel: VideoGenViewModel = viewModel(
        key = "vid_$uid",
        factory = VideoGenViewModel.provideFactory(uid)
    )
    val batchVideoViewModel: BatchVideoViewModel = viewModel(
        key = "batch_vid_$uid",
        factory = BatchVideoViewModel.provideFactory(uid)
    )
    val imageGenViewModel: ImageGenViewModel = viewModel(
        key = "img_$uid",
        factory = ImageGenViewModel.provideFactory(uid)
    )

    LaunchedEffect(uid) {
        mainViewModel.ensureProfile(firebaseUser)
    }

    val profileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val creationsState by mainViewModel.creationsState.collectAsStateWithLifecycle()

    val activeLanguage = (profileState as? UiState.Success)?.data?.preferredLanguage ?: fallbackLanguage
    val activeDarkMode = (profileState as? UiState.Success)?.data?.darkMode ?: fallbackDarkMode
    val strings = remember(activeLanguage) { LocalizationStrings.forLanguage(activeLanguage) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var selectedCreationForDetail by remember { mutableStateOf<Creation?>(null) }
    var showTutorial by remember { mutableStateOf(false) }
    var isVideoBatchMode by remember { mutableStateOf(false) }
    var activeInAppAlert by remember { mutableStateOf<InAppVideoCompletionAlert?>(null) }

    LaunchedEffect(currentDestination?.route) {
        AppVisibilityTracker.setActiveRoute(currentDestination?.route ?: ROUTE_HOME)
        if (currentDestination?.route == ROUTE_VIDEO_GEN) {
            activeInAppAlert = null
        }
    }

    LaunchedEffect(Unit) {
        MubaNotificationHelper.inAppAlerts.collect { alert ->
            activeInAppAlert = alert
        }
    }

    LaunchedEffect(activeInAppAlert?.id) {
        val alert = activeInAppAlert ?: return@LaunchedEffect
        delay(9000L)
        if (activeInAppAlert?.id == alert.id) {
            activeInAppAlert = null
        }
    }

    LaunchedEffect(onboardingCompleted) {
        if (!onboardingCompleted) {
            withFrameNanos {}
            delay(300L)
            showTutorial = true
        } else {
            showTutorial = false
        }
    }

    val navItems = remember(strings) {
        listOf(
            NavTabItem(ROUTE_HOME, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home),
            NavTabItem(ROUTE_VIDEO_GEN, strings.navVideo, Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation),
            NavTabItem(ROUTE_IMAGE_GEN, strings.navImage, Icons.Filled.Image, Icons.Outlined.Image),
            NavTabItem(ROUTE_CREATIONS, strings.navCreations, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
            NavTabItem(ROUTE_SETTINGS, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingNotificationNav?.token) {
        val req = pendingNotificationNav ?: return@LaunchedEffect
        isVideoBatchMode = req.isBatchMode
        activeInAppAlert = null
        navigateToTab(req.route)
        onNotificationNavHandled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MubaPurple,
                                    selectedTextColor = MubaPurple,
                                    indicatorColor = MubaPurple.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            MubaLogoMark(size = 34.dp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MubaPurple,
                                    selectedTextColor = MubaPurple,
                                    indicatorColor = MubaPurple.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("nav_rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            strings = strings,
                            userProfileState = profileState,
                            creationsState = creationsState,
                            onCreateVideoClick = {
                                isVideoBatchMode = false
                                navigateToTab(ROUTE_VIDEO_GEN)
                            },
                            onBatchVideoClick = {
                                isVideoBatchMode = true
                                navigateToTab(ROUTE_VIDEO_GEN)
                            },
                            onCreateImageClick = { navigateToTab(ROUTE_IMAGE_GEN) },
                            onSeeAllCreations = { navigateToTab(ROUTE_CREATIONS) },
                            onCreationClick = { creation ->
                                selectedCreationForDetail = creation
                                navigateToTab(ROUTE_CREATIONS)
                            },
                            onStartTutorial = { showTutorial = true }
                        )
                    }
                    composable(ROUTE_VIDEO_GEN) {
                        BackHandler { navigateToTab(ROUTE_HOME) }
                        VideoGeneratorScreen(
                            strings = strings,
                            viewModel = videoGenViewModel,
                            batchViewModel = batchVideoViewModel,
                            isBatchMode = isVideoBatchMode,
                            onBatchModeChange = { isVideoBatchMode = it }
                        )
                    }
                    composable(ROUTE_IMAGE_GEN) {
                        BackHandler { navigateToTab(ROUTE_HOME) }
                        ImageGeneratorScreen(
                            strings = strings,
                            viewModel = imageGenViewModel,
                            onAnimateIntoVideo = { bitmap, prompt ->
                                videoGenViewModel.setSourceBitmapDirect(bitmap, prompt)
                                navigateToTab(ROUTE_VIDEO_GEN)
                            }
                        )
                    }
                    composable(ROUTE_CREATIONS) {
                        BackHandler { navigateToTab(ROUTE_HOME) }
                        CreationsScreen(
                            strings = strings,
                            creationsState = creationsState,
                            selectedCreation = selectedCreationForDetail,
                            onSelectCreation = { selectedCreationForDetail = it },
                            onRenameCreation = mainViewModel::renameCreation,
                            onDeleteCreation = mainViewModel::deleteCreation,
                            videoGenViewModel = videoGenViewModel
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        BackHandler { navigateToTab(ROUTE_HOME) }
                        SettingsScreen(
                            strings = strings,
                            userProfileState = profileState,
                            currentLanguageCode = activeLanguage,
                            isDarkMode = activeDarkMode,
                            onLanguageChange = { newLang ->
                                mainViewModel.updatePreferences(newLang, activeDarkMode)
                            },
                            onDarkModeChange = { newDark ->
                                mainViewModel.updatePreferences(activeLanguage, newDark)
                            },
                            onDisplayNameChange = mainViewModel::updateDisplayName,
                            onReplayTutorial = { showTutorial = true },
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = activeInAppAlert != null && !showTutorial,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 560.dp)
        ) {
            val currentAlert = activeInAppAlert
            if (currentAlert != null) {
                InAppVideoAlertBanner(
                    alert = currentAlert,
                    onViewClick = {
                        isVideoBatchMode = currentAlert.isBatch
                        activeInAppAlert = null
                        navigateToTab(ROUTE_VIDEO_GEN)
                    },
                    onDismiss = { activeInAppAlert = null }
                )
            }
        }

        AnimatedVisibility(
            visible = showTutorial,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OnboardingTutorialOverlay(
                onDismiss = {
                    showTutorial = false
                    scope.launch { userRepository.setOnboardingCompleted(true) }
                },
                onJumpToStudio = { route ->
                    showTutorial = false
                    scope.launch { userRepository.setOnboardingCompleted(true) }
                    navigateToTab(route)
                }
            )
        }
    }
}
