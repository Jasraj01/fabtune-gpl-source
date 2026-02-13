package com.metrolist.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.lifecycle.coroutineScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DefaultOpenTabKey
import com.metrolist.music.constants.DisableScreenshotKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.constants.UseNewMiniPlayerDesignKey
import com.metrolist.music.constants.NavigationBarAnimationSpec
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.constants.SlimNavBarHeight
import com.metrolist.music.constants.SlimNavBarKey
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AccountSettingsDialog
import com.metrolist.music.ui.component.BottomSheetMenu
import com.metrolist.music.ui.component.BottomSheetPage
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.component.AppNavigationBar
import com.metrolist.music.ui.component.AppNavigationRail
import com.metrolist.music.ui.component.shimmer.ShimmerTheme
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.player.BottomSheetPlayer
import com.metrolist.music.ui.reviewbox.ReviewDataStore
import com.metrolist.music.ui.reviewbox.triggerInAppReviewIfEligible
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.LocalIsSubscribed
import com.metrolist.music.ui.screens.navigationBuilder
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.screens.settings.NavigationTab
import com.metrolist.music.ui.theme.ColorSaver
import com.metrolist.music.ui.theme.DefaultThemeColor
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.ui.theme.extractThemeColor
import com.metrolist.music.ui.utils.appBarScrollBehavior
import com.metrolist.music.ui.utils.resetHeightOffset
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.Updater
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.ui.screens.PaywallViewModel
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.setAppLocale
import com.metrolist.music.viewmodels.HomeViewModel
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.metrolist.music.ui.component.PopupScreen
import kotlin.jvm.java
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_SEARCH = "com.fabtune.music.player.action.SEARCH"
        private const val ACTION_EXPLORE = "com.fabtune.music.player.action.EXPLORE"
        private const val ACTION_LIBRARY = "com.fabtune.music.player.action.LIBRARY"
        private const val DEFERRED_STARTUP_DELAY_MS = 1200L
        private const val DEFERRED_SUBSCRIPTION_FETCH_DELAY_MS = 300L

        // ADVANCED FIX – Bluetooth/background resilience visibility flag
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private lateinit var reviewDataStore: ReviewDataStore

    private lateinit var consentInformation: ConsentInformation

    private val isMobileAdsInitialized = AtomicBoolean(false)


    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                isBound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    private var isBound = false
    private var deferredStartupJob: Job? = null

    private val deferredServiceBindRunnable = Runnable {
        if (!isInForeground || isBound || isFinishing || isDestroyed) return@Runnable
        // Bind playback service one frame later to reduce warm-start main-thread pressure.
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    private val deferredNotificationPermissionRunnable = Runnable {
        if (!isInForeground || isFinishing || isDestroyed) return@Runnable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@Runnable
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
        }
    }

    override fun onStart() {
        super.onStart()
        // ADVANCED FIX – Bluetooth/background resilience: track foreground state
        isInForeground = true
        window.decorView.post(deferredNotificationPermissionRunnable)
        window.decorView.post(deferredServiceBindRunnable)
    }

    override fun onStop() {
        // ADVANCED FIX – Bluetooth/background resilience: track background state
        isInForeground = false
        window.decorView.removeCallbacks(deferredNotificationPermissionRunnable)
        window.decorView.removeCallbacks(deferredServiceBindRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }

        val isCalmState = !isChangingConfigurations
        lifecycleScope.launch {
            triggerInAppReviewIfEligible(
                activity = this@MainActivity,
                reviewDataStore = reviewDataStore,
                isAppCalm = isCalmState,
            )
        }

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (dataStore.get(StopMusicOnTaskClearKey, false) && isFinishing) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {}

            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale = dataStore[AppLanguageKey]
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        reviewDataStore = ReviewDataStore(applicationContext)

        lifecycleScope.launch {
            reviewDataStore.ensureFirstOpenTimestampInitialized()
        }
        scheduleDeferredStartupWork()

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            val subscriptionState = remember { mutableStateOf(false) }
            val composeScope = rememberCoroutineScope()
            DisposableEffect(Unit) {
                val listener = UpdatedCustomerInfoListener { customerInfo ->
                    subscriptionState.value =
                        customerInfo.entitlements.active.containsKey("premium")
                }
                Purchases.sharedInstance.updatedCustomerInfoListener = listener
                val initialCustomerInfoJob = composeScope.launch {
                    delay(DEFERRED_SUBSCRIPTION_FETCH_DELAY_MS)
                    Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            subscriptionState.value =
                                customerInfo.entitlements.active.containsKey("premium")
                        }

                        override fun onError(error: PurchasesError) {
                            subscriptionState.value = false
                        }
                    })
                }
                onDispose {
                    initialCustomerInfoJob.cancel()
                    if (Purchases.sharedInstance.updatedCustomerInfoListener === listener) {
                        Purchases.sharedInstance.updatedCustomerInfoListener = null
                    }
                }
            }
            val isSubscribed = subscriptionState.value

            LaunchedEffect(Unit) {
                delay(DEFERRED_STARTUP_DELAY_MS)
                val fetchedVersionName = withContext(Dispatchers.IO) {
                    if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                        Updater.getLatestVersionName().getOrNull()
                    } else {
                        null
                    }
                }
                fetchedVersionName?.let { latestVersionName = it }
            }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.ON)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(playerConnection, enableDynamicTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song?.thumbnailUrl != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val result = imageLoader.execute(
                                    ImageRequest.Builder(this@MainActivity)
                                        .data(song.thumbnailUrl)
                                        .allowHardware(false)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(false)
                                        .build()
                                )
                                themeColor = result.image?.toBitmap()?.extractThemeColor()
                                    ?: DefaultThemeColor
                            } catch (e: Exception) {
                                // Fallback to default on error
                                themeColor = DefaultThemeColor
                            }
                        }
                    } else {
                        themeColor = DefaultThemeColor
                    }
                }
            }

            MetrolistTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                            )
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val configuration = LocalConfiguration.current
                    val cutoutInsets = WindowInsets.displayCutout
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp =
                        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    val navController = rememberNavController()

                    val paywallViewModel: PaywallViewModel = hiltViewModel()

                    val paywallUiState by paywallViewModel.uiState.collectAsState()
                    LaunchedEffect(isSubscribed) {
                        paywallViewModel.onSubscriptionStateChanged(isSubscribed)
                    }

                    DisposableEffect(navController, paywallUiState.showPaywall) {
                        val listener =
                            NavController.OnDestinationChangedListener { _, destination, _ ->
                                // If user was on "premium" and is now going somewhere else
                                // but showPaywall is still true, let's reset it:
                                if (destination.route != "premium" && paywallUiState.showPaywall) {
                                    paywallViewModel.markPaywallShown()
                                }
                            }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose {
                            navController.removeOnDestinationChangedListener(listener)
                        }
                    }

                    val shouldShowPaywall by remember(
                        paywallUiState.showPaywall,
                        paywallUiState.isSubscribed
                    ) {
                        derivedStateOf { paywallUiState.showPaywall && !paywallUiState.isSubscribed }
                    }
                    LaunchedEffect(Unit) {
                        snapshotFlow { shouldShowPaywall }
                            .distinctUntilChanged()
                            .collect { show ->
                                Timber.d("HomeScreen: shouldShowPaywall=$show")
                                if (show) {
                                    val currentRoute =
                                        navController.currentBackStackEntry?.destination?.route
                                    if (currentRoute != "premium") {
                                        navController.navigate("premium")
                                    }
                                }
                            }
                    }

                    var accountImageUrl by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        dataStore.data
                            .map { it[InnerTubeCookieKey] }
                            .distinctUntilChanged()
                            .collectLatest { cookie ->
                                accountImageUrl = if (cookie.isNullOrBlank()) {
                                    null
                                } else {
                                    withContext(Dispatchers.IO) {
                                        if (YouTube.cookie != cookie) {
                                            YouTube.cookie = cookie
                                        }
                                        val accountInfo = YouTube.accountInfo()
                                        accountInfo.onFailure { reportException(it) }
                                        accountInfo.getOrNull()?.thumbnailUrl
                                    }
                                }
                            }
                    }
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                    val navigationItems = remember { Screens.MainScreens }
                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (useNewMiniPlayerDesign) = rememberPreference(
                        UseNewMiniPlayerDesignKey,
                        defaultValue = true
                    )
                    val defaultOpenTab by rememberEnumPreference(
                        DefaultOpenTabKey,
                        defaultValue = NavigationTab.HOME
                    )
                    val tabOpenedFromShortcut = remember {
                        when (intent?.action) {
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            ACTION_EXPLORE -> NavigationTab.EXPLORE
//                                ACTION_SEARCH -> NavigationTab.SEARCH
                            else -> null
                        }
                    }

                    val topLevelScreens = remember {
                        listOf(
                            Screens.Home.route,
                            Screens.Explore.route,
//                            Screens.Search.route,
                            Screens.Library.route,
                            "settings",
                        )
                    }

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

//                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

//                    val onSearch: (String) -> Unit = remember {
//                        { searchQuery ->
//                            if (searchQuery.isNotEmpty()) {
//                                onActiveChange(false)
//                                navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
//
//                                if (dataStore[PauseSearchHistoryKey] != true) {
//                                    lifecycleScope.launch(Dispatchers.IO) {
//                                        database.query {
//                                            insert(SearchHistory(query = searchQuery))
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    // Use derivedStateOf to avoid unnecessary recompositions
                    val currentRoute by remember {
                        derivedStateOf { navBackStackEntry?.destination?.route }
                    }

                    val inSearchScreen by remember {
                        derivedStateOf { currentRoute?.startsWith("search/") == true }
                    }
                    val navigationItemRoutes = remember(navigationItems) {
                        navigationItems.map { it.route }.toSet()
                    }

                    val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes) {
                        currentRoute == null ||
                                navigationItemRoutes.contains(currentRoute) ||
                                currentRoute?.startsWith("search/") == true
                    }

                    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

                    val showRail = isLandscape && !inSearchScreen

                    val navPadding = if (shouldShowNavigationBar && !showRail) {
                        if (slimNav) SlimNavBarHeight else NavigationBarHeight
                    } else {
                        0.dp
                    }

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = "navBarHeight",
                    )

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = bottomInset +
                                (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                                (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                                MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                    // made changes here
                    val playerAwareWindowInsets = remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                        navBackStackEntry?.destination?.route,
                    ) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar && !showRail) {
                            bottom += NavigationBarHeight
                        }
                        if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight

                        // Add extra top padding for search bar on Explore screen // changes here main
                        val topPadding =
                            if (navBackStackEntry?.destination?.route == Screens.Explore.route) {
                                AppBarHeight + 64.dp  // Title + search bar height
                            } else {
                                AppBarHeight
                            }

                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = topPadding, bottom = bottom))
                    }

                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

//                    val searchBarScrollBehavior =
//                        appBarScrollBehavior(
//                            canScroll = {
//                                !inSearchScreen &&
//                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
//                            },
//                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                !inSearchScreen &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    LaunchedEffect(navBackStackEntry) {
                        if (inSearchScreen) {
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")
                            // Defensive decode guard to prevent malformed deep-link crashes.
                            val searchQuery = withContext(Dispatchers.IO) {
                                rawQuery?.let { query ->
                                    if (query.contains("%")) {
                                        query
                                    } else {
                                        runCatching { URLDecoder.decode(query, "UTF-8") }.getOrDefault(query)
                                    }
                                }.orEmpty()
                            }
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length)
                                )
                            )
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }

                        // Reset scroll behavior for main navigation items
                        if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            if (navigationItems.fastAny { it.route == previousTab }) {
                                topAppBarScrollBehavior.state.resetHeightOffset()
                            }
                        }

//                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()

                        // Track previous tab for animations
                        navController.currentBackStackEntry?.destination?.route?.let {
                            setPreviousTab(it)
                        }
                    }

//                    LaunchedEffect(active) {
//                        if (active) {
//                            searchBarScrollBehavior.state.resetHeightOffset()
//                            topAppBarScrollBehavior.state.resetHeightOffset()
//                            searchBarFocusRequester.requestFocus()
//                        }
//                    }

                    LaunchedEffect(playerConnection) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState) {
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                        mediaItem != null &&
                                        playerBottomSheetState.isDismissed
                                    ) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar = navBackStackEntry?.destination?.route in topLevelScreens &&
                                navBackStackEntry?.destination?.route != "settings"
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    // Persisted tap tracking for tab-centric double-tap detection.
                    // Primitive state avoids boxing and per-tap allocations.
                    var lastNavTapUptimeMs by rememberSaveable { mutableLongStateOf(0L) }
                    var lastNavTapType by rememberSaveable { mutableIntStateOf(0) } // 0 = none, 1 = explore
                    val doubleTapTimeoutMs = remember { ViewConfiguration.getDoubleTapTimeout().toLong() }

                    val onMainNavItemClick: (Screens, Boolean) -> Unit = remember(
                        navController,
                        coroutineScope,
                        topAppBarScrollBehavior,
                        playerBottomSheetState,
                        doubleTapTimeoutMs
                    ) {
                        { screen: Screens, isSelected: Boolean ->
                            if (playerBottomSheetState.isExpanded) {
                                playerBottomSheetState.collapseSoft()
                            }

                            val nowUptimeMs = SystemClock.uptimeMillis()
                            val isExplore = screen.route == Screens.Explore.route

                            if (isExplore) {
                                val elapsed = nowUptimeMs - lastNavTapUptimeMs
                                val isDoubleTapOnExplore =
                                    lastNavTapType == 1 &&
                                            lastNavTapUptimeMs > 0L &&
                                            elapsed >= 0L &&
                                            elapsed <= doubleTapTimeoutMs

                                if (isDoubleTapOnExplore) {
                                    // Two consecutive taps on Explore within system timeout: open Search.
                                    // This is tab-centric and does not depend on current destination state.
                                    lastNavTapType = 0
                                    lastNavTapUptimeMs = 0L
                                    navController.navigate("search") {
                                        launchSingleTop = true
                                    }
                                } else {
                                    // Arm Explore for possible second tap.
                                    lastNavTapType = 1
                                    lastNavTapUptimeMs = nowUptimeMs

                                    // Single tap behavior: only navigate when Explore is not currently selected.
                                    if (!isSelected) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            } else {
                                // Non-Explore tap breaks Explore double-tap sequence.
                                lastNavTapType = 0
                                lastNavTapUptimeMs = 0L

                                if (isSelected) {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "scrollToTop",
                                        true
                                    )
                                    coroutineScope.launch {
                                        topAppBarScrollBehavior.state.resetHeightOffset()
                                    }
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }

                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            pendingIntent?.let { pending ->
                                handleDeepLinkIntent(pending, navController)
                            }
                            pendingIntent = null
                        } else {
                            handleDeepLinkIntent(intent, navController)
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            handleDeepLinkIntent(intent, navController)
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    // changes here made
                    val currentTitleRes = remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.app_name
                            Screens.Explore.route -> R.string.search
//                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                    @Composable
                    fun currentRoute(navController: NavHostController): String? {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        return navBackStackEntry?.destination?.route
                    }


                    var showAccountDialog by remember { mutableStateOf(false) }
                    val accountSettingsHomeViewModel: HomeViewModel? =
                        if (showAccountDialog) hiltViewModel() else null

                    val baseBg =
                        if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(
                            MaterialTheme.colorScheme.surface
                        ),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalIsPlayerExpanded provides (!playerBottomSheetState.isCollapsed && !playerBottomSheetState.isDismissed),
                        LocalIsSubscribed provides isSubscribed,
                    ) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                AnimatedVisibility(
                                    visible = shouldShowTopBar,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                                    )

                                    val route = currentRoute(navController)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row {
                                            // Regular TopAppBar with title and icons // changes here
                                            TopAppBar(
                                                title = {
                                                    val useCompactGetPlusCta =
                                                        !isSubscribed && configuration.screenWidthDp <= 360
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = currentTitleRes?.let {
                                                                stringResource(it)
                                                            } ?: "",
                                                            style = MaterialTheme.typography.titleLarge,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = if (!isSubscribed) Modifier.weight(
                                                                1f,
                                                                fill = false
                                                            ) else Modifier,
                                                        )
                                                        if (!isSubscribed) {
                                                            Spacer(
                                                                modifier = Modifier.width(
                                                                    if (useCompactGetPlusCta) 4.dp else 8.dp
                                                                )
                                                            )
                                                            GetPlusCtaButton(
                                                                onClick = {
                                                                    navController.navigate("premium") {
                                                                        launchSingleTop = true
                                                                    }
                                                                },
                                                                compact = useCompactGetPlusCta
                                                            )
                                                        }
                                                    }
                                                },
                                                actions = {
                                                    if (route == Screens.Library.route) {
                                                        IconButton(onClick = {
                                                            navController.navigate(
                                                                "stats"
                                                            )
                                                        }) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.stats),
                                                                contentDescription = stringResource(
                                                                    R.string.stats
                                                                )
                                                            )
                                                        }
                                                    }

                                                    if (route != "explore") {
                                                        IconButton(onClick = {
                                                            navController.navigate(
                                                                "history"
                                                            )
                                                        }) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.history),
                                                                contentDescription = stringResource(
                                                                    R.string.history
                                                                )
                                                            )
                                                        }

                                                        IconButton(onClick = {
                                                            showAccountDialog = true
                                                        }) {
                                                            if (accountImageUrl != null) {
                                                                AsyncImage(
                                                                    model = accountImageUrl,
                                                                    contentDescription = stringResource(
                                                                        R.string.account
                                                                    ),
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .clip(CircleShape)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.settings_new),
                                                                    contentDescription = stringResource(
                                                                        R.string.account
                                                                    ),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        // Icons for Explore screen
                                                        IconButton(onClick = {
                                                            navController.navigate(
                                                                "history"
                                                            )
                                                        }) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.history),
                                                                contentDescription = stringResource(
                                                                    R.string.history
                                                                )
                                                            )
                                                        }

                                                        IconButton(onClick = {
                                                            showAccountDialog = true
                                                        }) {
                                                            if (accountImageUrl != null) {
                                                                AsyncImage(
                                                                    model = accountImageUrl,
                                                                    contentDescription = stringResource(
                                                                        R.string.account
                                                                    ),
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .clip(CircleShape)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.settings_new),
                                                                    contentDescription = stringResource(
                                                                        R.string.account
                                                                    ),
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                scrollBehavior = topAppBarScrollBehavior,
                                                colors = TopAppBarDefaults.topAppBarColors( // changes here
                                                    containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                                                    scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.windowInsetsPadding(
                                                    if (showRail) {
                                                        WindowInsets(left = NavigationBarHeight)
                                                            .add(cutoutInsets.only(WindowInsetsSides.Start))
                                                    } else {
                                                        cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                                    }
                                                )
                                            )
                                        }

                                        // Search bar below title - ONLY for Explore screen // made changes from here to
                                        if (route == Screens.Explore.route) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Add spacer for NavigationRail when in landscape
                                                if (showRail) {
                                                    Spacer(
                                                        modifier = Modifier.width(
                                                            NavigationBarHeight
                                                        )
                                                    )
                                                }

                                                // Search bar with proper padding
                                                Surface(
                                                    onClick = { navController.navigate("search") },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(
                                                            horizontal = 12.dp,
                                                            vertical = 8.dp
                                                        )
                                                        .height(48.dp),
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = if (pureBlack) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                                                    tonalElevation = 2.dp
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 16.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.search),
                                                            contentDescription = null,
                                                            tint = if (pureBlack) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            text = stringResource(R.string.search),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = if (pureBlack) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        } // to here
                                    }
                                }
                            },
                            bottomBar = {
                                // Pre-calculate values for graphicsLayer to avoid reading state during composition
                                val navBarTotalHeight = bottomInset + NavigationBarHeight

                                if (!showRail && currentRoute != "wrapped") {
                                    Box {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack
                                        )

                                        AppNavigationBar(
                                            navigationItems = navigationItems,
                                            currentRoute = currentRoute,
                                            onItemClick = onMainNavItemClick,
                                            pureBlack = pureBlack,
                                            slimNav = slimNav,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInset + navPadding)
                                                // Use graphicsLayer instead of offset to avoid recomposition
                                                // graphicsLayer runs during draw phase, not composition phase
                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    translationY = if (navBarHeightPx == 0f) {
                                                        totalHeightPx
                                                    } else {
                                                        // Read progress only during draw phase
                                                        val progress =
                                                            playerBottomSheetState.progress.coerceIn(
                                                                0f,
                                                                1f
                                                            )
                                                        val slideOffset = totalHeightPx * progress
                                                        val hideOffset =
                                                            totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                        slideOffset + hideOffset
                                                    }
                                                }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInsetDp)
                                                // Use graphicsLayer for background color changes
                                                .graphicsLayer {
                                                    val progress = playerBottomSheetState.progress
                                                    alpha =
                                                        if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                                }
                                                .background(baseBg)
                                        )
                                    }
                                } else {
                                    if (currentRoute != "wrapped") {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                            // Use graphicsLayer for background color changes
                                            .graphicsLayer {
                                                val progress = playerBottomSheetState.progress
                                                alpha =
                                                    if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                            }
                                            .background(baseBg)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                if (showRail && currentRoute != "wrapped") {
                                    AppNavigationRail(
                                        navigationItems = navigationItems,
                                        currentRoute = currentRoute,
                                        onItemClick = onMainNavItemClick,
                                        pureBlack = pureBlack
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    // NavHost with animations (Material 3 Expressive style)
                                    NavHost(
                                        navController = navController,
                                        startDestination = when (tabOpenedFromShortcut
                                            ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.EXPLORE -> Screens.Explore
                                            NavigationTab.LIBRARY -> Screens.Library
                                            else -> Screens.Home
                                        }.route,
                                        // Enter Transition - smoother with smaller offset and longer duration
                                        enterTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }
                                            val previousRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }

                                            if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                                                slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                            else
                                                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        },
                                        // Exit Transition - smoother with smaller offset and longer duration
                                        exitTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }
                                            val targetRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }

                                            if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                            else
                                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        },
                                        // Pop Enter Transition - smoother with smaller offset and longer duration
                                        popEnterTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }
                                            val previousRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }

                                            if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                                slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                            else
                                                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                        },
                                        // Pop Exit Transition - smoother with smaller offset and longer duration
                                        popExitTransition = {
                                            val currentRouteIndex = navigationItems.indexOfFirst {
                                                it.route == initialState.destination.route
                                            }
                                            val targetRouteIndex = navigationItems.indexOfFirst {
                                                it.route == targetState.destination.route
                                            }

                                            if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                            else
                                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                        },
                                        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                    ) {
                                        navigationBuilder(
                                            navController = navController,
                                            scrollBehavior = topAppBarScrollBehavior,
                                            latestVersionName = latestVersionName,
                                            activity = this@MainActivity,
                                            snackbarHostState = snackbarHostState
                                        )
                                    }
                                }
                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        if (showAccountDialog) {
                            AccountSettingsDialog(
                                navController = navController,
                                onDismiss = {
                                    showAccountDialog = false
                                    accountSettingsHomeViewModel?.refresh()
                                },
//                                latestVersionName = latestVersionName
                            )
                        }

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PopupScreen() // to here
        }
    }

    private fun scheduleDeferredStartupWork() {
        if (deferredStartupJob != null) return
        deferredStartupJob = lifecycleScope.launch {
            delay(DEFERRED_STARTUP_DELAY_MS)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch

            withContext(Dispatchers.Default) {
                runCatching {
                    FirebaseApp.initializeApp(this@MainActivity)
                    Firebase.analytics
                }.onFailure { reportException(it) }
            }

            initializeConsentAndAdMob()
        }
    }


    private fun initializeConsentAndAdMob() {

        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        // ====================================================================
        // 🚨 FOR TESTING ONLY - REMOVE BEFORE PRODUCTION! 🚨
        // ====================================================================
        // Uncomment ONLY during development to test consent flow
        // MUST be commented out before production release!
        /*
        val debugSettings = ConsentDebugSettings.Builder(this)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .addTestDeviceHashedId("YOUR_TEST_DEVICE_ID")  // Get from logcat
            .build()
        paramsBuilder.setConsentDebugSettings(debugSettings)
        Log.d(TAG, "⚠️ TEST MODE: Debug geography enabled")
        */
        // ====================================================================

        val params = paramsBuilder.build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            // SUCCESS
            {

                // Show consent form if required
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Timber.e("UMP Error: ${formError.errorCode}, ${formError.message}")
                    }

                    // Initialize AdMob after consent handling
                    initializeMobileAdsSdk()
                }
            },
            // ERROR
            { requestConsentError ->
                Timber.e("UMP Request Error: ${requestConsentError.errorCode}, ${requestConsentError.message}")

                // Still initialize AdMob (will use restricted mode)
                initializeMobileAdsSdk()
            }
        )

        // Early initialization if already consented
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk()
        }
    }


    /**
     * Initialize Mobile Ads SDK - called only once via AtomicBoolean
     *
     * Key fixes:
     * 1. Thread-safe single initialization
     * 2. No hardcoded test device IDs (commented out for testing)
     * 3. Complete RequestConfiguration with all compliance settings
     * 4. Runs on background thread as recommended by Google
     */
    private fun initializeMobileAdsSdk() {
        // Check if already initialized (thread-safe)
        if (isMobileAdsInitialized.getAndSet(true)) {
            Timber.d("AdMob already initialized - skipping")
            return
        }

        Timber.tag(TAG).d("Initializing Mobile Ads SDK...")

        // Run on background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Initialize SDK
                MobileAds.initialize(this@MainActivity) { status ->
                    Timber.d("AdMob initialized")

                    // Log adapter status
                    status.adapterStatusMap.forEach { (adapter, adapterStatus) ->
                        Timber.d("  📊 $adapter: ${adapterStatus.initializationState}")
                    }
                }

                // ============================================================
                // REQUEST CONFIGURATION
                // ============================================================
                val requestConfigBuilder = RequestConfiguration.Builder()

                // ============================================================
                // 🚨 TESTING ONLY - REMOVE BEFORE PRODUCTION! 🚨
                // ============================================================
                // Uncomment ONLY for testing during development
                // Get your device ID from logcat first time you run
                // MUST comment out before production!

                val testDeviceIds = listOf(
                    "D4529930AAA10B74780E7F9DCE262852"  // Replace with your device ID
                )
                requestConfigBuilder.setTestDeviceIds(testDeviceIds)
                Timber.d("TEST MODE: Using test device IDs")

                // ============================================================

                // PRODUCTION SETTINGS (keep these)
                requestConfigBuilder
                    // Set to FALSE if NOT for children under 13
                    .setTagForChildDirectedTreatment(
                        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                    )
                    // Set to FALSE if users NOT under age of consent (typically 16 in EEA)
                    .setTagForUnderAgeOfConsent(
                        RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
                    )
                    // Set appropriate content rating for your app
                    // G = General (safest), PG, T, or MA
                    .setMaxAdContentRating(
                        RequestConfiguration.MAX_AD_CONTENT_RATING_G
                    )

                MobileAds.setRequestConfiguration(requestConfigBuilder.build())

            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    // ============================================================================
    // PRIVACY OPTIONS (for settings screen)
    // ============================================================================

    /**
     * Show privacy options form - call from settings screen
     * Allows users to change their consent preferences
     *
     * Usage in settings:
     * ```
     * val activity = LocalContext.current as? MainActivity
     * Button(onClick = { activity?.showPrivacyOptionsForm() }) {
     *     Text("Privacy Settings")
     * }
     * ```
     */
    fun showPrivacyOptionsForm() {
        UserMessagingPlatform.showPrivacyOptionsForm(this) { formError ->
            if (formError != null) {
                Timber.tag(TAG).e("Privacy form error: ${formError.message}")
            }
        }
    }



    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                withContext(Dispatchers.Main) {
                                    navController.navigate("album/$browseId")
                                }
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                }
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(videoId), playlistId).onSuccess { queue ->
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                        queue.firstOrNull()?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(null, playlistId).onSuccess { queue ->
                            val firstItem = queue.firstOrNull()
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                        firstItem?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

}

@Composable
private fun GetPlusCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val chipHeight = if (compact) 29.dp else 31.5.dp
    val horizontalPadding = if (compact) 8.dp else 12.dp
    val textSize = if (compact) 10.8.sp else 12.8.sp
    val separator = if (compact) "" else " "
    val chipShape = RoundedCornerShape(5.dp)
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = chipShape,
            color = Color(0xFF2A292C).copy(alpha = 0.54f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .height(chipHeight)
                .border(
                    width = 0.6.dp,
                    color = Color.White.copy(alpha = 0.07f),
                    shape = chipShape
                )
                .graphicsLayer(alpha = 0.98f, clip = true)
        ) {
            Box(
                modifier = Modifier
                    .height(chipHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.045f),
                                Color.Transparent,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = horizontalPadding)
                ) {
                    val baseLabel = stringResource(R.string.get_plus_chip_label_text)
                    val plusLabel = stringResource(R.string.get_plus_chip_plus_symbol)
                    val ctaLabel = remember(baseLabel, plusLabel, separator) {
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = Color.White.copy(alpha = 0.96f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) { append(baseLabel) }
                            append(separator)
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFFFB300),
                                    fontWeight = FontWeight.Bold
                                )
                            ) { append(plusLabel) }
                        }
                    }
                    Text(
                        text = ctaLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = textSize,
                            lineHeight = textSize,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalIsPlayerExpanded = compositionLocalOf { false }
