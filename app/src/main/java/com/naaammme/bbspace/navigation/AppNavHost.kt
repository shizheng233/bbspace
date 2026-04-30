package com.naaammme.bbspace.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.buildNavTransitions
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.auth.navigation.ACCOUNT_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.SMS_LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.accountScreen
import com.naaammme.bbspace.feature.auth.navigation.loginScreen
import com.naaammme.bbspace.feature.auth.navigation.smsLoginScreen
import com.naaammme.bbspace.feature.bbspace.navigation.bbSpaceScreen
import com.naaammme.bbspace.feature.bbspace.navigation.navigateToBbSpace
import com.naaammme.bbspace.feature.download.navigation.downloadScreen
import com.naaammme.bbspace.feature.download.navigation.navigateToDownload
import com.naaammme.bbspace.feature.history.navigation.historyScreen
import com.naaammme.bbspace.feature.history.navigation.navigateToHistory
import com.naaammme.bbspace.feature.home.HomeScreen
import com.naaammme.bbspace.feature.live.navigation.liveScreen
import com.naaammme.bbspace.feature.live.navigation.navigateToLive
import com.naaammme.bbspace.feature.search.navigation.navigateToSearch
import com.naaammme.bbspace.feature.search.navigation.searchScreen
import com.naaammme.bbspace.feature.space.navigation.navigateToSpace
import com.naaammme.bbspace.feature.space.navigation.spaceScreen
import com.naaammme.bbspace.feature.settings.navigation.SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.settingsScreen
import com.naaammme.bbspace.feature.download.DownloadViewModel
import com.naaammme.bbspace.feature.user.UserScreen
import com.naaammme.bbspace.playback.InAppMiniPlayer
import com.naaammme.bbspace.playback.PlaybackHostViewModel
import com.naaammme.bbspace.feature.video.navigation.navigateToVideo
import com.naaammme.bbspace.feature.video.navigation.videoScreen

private const val MAIN_ROUTE = "main"

@Composable
fun AppNavHost(themeConfig: ThemeConfig = ThemeConfig()) {
    val rootNavController = rememberNavController()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val playbackHostViewModel: PlaybackHostViewModel = hiltViewModel()
    val player by playbackHostViewModel.player.collectAsStateWithLifecycle()
    val target by playbackHostViewModel.currentTarget.collectAsStateWithLifecycle()
    val sessionState by playbackHostViewModel.sessionState.collectAsStateWithLifecycle()
    val pageMeta by playbackHostViewModel.pageMeta.collectAsStateWithLifecycle()
    val miniPlayerEnabled by playbackHostViewModel.miniPlayerEnabled.collectAsStateWithLifecycle()
    val navBackStackEntry by rootNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val isPlaybackRoute = currentRoute.startsWith("video/") || currentRoute.startsWith("live/")
    val exitPlayback: () -> Unit = {
        val targetRoute = rootNavController.previousBackStackEntry?.destination?.route.orEmpty()
        val shouldMinimize = miniPlayerEnabled &&
                targetRoute.isNotEmpty() &&
                !targetRoute.startsWith("video/") &&
                !targetRoute.startsWith("live/")
        if (shouldMinimize) {
            playbackHostViewModel.minimize()
        } else {
            playbackHostViewModel.close()
        }
        rootNavController.popBackStack()
        Unit
    }
    val openVideo: (VideoRoute) -> Unit = { route ->
        playbackHostViewModel.expand()
        rootNavController.navigateToVideo(route)
    }
    val openLive: (LiveRoute) -> Unit = { route ->
        playbackHostViewModel.expand()
        rootNavController.navigateToLive(route)
    }
    val transitions = remember(themeConfig.transitionStyle, themeConfig.animationSpeed) {
        buildNavTransitions<NavBackStackEntry>(
            themeConfig.transitionStyle,
            themeConfig.animationSpeed
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = rootNavController,
            startDestination = MAIN_ROUTE,
            enterTransition = { transitions.enter(this) },
            exitTransition = { transitions.exit(this) },
            popEnterTransition = { transitions.popEnter(this) },
            popExitTransition = { transitions.popExit(this) }
        ) {
            composable(MAIN_ROUTE) {
                MainTabsScaffold(
                    onNavigateToSearch = { rootNavController.navigateToSearch() },
                    onNavigateToSettings = { rootNavController.navigate(SETTINGS_ROUTE) },
                    onNavigateToAccount = { rootNavController.navigate(ACCOUNT_ROUTE) },
                    onNavigateToBbSpace = { rootNavController.navigateToBbSpace() },
                    onNavigateToHistory = { rootNavController.navigateToHistory() },
                    onNavigateToDownload = { rootNavController.navigateToDownload() },
                    onNavigateToVideo = openVideo,
                    onNavigateToSpace = rootNavController::navigateToSpace,
                    onNavigateToLive = openLive
                )
            }

            loginScreen(
                onLoginSuccess = { rootNavController.popBackStack() },
                onBack = { rootNavController.popBackStack() },
                onSwitchToSms = {
                    rootNavController.navigate(SMS_LOGIN_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )

            smsLoginScreen(
                onLoginSuccess = { rootNavController.popBackStack() },
                onBack = { rootNavController.popBackStack() },
                onSwitchToQr = {
                    rootNavController.navigate(LOGIN_ROUTE) {
                        popUpTo(SMS_LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )

            accountScreen(
                onBack = { rootNavController.popBackStack() },
                onAddAccount = { rootNavController.navigate(SMS_LOGIN_ROUTE) },
                onSwitched = { rootNavController.popBackStack() }
            )

            bbSpaceScreen(rootNavController)
            settingsScreen(rootNavController)
            searchScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo
            )
            historyScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo,
                onOpenLive = openLive
            )
            spaceScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo
            )
            videoScreen(
                onBack = exitPlayback,
                onOpenVideo = openVideo,
                onOpenSpace = rootNavController::navigateToSpace,
                onOpenDownloadCache = { rootNavController.navigateToDownload() },
                onStartDownload = downloadViewModel::enqueueDownload
            )
            downloadScreen(
                navController = rootNavController,
                onBack = { rootNavController.popBackStack() },
                viewModel = downloadViewModel
            )
            liveScreen(
                onBack = exitPlayback
            )
        }

        target?.let { miniTarget ->
            if (playbackHostViewModel.isMiniMode && miniPlayerEnabled && !isPlaybackRoute) {
            InAppMiniPlayer(
                player = player,
                target = miniTarget,
                sessionState = sessionState,
                pageMeta = pageMeta,
                onExpand = {
                    playbackHostViewModel.expand()
                    when (val playbackTarget = miniTarget) {
                        is StreamPlaybackTarget.Video -> openVideo(playbackTarget.route)
                        is StreamPlaybackTarget.Live -> openLive(playbackTarget.route)
                    }
                },
                onTogglePlay = playbackHostViewModel::togglePlayPause,
                onClose = playbackHostViewModel::close,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = 16.dp,
                        top = 16.dp,
                        start = 16.dp,
                        bottom = if (currentRoute == MAIN_ROUTE) 88.dp else 16.dp
                    )
            )
        }
        }
    }
}

@Composable
private fun MainTabsScaffold(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToVideo: (VideoRoute) -> Unit,
    onNavigateToSpace: (SpaceRoute) -> Unit,
    onNavigateToLive: (LiveRoute) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelRoute.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            TopLevelRoute.entries.forEach { tab ->
                if (currentTab == tab) {
                    saveableStateHolder.SaveableStateProvider(tab.route) {
                        when (tab) {
                            TopLevelRoute.HOME -> HomeScreen(
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToSettings = onNavigateToSettings,
                                onOpenVideo = onNavigateToVideo,
                                onOpenSpace = onNavigateToSpace,
                                onOpenLive = onNavigateToLive
                            )
                            TopLevelRoute.DYNAMIC -> PlaceholderScreen("动态")
                            TopLevelRoute.MESSAGE -> PlaceholderScreen("消息")
                            TopLevelRoute.PROFILE -> UserScreen(
                                onNavigateToAccount = onNavigateToAccount,
                                onNavigateToBbSpace = onNavigateToBbSpace,
                                onNavigateToHistory = onNavigateToHistory,
                                onNavigateToDownload = onNavigateToDownload,
                                onOpenSpace = onNavigateToSpace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(title)
    }
}
