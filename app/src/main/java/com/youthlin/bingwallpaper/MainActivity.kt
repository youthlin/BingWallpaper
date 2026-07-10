package com.youthlin.bingwallpaper

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youthlin.bingwallpaper.ui.BingViewModel
import com.youthlin.bingwallpaper.ui.DetailScreen
import com.youthlin.bingwallpaper.ui.HomeScreen
import com.youthlin.bingwallpaper.ui.SettingsScreen
import com.youthlin.bingwallpaper.ui.theme.BingTheme
import kotlinx.coroutines.launch

/**
 * 整个应用唯一的 Activity。AndroidManifest 中声明为启动入口。
 * 启动流程：BingApp.onCreate() → MainActivity.onCreate() → setContent { BingTheme { RootNav() } }
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 内容延伸到状态栏和导航栏下方
        setContent { BingTheme { RootNav() } }
    }
}

private const val ANIM = 240

/**
 * 应用导航根节点。只有两个页面：
 * 1. "tabs" — 底部 Tab 页（壁纸列表 + 设置）
 * 2. "detail/{id}" — 图片详情页（左右滑动浏览）
 *
 * 导航行为：
 * - 列表点击 → 进入详情页（从右滑入，返回时从左滑出）
 * - 详情页返回 → 回到列表（预测手势动画）
 */
@Composable
private fun RootNav() {
    val nav = rememberNavController()
    val vm: BingViewModel = viewModel()
    val context = LocalContext.current
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.restoreKnownGalleryRefs(delayMillis = 300, restart = true)
    }

    // 启动后尽早请求图库权限，先恢复旧图片 Uri，再允许详情页/预取触发下载。
    LaunchedEffect(Unit) {
        val permissions = galleryReadPermissionsToRequest(context)
        if (permissions.isNotEmpty()) {
            galleryPermissionLauncher.launch(permissions)
        } else {
            vm.restoreKnownGalleryRefs()
        }
    }

    // 收集 ViewModel 发出的一次性 Toast 事件
    LaunchedEffect(Unit) {
        vm.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(
        navController = nav,
        startDestination = "tabs",
        enterTransition = {
            slideInHorizontally(tween(ANIM)) { it } // 新页面从右侧滑入
        },
        exitTransition = {
            slideOutHorizontally(tween(ANIM)) { -it / 4 } // 旧页面向左1/4滑出
        },
        popEnterTransition = {
            slideInHorizontally(tween(ANIM)) { -it / 4 } // 返回时从左侧1/4滑入
        },
        popExitTransition = {
            slideOutHorizontally(tween(ANIM)) { it } // 当前页向右滑出
        }
    ) {
        composable("tabs") {
            TabsScreen(
                onOpenDetail = { id -> nav.navigate("detail/$id") },
                vm = vm
            )
        }
        composable("detail/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailScreen(id = id, onBack = { nav.popBackStack() }, vm = vm)
        }
    }
}

/**
 * 底部 Tab 页：用 HorizontalPager 切换"壁纸"和"设置"两个页面，
 * 配合 NavigationBar 实现点击切换和滑动切换。
 */
@Composable
private fun TabsScreen(
    onOpenDetail: (String) -> Unit,
    vm: BingViewModel
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val current = pagerState.currentPage
                NavigationBarItem(
                    selected = current == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) }
                )
                NavigationBarItem(
                    selected = current == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> HomeScreen(onOpenDetail = onOpenDetail, vm = vm)
                    1 -> SettingsScreen()
                }
            }
        }
    }
}
