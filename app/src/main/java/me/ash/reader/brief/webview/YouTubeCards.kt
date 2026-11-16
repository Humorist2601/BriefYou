package me.ash.reader.brief.webview

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * YouTube videos placed in an article (an embedded player) are shown as a card: the video's
 * thumbnail in the same frame as other videos (the Videos page's corners and padding), with a
 * video play-button shape in the accent colour in the middle and "YouTube" on a chip under it. A tap opens
 * the video in the YouTube app (or the browser without it).
 *
 * YouTube no longer plays videos embedded in an app's page that doesn't say which app it is
 * ("Error 153"); the reading page is loaded without an address of its own, and giving it one
 * would change how every article loads (pictures from http addresses, a font from the phone).
 * The text-only reader (Read You's) already showed these as a picture with a play button.
 * Brief You.
 */
object YouTubeCards {

    /** A watch or short link to a YouTube video. */
    private val WATCH =
        Regex("^https?://(?:(?:www\\.|m\\.)?youtube\\.com/(?:watch|shorts/|live/)|youtu\\.be/)", RegexOption.IGNORE_CASE)

    fun isVideoLink(url: String): Boolean = WATCH.containsMatchIn(url)

    /** Opens a YouTube link in the YouTube app when there is one (Android picks it), else a browser. */
    fun open(context: Context, url: String): Boolean =
        runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
            .getOrDefault(false)

    fun css(corners: String, pad: String, width: String): String =
        """
/* Brief You: YouTube cards (see YouTubeCards) */
a.brief-yt {
    display: block !important;
    position: relative !important;
    overflow: hidden !important;
    aspect-ratio: 16 / 9;
    width: $width !important;
    max-width: $width !important;
    margin: 0.5em calc(0px - var(--text-margin) + $pad) !important;
    border-radius: $corners !important;
    background: var(--code-bg-color);
    text-decoration: none !important;
    -webkit-tap-highlight-color: transparent;
}
a.brief-yt > img {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    height: 100% !important;
    margin: 0 !important;
    border-radius: 0 !important;
    object-fit: cover;
    /* Fades in when it arrives, like the article's other pictures (see ImagePlaceholders). */
    opacity: 0;
    transition: opacity 0.45s ease;
}
a.brief-yt > img.brief-yt-loaded {
    opacity: 1;
}
@font-face {
    font-family: brief-ui;
    src: url("file:///android_res/font/google_sans_flex.ttf");
}
a.brief-yt > .brief-yt-mark {
    position: absolute;
    inset: 0;
}
/* The video play-button shape, in the accent colour. */
a.brief-yt .brief-yt-play {
    position: absolute;
    left: 50%;
    top: 50%;
    margin: -25px 0 0 -36px;
    width: 72px;
    height: 50px;
    border-radius: 15px;
    background: var(--link-text-color);
    box-shadow: 0 3px 12px rgba(0, 0, 0, 0.45);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.2s cubic-bezier(0.2, 0, 0, 1);
}
a.brief-yt:active .brief-yt-play {
    transform: scale(0.92);
}
a.brief-yt .brief-yt-play > svg {
    width: 28px;
    height: 28px;
    margin-left: 2px;
    fill: var(--code-bg-color);
}
/* "YouTube" under it (the button stays in the very middle), on a dark chip (it sits on the
   picture, whatever the theme), in the app's own font. */
a.brief-yt .brief-yt-chip {
    position: absolute;
    left: 50%;
    top: calc(50% + 32px);
    transform: translateX(-50%);
    white-space: nowrap;
    padding: 3px 10px;
    border-radius: 10px;
    background: rgba(17, 19, 24, 0.6);
    color: #ffffff !important;
    font: 600 13px/1.3 brief-ui, sans-serif;
    letter-spacing: 0;
}
"""

    /** Turns embedded YouTube players into cards (before the picture placeholders run). */
    val SCRIPT: String =
        """
// Brief You: YouTube cards (see YouTubeCards)
(function () {
    var ID = /(?:youtube(?:-nocookie)?\.com\/(?:embed|v)\/|youtube\.com\/shorts\/)([A-Za-z0-9_-]{6,})/i;
    var PLAY = '<svg viewBox="0 0 24 24"><path d="M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.7l-10-6.5A1 1 0 0 0 8 5.5z"/></svg>';
    document.querySelectorAll("article iframe[src]").forEach(function (frame) {
        var src = frame.getAttribute("src") || "";
        var m = ID.exec(src);
        if (!m) return;
        var id = m[1];
        // Where the article's player started, kept for YouTube.
        var start = /[?&](?:start|t)=(\d+)/.exec(src);
        var card = document.createElement("a");
        card.className = "brief-yt";
        card.href = "https://www.youtube.com/watch?v=" + id + (start ? "&t=" + start[1] + "s" : "");
        card.setAttribute("aria-label", "YouTube");
        var img = document.createElement("img");
        img.src = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        img.alt = "";
        // Offline: the card's plain colour instead of a broken picture.
        img.addEventListener("error", function () { img.style.visibility = "hidden"; });
        img.addEventListener("load", function () { img.classList.add("brief-yt-loaded"); });
        card.appendChild(img);
        card.insertAdjacentHTML("beforeend",
            '<span class="brief-yt-mark"><span class="brief-yt-play">' + PLAY + '</span><span class="brief-yt-chip">YouTube</span></span>');
        frame.parentNode.replaceChild(card, frame);
    });
})();
"""
}
