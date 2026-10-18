package me.ash.reader.ui.page.adaptive

import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.zIndex
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
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
import me.ash.reader.ui.component.reader.ExpandedContentWidth
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.MediumContentWidth
import me.ash.reader.ui.page.home.flow.FlowPage
import me.ash.reader.ui.page.home.reading.ReadingPage
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
    onNavigateToAiSettings: () -> Unit = {},
) {

    val scope = rememberCoroutineScope()

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

    // Brief You: on a phone the article opens out of its card and closes back into it, over a
    // still picture of the list (see ArticleReveal).
    val reveal = remember { ArticleReveal() }
    val listLayer = rememberGraphicsLayer()
    val ownMotion = !isTwoPane && reveal.listPicture != null
    val closeArticle: () -> Unit = {
        scope.launch {
            reveal.card = ArticleCardBounds.of(navigator.currentDestination?.contentKey?.articleId)
            reveal.close {
                navigator.navigateBack(backBehavior)
                viewModel.clearReadingData()
            }
        }
    }

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

    Box(modifier = modifier) {
    if (!isTwoPane) ArticleRevealBackdrop(reveal)
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = Modifier,
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
            // On a phone the article opens over a still picture of the list (see ArticleReveal), so
            // the list pane itself just goes and comes back.
            AnimatedPane(
                enterTransition =
                    if (ownMotion) EnterTransition.None
                    else motionDataProvider.calculateEnterTransition(paneRole),
                exitTransition =
                    if (ownMotion) ExitTransition.None
                    else motionDataProvider.calculateExitTransition(paneRole),
            ) {
                CompositionLocalProvider(
                    LocalBackgroundTextMeasurementExecutor provides
                        Executors.newSingleThreadExecutor(),
                ) {
                    Box(
                        modifier =
                            Modifier.alpha(animatedListAlpha)
                                // Kept as a picture for the article opening over it (phone).
                                .then(if (isTwoPane) Modifier else Modifier.recordInto(listLayer))
                    ) {
                        FlowPage(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            viewModel = viewModel,
                            onNavigateUp = onBack,
                            isTwoPane = isTwoPane,
                            showFilterBar =
                                navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] ==
                                    PaneAdaptedValue.Expanded,
                            navigateToArticle = { id, index ->
                                scope.launch {
                                    if (!isTwoPane) {
                                        reveal.prepareOpen(listLayer, ArticleCardBounds.of(id))
                                    }
                                    launch {
                                        navigator.navigateTo(
                                            pane = ListDetailPaneScaffoldRole.Detail,
                                            contentKey = ArticleData(articleId = id, listIndex = index),
                                        )
                                    }
                                    if (!isTwoPane) reveal.open()
                                }
                            },
                        )
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane(
                enterTransition =
                    if (ownMotion) EnterTransition.None
                    else motionDataProvider.calculateEnterTransition(paneRole),
                exitTransition =
                    if (ownMotion) ExitTransition.None
                    else motionDataProvider.calculateExitTransition(paneRole),
            ) {
                val contentKey = navigator.currentDestination?.contentKey
                // Going back closes the article into its card; the predictive back gesture
                // follows your finger, letting go finishes it and cancelling opens it again.
                PredictiveBackHandler(enabled = ownMotion) { events ->
                    try {
                        events.collect { event -> reveal.progress.snapTo(1f - event.progress) }
                        closeArticle()
                    } catch (e: CancellationException) {
                        scope.launch { reveal.settleOpen() }
                    }
                }
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
                  Box(modifier = if (ownMotion) Modifier.articleReveal(reveal) else Modifier) {
                    ReadingPage(
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
                                    if (ownMotion) {
                                        closeArticle()
                                    } else if (navigator.canNavigateBack(backBehavior)) {
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
                        onNavigateToAiSettings = onNavigateToAiSettings,
                    )
                  }
                }
            }
        },
    )
    }
}


