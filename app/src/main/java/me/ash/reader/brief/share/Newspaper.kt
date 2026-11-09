@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package me.ash.reader.brief.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.graphics.scale
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.brief.ai.ArticleText
import me.ash.reader.brief.ai.GemmaSummarizer
import me.ash.reader.brief.reading.FeedContent
import me.ash.reader.brief.ui.BriefYouMark
import me.ash.reader.brief.ui.LiterataFontFamily
import me.ash.reader.brief.ui.OffscreenPictureAt
import me.ash.reader.brief.ui.sharePicture
import me.ash.reader.domain.model.article.ArticleWithFeed
import org.jsoup.Jsoup

/** The steps of making a newspaper; the ones that don't apply are left out. */
enum class PressStep { FullText, Brief, Page }

/** The front page, gathered before the picture is taken. */
internal data class FrontPage(
    val masthead: String,
    val headline: String,
    val link: String,
    val host: String,
    val date: LocalDate,
    /** The lead picture, already printed in halftone dots. */
    val picture: ImageBitmap?,
    val brief: List<String>,
    val opening: String,
)

/** One newspaper being made, for the progress sheet. */
@Stable
class PressRun internal constructor(val title: String, val steps: List<PressStep>) {
    var current by mutableStateOf<PressStep?>(steps.firstOrNull())
        internal set
    /** The step that failed, if any (it shows Try again). */
    var failed by mutableStateOf<PressStep?>(null)
        internal set
    /** The AI's points so far, as they're written. */
    var brief by mutableStateOf("")
        internal set
    /** Shown only if it takes longer than a moment (without the AI it's nearly instant). */
    var visible by mutableStateOf(false)
        internal set

    fun isDone(step: PressStep): Boolean {
        val now = current ?: return true
        return steps.indexOf(step) < steps.indexOf(now)
    }
}

/** The app's newspaper maker: one, shared by the article screen and the article lists. */
val LocalNewspaperMaker = staticCompositionLocalOf<NewspaperMaker?> { null }

/**
 * Makes an article's front page and shares it: the feed's name as the masthead, the date and
 * site on the dateline, the headline, the lead picture printed in halftone dots, the AI's three
 * points boxed as "In brief", and the opening lines in two columns. A teaser is swapped for the
 * full article first. The title and link go along with the picture.
 */
@Stable
class NewspaperMaker internal constructor(
    private val context: Context,
    private val summarizer: GemmaSummarizer,
    private val articleText: ArticleText,
    private val scope: CoroutineScope,
) {
    var run by mutableStateOf<PressRun?>(null)
        private set

    internal var ready by mutableStateOf<FrontPage?>(null)
        private set

    private var job: Job? = null
    private var last: Pair<ArticleWithFeed, String?>? = null

    /** [html] is the article as shown (the full text on the reading page); else its description. */
    fun make(item: ArticleWithFeed, html: String? = null) {
        if (job?.isActive == true) return
        last = item to html
        job =
            scope.launch {
                val ai = summarizer.hasModel()
                val steps = buildList {
                    if (ai && articleText.needsFullText(item.article, html)) add(PressStep.FullText)
                    if (ai) add(PressStep.Brief)
                    add(PressStep.Page)
                }
                val press = PressRun(item.article.title, steps)
                run = press
                val reveal = launch {
                    delay(500)
                    press.visible = true
                }
                try {
                    val body =
                        if (PressStep.FullText in steps) {
                            articleText.best(item.article, html)
                        } else {
                            listOfNotNull(html, item.article.rawDescription).filter { it.isNotBlank() }.maxByOrNull { it.length }.orEmpty()
                        }
                    var points = emptyList<String>()
                    if (PressStep.Brief in steps) {
                        press.current = PressStep.Brief
                        var latest = ""
                        summarizer.summarizeForCard(item.article.title, body).collect {
                            latest = it
                            press.brief = it
                        }
                        points = bullets(latest)
                    }
                    press.current = PressStep.Page
                    ready = gather(item, body, points)
                    reveal.cancel()
                } catch (e: CancellationException) {
                    run = null
                    ready = null
                    throw e
                } catch (e: Exception) {
                    press.failed = press.current
                    press.visible = true
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        run = null
        ready = null
    }

    fun retry() {
        val (item, html) = last ?: return
        job = null
        run = null
        make(item, html)
    }

    internal fun done(picture: ImageBitmap?) {
        val page = ready
        ready = null
        if (picture == null || page == null) {
            run?.let {
                it.failed = PressStep.Page
                it.visible = true
            }
            return
        }
        run = null
        sharePicture(context, picture, "newspaper", listOf(page.headline, page.link).filter { it.isNotBlank() }.joinToString("\n"))
    }

    private suspend fun gather(item: ArticleWithFeed, body: String, points: List<String>): FrontPage {
        val article = item.article
        val pictureUrl =
            FeedContent.listImage(article.img, article.link)?.takeIf { it.isNotBlank() }
                ?: body.takeIf { it.isNotBlank() }?.let { FeedContent.prepared(it).hero }
        val picture = pictureUrl?.let { load(it) }?.let { withContext(Dispatchers.Default) { halftone(it) } }
        val host = runCatching { URI(article.link).host?.removePrefix("www.") }.getOrNull().orEmpty()
        return FrontPage(
            masthead = item.feed.name,
            headline = article.title,
            link = article.link,
            host = host,
            date = LocalDate.now(),
            picture = picture?.asImageBitmap(),
            brief = points,
            opening = opening(body, if (points.isEmpty()) 900 else 480),
        )
    }

    private suspend fun load(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                    val result =
                        context.imageLoader.execute(
                            ImageRequest.Builder(context).data(url).size(900).allowHardware(false).build()
                        )
                    (result as? SuccessResult)?.drawable?.toBitmapOrNull()
                }
                .getOrNull()
        }

    private companion object {
        /** The AI's points, one per line, without their marks. */
        fun bullets(text: String): List<String> =
            text.lines()
                .map { it.trim().trimStart('•', '-', '*', ' ').trim() }
                .filter { it.isNotEmpty() }
                .take(3)

        /** The opening lines as plain text, cut at a word. */
        fun opening(html: String, chars: Int): String {
            val text =
                runCatching { Jsoup.parse(html).apply { select("script, style, figure, img").remove() }.text() }
                    .getOrDefault("")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            return if (text.length <= chars) text else text.take(chars).substringBeforeLast(' ') + "…"
        }

        /**
         * The picture as newsprint: a black-and-white photo, a little more contrasty and toned
         * from ink to paper, with a faint dot screen on top. The photo stays recognisable; the
         * dots make it read as print. Drawn on the CPU, so it looks the same on every phone.
         */
        fun halftone(src: Bitmap): Bitmap {
            val width = 900
            val height = (width * src.height / src.width.toFloat()).toInt().coerceIn(1, 1400)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            canvas.drawColor(Paper.toArgb())
            // Grey (with 15% more contrast) mapped from ink (black) to paper (white), in one step.
            val contrast = 1.15f
            fun channel(ink: Float, paper: Float): FloatArray {
                val k = (paper - ink) / 255f
                return floatArrayOf(
                    k * contrast * 0.299f,
                    k * contrast * 0.587f,
                    k * contrast * 0.114f,
                    0f,
                    ink + k * (128f - 128f * contrast),
                )
            }
            val matrix =
                channel(Ink.red * 255, Paper.red * 255) +
                    channel(Ink.green * 255, Paper.green * 255) +
                    channel(Ink.blue * 255, Paper.blue * 255) +
                    floatArrayOf(0f, 0f, 0f, 1f, 0f)
            val photo = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
            }
            val scaled = src.scale(width, height, filter = true)
            canvas.drawBitmap(scaled, 0f, 0f, photo)
            // The dot screen: small ink dots on a grid at 45°, as big as the photo is dark, faint.
            val cell = 4
            val cols = width / cell + 2
            val rows = height / cell + 2
            val small = src.scale(cols, rows, filter = true)
            val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Ink.toArgb()
                alpha = 70
            }
            for (y in 0 until rows) {
                val shift = if (y % 2 == 1) cell / 2f else 0f
                for (x in 0 until cols) {
                    val p = small.getPixel(x, y)
                    val r = (p shr 16) and 0xff
                    val g = (p shr 8) and 0xff
                    val b = p and 0xff
                    val light = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    val radius = cell * 0.6f * sqrt(1f - light)
                    if (radius > 0.3f) canvas.drawCircle(x * cell + shift, y * cell + cell / 2f, radius, dots)
                }
            }
            return out
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NewspaperDependencies {
    fun summarizer(): GemmaSummarizer

    fun articleText(): ArticleText
}

@Composable
fun rememberNewspaperMaker(): NewspaperMaker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember {
        val deps = EntryPointAccessors.fromApplication(context, NewspaperDependencies::class.java)
        NewspaperMaker(context, deps.summarizer(), deps.articleText(), scope)
    }
}

/** The progress sheet, and the front page's picture taken off screen. Put it where it gets the screen's width. */
@Composable
fun NewspaperHost(maker: NewspaperMaker) {
    // Always 1600 pixels wide (a 400 dp page at 4×), whatever the phone, so the small print
    // stays sharp after messaging apps shrink it.
    maker.ready?.let { page ->
        OffscreenPictureAt(widthPx = 1600, widthDp = 400f, capturing = true, onPicture = { maker.done(it) }) {
            FrontPagePicture(page)
        }
    }
    val run = maker.run ?: return
    if (!run.visible) return
    ModalBottomSheet(
        onDismissRequest = maker::cancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PressSheet(run, onCancel = maker::cancel, onRetry = maker::retry)
    }
}

@Composable
private fun PressSheet(run: PressRun, onCancel: () -> Unit, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
        Text(stringResource(R.string.newspaper_printing), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            run.title,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(16.dp))
        run.steps.forEachIndexed { i, step ->
            val big = 20.dp
            val small = 4.dp
            val failed = run.failed == step
            val now = run.current == step && !failed
            val done = run.isDone(step) && !failed
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = 1.5.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (i == 0) big else small,
                                topEnd = if (i == 0) big else small,
                                bottomStart = if (i == run.steps.lastIndex) big else small,
                                bottomEnd = if (i == run.steps.lastIndex) big else small,
                            )
                        )
                        .background(if (failed) scheme.errorContainer else scheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    when {
                        failed ->
                            Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.error), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PriorityHigh, null, tint = scheme.onError, modifier = Modifier.size(16.dp))
                            }
                        done ->
                            Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                            }
                        now -> CircularWavyProgressIndicator(modifier = Modifier.size(26.dp))
                        else -> Box(Modifier.size(26.dp).clip(CircleShape).background(scheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(
                        when (step) {
                            PressStep.FullText -> R.string.newspaper_step_full
                            PressStep.Brief -> R.string.newspaper_step_brief
                            PressStep.Page -> R.string.newspaper_step_page
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        when {
                            failed -> scheme.onErrorContainer
                            now || done -> scheme.onSurface
                            else -> scheme.onSurfaceVariant
                        },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (run.failed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                androidx.compose.material3.Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.newspaper_retry))
                }
            }
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
    }
}

/** "Share as newspaper" in an article's long-press menu, under Share. */
@Composable
fun NewspaperMenuItem(item: ArticleWithFeed, iconSize: DpSize, onItemClick: (() -> Unit)?) {
    val maker = LocalNewspaperMaker.current ?: return
    DropdownMenuItem(
        text = { Text(text = stringResource(R.string.newspaper_share)) },
        onClick = {
            maker.make(item)
            onItemClick?.invoke()
        },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Newspaper, contentDescription = null, modifier = Modifier.size(iconSize)) },
    )
}

private val Paper = Color(0xFFF6F1E6)
private val Ink = Color(0xFF1B1A17)
private val Faint = Color(0xFF5C574C)
private val Rule = Color(0xFFC9BFA9)

/** Literata at its heaviest, for the masthead. */
private val MastheadFont =
    FontFamily(
        Font(
            R.font.literata,
            weight = FontWeight.Black,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(900)),
        )
    )

private val ColumnText =
    TextStyle(
        fontFamily = LiterataFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Ink,
        textAlign = TextAlign.Justify,
        hyphens = Hyphens.Auto,
        lineBreak = LineBreak.Paragraph,
    )

/**
 * The front page, always on cream newsprint (the same in light and dark): just the page, straight,
 * with nothing around it.
 */
@Composable
private fun FrontPagePicture(page: FrontPage) {
    run {
        // Cut with a zig-zag at the top and bottom, like a page torn off a pad; the gaps are
        // transparent.
        Column(modifier = Modifier.fillMaxWidth().clip(ZigZagEdges).background(Paper).padding(horizontal = 18.dp, vertical = 26.dp)) {
            BasicText(
                text = page.masthead,
                style = TextStyle(fontFamily = MastheadFont, fontSize = 42.sp, lineHeight = 46.sp, color = Ink, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 42.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Dateline(page.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())))
                Dateline(stringResource(R.string.newspaper_issue))
                Dateline(page.host)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
            Text(
                text = page.headline,
                style =
                    TextStyle(
                        fontFamily = LiterataFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (page.picture == null) 34.sp else 28.sp,
                        lineHeight = if (page.picture == null) 38.sp else 31.sp,
                        color = Ink,
                        letterSpacing = (-0.3).sp,
                    ),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
            )
            page.picture?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (page.brief.isNotEmpty()) {
                    Column(Modifier.weight(1f).border(1.5.dp, Ink).padding(horizontal = 9.dp, vertical = 7.dp)) {
                        Text(
                            stringResource(R.string.newspaper_in_brief).uppercase(),
                            style = TextStyle(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, color = Ink),
                        )
                        page.brief.forEach { point ->
                            Text("• $point", style = ColumnText.copy(textAlign = TextAlign.Start), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Divider()
                    Opening(page.opening, Modifier.weight(1f))
                } else {
                    val (first, second) = split(page.opening)
                    Opening(first, Modifier.weight(1f))
                    Divider()
                    Text(second, style = ColumnText, modifier = Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Ink))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.newspaper_continued, page.host),
                    style = TextStyle(fontSize = 11.sp, color = Faint),
                    modifier = Modifier.weight(1f),
                )
                BriefYouMark(Faint, container = Ink, glyph = Paper, shape = RoundedCornerShape(30))
            }
        }
    }
}

@Composable
private fun Dateline(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 9.sp, letterSpacing = 0.8.sp, color = Ink),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(Rule))
}

/** The opening lines with a big first letter, as papers start a story. */
@Composable
private fun Opening(text: String, modifier: Modifier) {
    if (text.isEmpty()) {
        Spacer(modifier)
        return
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)) { append(text.take(1)) }
            append(text.drop(1))
        },
        style = ColumnText,
        modifier = modifier,
    )
}

/** The text in two halves for two columns, cut at a word near the middle. */
private fun split(text: String): Pair<String, String> {
    if (text.length < 120) return text to ""
    val middle = text.lastIndexOf(' ', text.length / 2).takeIf { it > 0 } ?: (text.length / 2)
    return text.substring(0, middle).trim() to text.substring(middle).trim()
}

/** A page edge cut in a zig-zag at the top and bottom (teeth 12 dp wide, 6 dp deep). */
private val ZigZagEdges =
    object : androidx.compose.ui.graphics.Shape {
        override fun createOutline(
            size: androidx.compose.ui.geometry.Size,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ): androidx.compose.ui.graphics.Outline {
            val depth = with(density) { 6.dp.toPx() }
            val teeth = (size.width / with(density) { 12.dp.toPx() }).toInt().coerceAtLeast(1)
            val tooth = size.width / teeth
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, depth)
                    for (i in 0 until teeth) {
                        lineTo(i * tooth + tooth / 2, 0f)
                        lineTo((i + 1) * tooth, depth)
                    }
                    lineTo(size.width, size.height - depth)
                    for (i in teeth downTo 1) {
                        lineTo(i * tooth - tooth / 2, size.height)
                        lineTo((i - 1) * tooth, size.height - depth)
                    }
                    close()
                }
            return androidx.compose.ui.graphics.Outline.Generic(path)
        }
    }
