package com.ay.briefyou.ui.page.adaptive

import com.ay.briefyou.brief.reading.BriefReadingPage // Brief You
import com.ay.briefyou.brief.reading.briefEnterTransition // Brief You
import com.ay.briefyou.brief.reading.briefExitTransition // Brief You
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.LocalBackgroundTextMeasurementExecutor
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import com.ay.briefyou.ui.component.reader.ExpandedContentWidth
import com.ay.briefyou.ui.component.reader.LocalTextContentWidth
import com.ay.briefyou.ui.component.reader.MediumContentWidth
import com.ay.briefyou.ui.page.home.flow.FlowPage
import com.ay.briefyou.ui.page.home.reading.ReadingPage
import timber.log.Timber

@Parcelize data class ArticleData(val articleId: String, val listIndex: Int? = null) : Parcelable

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ArticleListReaderPage(
    modifier: Modifier = Modifier,
    scaffoldDirective: PaneScaffoldDirective,
    navigator: ThreePaneScaffoldNavigator<ArticleData>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: ArticleListReaderViewModel,
    onBack: () -> Unit,
    onNavigateToStylePage: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {}, // Brief You
) {

    val scope = rememberCoroutineScope()
    // Brief You: one text-measuring thread for the list, made once. Made inside the provider below,
    // a new one was handed down each time the list pane recomposed (each tap, slide and back step),
    // which rebuilt every visible row (a new value for the whole list) and left a thread behind.
    val textMeasureExecutor = remember { Executors.newSingleThreadExecutor() } // Brief You
    androidx.compose.runtime.DisposableEffect(textMeasureExecutor) { onDispose { textMeasureExecutor.shutdown() } } // Brief You

    val backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    val hiddenAnchor = remember(scaffoldDirective) { PaneExpansionAnchor.Offset.fromStart(0.dp) }

    val expandedAnchor =
        remember(scaffoldDirective) {
            PaneExpansionAnchor.Offset.fromStart(scaffoldDirective.defaultPanePreferredWidth)
        }

    val paneExpansionState =
        rememberPaneExpansionState(
            initialAnchoredIndex = 1,
            anchors = listOf(hiddenAnchor, expandedAnchor),
            anchoringAnimationSpec =
                spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = 1f),
        )

    val isTwoPane =
        navigator.scaffoldValue.run {
            get(ListDetailPaneScaffoldRole.List) == PaneAdaptedValue.Expanded &&
                get(ListDetailPaneScaffoldRole.Detail) == PaneAdaptedValue.Expanded
        }

    val navigationAction =
        if (isTwoPane) {
            val currentAnchor = paneExpansionState.currentAnchor
            if (currentAnchor == null || currentAnchor == expandedAnchor) {
                NavigationAction.HideList
            } else {
                NavigationAction.ExpandList
            }
        } else {
            NavigationAction.Close
        }

    var listAlpha by rememberSaveable { mutableFloatStateOf(1f) }

    LaunchedEffect(isTwoPane) {
        Timber.d("isTwoPane: $isTwoPane")
        if (!isTwoPane) {
            listAlpha = 1f
            paneExpansionState.animateTo(expandedAnchor)
        }
    }

    val contentWidth =
        when (navigationAction) {
            NavigationAction.HideList,
            NavigationAction.Close -> MediumContentWidth
            NavigationAction.ExpandList -> ExpandedContentWidth
        }

    val animatedContentWidth by animateDpAsState(contentWidth)
    val animatedListAlpha by animateFloatAsState(listAlpha)

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        defaultBackBehavior = backBehavior,
        paneExpansionDragHandle = { Spacer(modifier = Modifier.width(2.dp)) },
        paneExpansionState = paneExpansionState,
        listPane = {
            if (navigationAction == NavigationAction.ExpandList) {
                BackHandler {
                    listAlpha = 1f
                    scope.launch { paneExpansionState.animateTo(expandedAnchor) }
                }
            }
            AnimatedPane(
                // Brief You: no dark flash between the list and an article (see PaneTransitions)
                enterTransition = motionDataProvider.briefEnterTransition(paneRole, !isTwoPane),
                exitTransition = motionDataProvider.briefExitTransition(paneRole, !isTwoPane),
            ) {
                CompositionLocalProvider(
                    LocalBackgroundTextMeasurementExecutor provides textMeasureExecutor // Brief You: made once (above)
                ) {
                    Box(modifier = Modifier.alpha(animatedListAlpha)) {
                        FlowPage(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            viewModel = viewModel,
                            onNavigateUp = onBack,
                            isTwoPane = isTwoPane,
                            showFilterBar = // Brief You
                                navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] ==
                                    PaneAdaptedValue.Expanded,
                            navigateToArticle = { id, index ->
                                viewModel.openNow(id, index) // Brief You: started on the tap
                                scope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = ArticleData(articleId = id, listIndex = index),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane(
                // Brief You: no dark flash between the list and an article (see PaneTransitions)
                enterTransition = motionDataProvider.briefEnterTransition(paneRole, !isTwoPane, quickFade = true), // Brief You: see quickFade
                exitTransition = motionDataProvider.briefExitTransition(paneRole, !isTwoPane),
            ) {
                if (com.ay.briefyou.brief.perf.PerfLog.enabled) { // Brief You: Nightly stutter log
                    val paneMoving = transition.currentState != transition.targetState // Brief You
                    LaunchedEffect(paneMoving) { com.ay.briefyou.brief.perf.PerfLog.note(if (paneMoving) "article pane: slide" else "article pane: slide done") } // Brief You
                } // Brief You
                val contentKey = navigator.currentDestination?.contentKey
                LaunchedEffect(contentKey) {
                    if (contentKey == null) {
                        delay(100L)
                        viewModel.clearReadingData()
                    } else {
                        viewModel.initData(
                            articleId = contentKey.articleId,
                            listIndex = contentKey.listIndex,
                        )
                    }
                }

                CompositionLocalProvider(LocalTextContentWidth provides animatedContentWidth) {
                    BriefReadingPage( // Brief You: in the picture's colours
                        viewModel = viewModel,
                        navigationAction = navigationAction,
                        onLoadArticle = { id, index ->
                            scope.launch {
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey = ArticleData(articleId = id, listIndex = index),
                                )
                            }
                        },
                        onNavAction = {
                            when (it) {
                                NavigationAction.Close -> {
                                    if (navigator.canNavigateBack(backBehavior)) {
                                        scope
                                            .launch { navigator.navigateBack(backBehavior) }
                                            .invokeOnCompletion { viewModel.clearReadingData() }
                                    } else {
                                        onBack()
                                    }
                                }
                                NavigationAction.HideList -> {
                                    scope.launch {
                                        listAlpha = 0f
                                        paneExpansionState.animateTo(hiddenAnchor)
                                    }
                                }
                                NavigationAction.ExpandList -> {
                                    listAlpha = 1f
                                    scope.launch { paneExpansionState.animateTo(expandedAnchor) }
                                }
                            }
                        },
                        onNavigateToStylePage = onNavigateToStylePage,
                        onNavigateToAiSettings = onNavigateToAiSettings, // Brief You
                    )
                }
            }
        },
    )
}
