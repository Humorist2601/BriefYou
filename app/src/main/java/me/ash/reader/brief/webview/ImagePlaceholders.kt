package me.ash.reader.brief.webview

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Placeholders for the pictures in an article, so the text doesn't jump while they load.
 *
 * Each picture that isn't there yet is put in a box of its own size (read from its width and
 * height; a wide 16:9 box when the page doesn't say). The box is tonal, with Material 3 Expressive's
 * shape-changing loading indicator in the middle. When the picture arrives the indicator fades away and
 * the picture fades in; a box that had to guess eases to the picture's real shape first. Small
 * pictures (icons, emoji) and pictures already loaded get no box.
 *
 * Added to the reading page's own styles and script by Brief You's web view, so Read You's
 * WebViewStyle and WebViewScript stay as they are.
 */
object ImagePlaceholders {

    /** Pictures narrower (or lower) than this many pixels get no box. */
    private const val MIN_SIZE = 120

    /** The last styles made, by their two colours: the reading page asks again on every redraw. */
    private var lastCss: Triple<Int, Int, String>? = null

    /** The styles, in the page's colours: the box's tonal colour and the indicator's. */
    fun css(boxColor: Int, indicatorColor: Int): String {
        // Building the animated indicator takes a few milliseconds, too long to repeat while the
        // article opens; it only changes with the colours.
        lastCss?.let { (box, indicator, css) ->
            if (box == boxColor && indicator == indicatorColor) return css
        }
        return buildCss(boxColor, indicatorColor).also {
            lastCss = Triple(boxColor, indicatorColor, it)
        }
    }

    private fun buildCss(boxColor: Int, indicatorColor: Int): String {
        val box = cssColor(boxColor)
        val indicator = svgDataUri(indicatorColor)
        return """
/* Brief You: picture placeholders (see ImagePlaceholders) */
.brief-box {
    display: block !important;
    position: relative !important;
    overflow: hidden !important;
    margin: 0.5em calc(0px - var(--text-margin) + var(--img-margin)) !important;
    max-width: calc(100% + 2 * var(--text-margin) - 2 * var(--img-margin)) !important;
    border-radius: var(--img-border-radius) !important;
    background: $box;
    transition: height 0.35s cubic-bezier(0.2, 0, 0, 1), background-color 0.4s ease;
}
.brief-box::after {
    content: "";
    position: absolute;
    left: 50%;
    top: 50%;
    width: 44px;
    height: 44px;
    margin: -22px 0 0 -22px;
    background: url("$indicator") center / contain no-repeat;
    transition: opacity 0.2s ease;
}
.brief-box.brief-loaded {
    background-color: transparent;
}
.brief-box.brief-loaded::after {
    opacity: 0;
}
.brief-box > img {
    display: block !important;
    margin: 0 !important;
    width: 100% !important;
    max-width: 100% !important;
    height: 100% !important;
    object-fit: cover;
    border-radius: 0 !important;
    opacity: 0;
    transition: opacity 0.45s ease;
}
.brief-box.brief-loaded > img {
    opacity: 1;
}
/* Pictures narrower than the text: centred (wide ones still run edge to edge). */
.brief-box.brief-center {
    margin-left: auto !important;
    margin-right: auto !important;
}
img.brief-center {
    display: block !important;
    margin-left: auto !important;
    margin-right: auto !important;
    max-width: 100% !important;
}
.brief-box + small {
    display: inline-block;
    margin-top: var(--image-caption-margin) !important;
}
"""
    }

    /** The script: boxes the pictures still loading, and reveals each one when it arrives. */
    val SCRIPT: String =
        """
// Brief You: picture placeholders (see ImagePlaceholders)
(function () {
    var MIN = $MIN_SIZE;
    // A picture (or its box) narrower than the text is centred; icons and emoji stay in the line.
    function centerIfSmall(el, width) {
        var column = el.parentElement;
        while (column && column.clientWidth === 0) column = column.parentElement;
        if (column && width >= MIN && width < column.clientWidth - 1) el.classList.add("brief-center");
    }
    // A picture that can't load tries the address in its data-fallback (see FeedContent). This
    // replaces an inline onerror, which the page's content policy blocks.
    document.addEventListener("error", function (e) {
        var img = e.target;
        if (img && img.tagName === "IMG" && img.dataset.fallback) {
            var next = img.dataset.fallback;
            delete img.dataset.fallback;
            img.dataset.retrying = "1";
            img.src = next;
        }
    }, true);
    // One that had already failed before this script ran (no event is coming for it).
    document.querySelectorAll("img[data-fallback]").forEach(function (img) {
        if (img.complete && img.naturalWidth === 0) {
            var next = img.dataset.fallback;
            delete img.dataset.fallback;
            img.dataset.retrying = "2";
            img.src = next;
        }
    });
    document.querySelectorAll("article img").forEach(function (img) {
        if (img.closest("table") || img.closest(".brief-box")) return;
        // The size the page gives the picture: its width and height, or a pixel size in its style.
        var w = parseInt(img.getAttribute("width")) || (/px$/.test(img.style.width) ? parseInt(img.style.width) : 0);
        var h = parseInt(img.getAttribute("height")) || (/px$/.test(img.style.height) ? parseInt(img.style.height) : 0);
        if ((w && w < MIN) || (h && h < MIN / 2)) return;
        if (img.complete && img.naturalWidth > 0) {
            centerIfSmall(img, img.naturalWidth);
            return;
        }

        var guess = !(w && h);
        var box = document.createElement("span");
        box.className = "brief-box";
        box.style.aspectRatio = guess ? "16 / 9" : w + " / " + h;
        // As wide as the picture says (the box's max-width keeps it on the page), or the page.
        box.style.width = w ? w + "px" : "100vw";
        img.parentNode.insertBefore(box, img);
        box.appendChild(img);
        if (w) centerIfSmall(box, w);

        function unbox() {
            if (!box.parentNode) return;
            box.parentNode.insertBefore(img, box);
            box.remove();
        }

        function reveal() {
            if (!box.parentNode) return;
            // A small picture after all: shown as it is, without a box.
            if (img.naturalWidth < MIN || img.naturalHeight < MIN / 2) {
                unbox();
                return;
            }
            if (guess) {
                // Ease from the guessed shape to the picture's own.
                var from = box.getBoundingClientRect().height;
                box.style.width = img.naturalWidth + "px";
                box.style.aspectRatio = "auto";
                box.style.height = from + "px";
                var width = box.getBoundingClientRect().width;
                box.style.height = (width * img.naturalHeight / img.naturalWidth) + "px";
                centerIfSmall(box, img.naturalWidth);
            }
            box.classList.add("brief-loaded");
        }

        img.addEventListener("load", reveal);
        img.addEventListener("error", function () {
            // A picture with its own fallback tries that next (see FeedContent); others go.
            if (img.dataset.retrying === "1") img.dataset.retrying = "2";
            else unbox();
        });
        if (img.complete && img.naturalWidth > 0) reveal();
    });
})();
"""

    private fun cssColor(argb: Int): String {
        val a = (argb ushr 24 and 0xFF) / 255f
        return "rgba(${argb shr 16 and 0xFF}, ${argb shr 8 and 0xFF}, ${argb and 0xFF}, $a)"
    }

    /**
     * The loading indicator as an animated SVG data address, in [argb]: like Material 3
     * Expressive's LoadingIndicator (used elsewhere in the app), it turns while morphing through
     * soft burst, nine-sided cookie, pentagon, pill, sunny, four-sided cookie and oval. Every shape
     * is drawn with the same number of points around the centre so one can morph into the next.
     */
    private fun svgDataUri(argb: Int): String {
        val paths = PATHS
        val n = paths.size
        val keyTimes = (0..n).joinToString(";") { fmt(it.toDouble() / n, 4) }
        val keySplines = List(n) { "0.3 0 0 1" }.joinToString(";")
        val rotation = (0..n).joinToString(";") { "${fmt(it * 360.0 * 3 / n, 1)} 22 22" }
        val timing =
            "dur='${fmt(n * 0.65, 2)}s' repeatCount='indefinite' calcMode='spline' " +
                "keyTimes='$keyTimes' keySplines='$keySplines'"
        val hex = "%06X".format(argb and 0xFFFFFF)
        val svg =
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 44 44'>" +
                "<path fill='%23$hex' d='${paths[0]}'>" +
                "<animate attributeName='d' $timing values='${(paths + paths[0]).joinToString(";")}'/>" +
                "<animateTransform attributeName='transform' type='rotate' $timing values='$rotation'/>" +
                "</path></svg>"
        return "data:image/svg+xml;utf8,$svg"
    }

    private const val POINTS = 96

    /** The shapes as a distance from the centre for each angle. */
    private val SHAPES: List<(Double) -> Double> =
        listOf(
            { t -> 0.86 + 0.084 * cos(10 * t).pow(3) + 0.05 * cos(10 * t) }, // soft burst
            { t -> 0.9 + 0.08 * cos(9 * t) }, // nine-sided cookie
            { t -> roundedPolygon(t, 5) }, // pentagon
            { t -> pill(t) },
            { t -> 0.9 + 0.1 * cos(8 * t) }, // sunny
            { t -> 0.86 + 0.13 * cos(4 * t) }, // four-sided cookie
            { t -> 1 / sqrt(cos(t).pow(2) + (sin(t) / 0.72).pow(2)) }, // oval
        )

    /** The shapes' paths, worked out once. */
    private val PATHS: List<String> by lazy { SHAPES.map(::shapePath) }

    /** A shape as a path of [POINTS] points, scaled so every shape looks the same size. */
    private fun shapePath(shape: (Double) -> Double): String {
        val radii = DoubleArray(POINTS) { shape(2 * PI * it / POINTS) }
        val scale = 19 / sqrt(radii.sumOf { it * it } / POINTS) * 0.93
        return radii.withIndex().joinToString("L", prefix = "M", postfix = "Z") { (i, r) ->
            val t = 2 * PI * i / POINTS
            "${fmt(22 + scale * r * cos(t), 1)} ${fmt(22 + scale * r * sin(t), 1)}"
        }
    }

    private fun roundedPolygon(t: Double, sides: Int): Double {
        val step = 2 * PI / sides
        val u = ((t + PI / 2) % step + step) % step - step / 2
        val r = cos(PI / sides) / cos(u)
        return 0.75 * r + 0.25 * cos(PI / sides) * 1.06
    }

    /** A horizontal pill (stadium): where the ray at angle [t] leaves it. */
    private fun pill(t: Double, half: Double = 1.0, radius: Double = 0.58): Double {
        var lo = 0.0
        var hi = 2.0
        repeat(40) {
            val m = (lo + hi) / 2
            val x = (abs(m * cos(t)) - (half - radius)).coerceAtLeast(0.0)
            val y = abs(m * sin(t))
            if (x * x + y * y <= radius * radius) lo = m else hi = m
        }
        return lo
    }

    private fun fmt(value: Double, decimals: Int) =
        "%.${decimals}f".format(java.util.Locale.ROOT, value)
}
