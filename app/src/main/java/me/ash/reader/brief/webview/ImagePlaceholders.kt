package me.ash.reader.brief.webview

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Placeholders for the pictures in an article, so the text doesn't jump while they load.
 *
 * Each picture that isn't there yet is put in a box of its own size (read from its width and
 * height; a wide 16:9 box when the page doesn't say). The box is tonal, with a turning Material 3
 * Expressive shape as the loading indicator. When the picture arrives the indicator fades away and
 * the picture fades in; a box that had to guess eases to the picture's real shape first. Small
 * pictures (icons, emoji) and pictures already loaded get no box.
 *
 * Added to the reading page's own styles and script by Brief You's web view, so Read You's
 * WebViewStyle and WebViewScript stay as they are.
 */
object ImagePlaceholders {

    /** Pictures narrower (or lower) than this many pixels get no box. */
    private const val MIN_SIZE = 120

    /** The styles, in the page's colours: the box's tonal colour and the indicator's. */
    fun css(boxColor: Int, indicatorColor: Int): String {
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
    animation: brief-indicator 1.4s cubic-bezier(0.4, 0, 0.2, 1) infinite;
    transition: opacity 0.2s ease;
}
.brief-box.brief-loaded {
    background-color: transparent;
}
.brief-box.brief-loaded::after {
    opacity: 0;
    animation: none;
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
.brief-box + small {
    display: inline-block;
    margin-top: var(--image-caption-margin) !important;
}
@keyframes brief-indicator {
    0% { transform: rotate(0deg) scale(0.86); }
    50% { transform: rotate(180deg) scale(1); }
    100% { transform: rotate(360deg) scale(0.86); }
}
"""
    }

    /** The script: boxes the pictures still loading, and reveals each one when it arrives. */
    val SCRIPT: String =
        """
// Brief You: picture placeholders (see ImagePlaceholders)
(function () {
    var MIN = $MIN_SIZE;
    document.querySelectorAll("article img").forEach(function (img) {
        if (img.closest("table") || img.closest(".brief-box")) return;
        // The size the page gives the picture: its width and height, or a pixel size in its style.
        var w = parseInt(img.getAttribute("width")) || (/px$/.test(img.style.width) ? parseInt(img.style.width) : 0);
        var h = parseInt(img.getAttribute("height")) || (/px$/.test(img.style.height) ? parseInt(img.style.height) : 0);
        if ((w && w < MIN) || (h && h < MIN / 2)) return;
        if (img.complete && img.naturalWidth > 0) return;

        var guess = !(w && h);
        var box = document.createElement("span");
        box.className = "brief-box";
        box.style.aspectRatio = guess ? "16 / 9" : w + " / " + h;
        // As wide as the picture says (the box's max-width keeps it on the page), or the page.
        box.style.width = w ? w + "px" : "100vw";
        img.parentNode.insertBefore(box, img);
        box.appendChild(img);

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
            }
            box.classList.add("brief-loaded");
        }

        img.addEventListener("load", reveal);
        img.addEventListener("error", function () {
            // A picture with its own fallback tries that next (see FeedContent); others go.
            if (!img.getAttribute("onerror")) unbox();
        });
        if (img.complete && img.naturalWidth > 0) reveal();
    });
})();
"""

    private fun cssColor(argb: Int): String {
        val a = (argb ushr 24 and 0xFF) / 255f
        return "rgba(${argb shr 16 and 0xFF}, ${argb shr 8 and 0xFF}, ${argb and 0xFF}, $a)"
    }

    /** A nine-sided cookie (an Expressive shape) as an SVG data address, in [argb]. */
    private fun svgDataUri(argb: Int): String {
        val points =
            (0 until 90).joinToString(" ") { i ->
                val t = 2 * PI * i / 90
                val r = 18 + 2.4 * cos(9 * t)
                "%.2f,%.2f".format(java.util.Locale.ROOT, 22 + r * cos(t), 22 + r * sin(t))
            }
        val hex = "%06X".format(argb and 0xFFFFFF)
        val svg =
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 44 44'>" +
                "<polygon points='$points' fill='%23$hex'/></svg>"
        return "data:image/svg+xml;utf8,$svg"
    }
}
