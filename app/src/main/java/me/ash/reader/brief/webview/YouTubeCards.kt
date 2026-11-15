package me.ash.reader.brief.webview

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * YouTube videos placed in an article (an embedded player) are shown as a card: the video's
 * thumbnail in the same frame as other videos (the Videos page's corners and padding), the app's
 * play button in the middle and a "YouTube" chip in the corner, like the video card's. A tap opens
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
}
a.brief-yt > .brief-yt-play {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 64px;
    height: 64px;
    margin: -32px 0 0 -32px;
    border-radius: 50%;
    background: var(--link-text-color);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.2s cubic-bezier(0.2, 0, 0, 1);
}
a.brief-yt:active > .brief-yt-play {
    transform: scale(0.92);
}
a.brief-yt > .brief-yt-play > svg {
    width: 30px;
    height: 30px;
    margin-left: 3px;
    fill: var(--code-bg-color);
}
a.brief-yt > .brief-yt-chip {
    position: absolute;
    left: 10px;
    top: 10px;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 9px;
    border-radius: 10px;
    background: color-mix(in srgb, var(--code-bg-color) 85%, transparent);
    color: var(--text-color) !important;
    font: 600 12px/1.2 sans-serif;
    letter-spacing: 0;
}
a.brief-yt > .brief-yt-chip > svg {
    width: 14px;
    height: 14px;
    fill: currentColor;
}
"""

    /** Turns embedded YouTube players into cards (before the picture placeholders run). */
    val SCRIPT: String =
        """
// Brief You: YouTube cards (see YouTubeCards)
(function () {
    var ID = /(?:youtube(?:-nocookie)?\.com\/(?:embed|v)\/|youtube\.com\/shorts\/)([A-Za-z0-9_-]{6,})/i;
    var PLAY = '<svg viewBox="0 0 24 24"><path d="M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.7l-10-6.5A1 1 0 0 0 8 5.5z"/></svg>';
    var LOGO = '<svg viewBox="0 0 24 24"><path d="M10 15l5.2-3L10 9v6zm11.6-7.8c.2.9.4 2.2.4 3.8v2c0 1.6-.2 2.9-.4 3.8-.2.8-.9 1.4-1.7 1.6C18.2 18.8 12 19 12 19s-6.2-.2-7.9-.6c-.8-.2-1.5-.8-1.7-1.6C2.2 15.9 2 14.6 2 13v-2c0-1.6.2-2.9.4-3.8.2-.8.9-1.4 1.7-1.6C5.8 5.2 12 5 12 5s6.2.2 7.9.6c.8.2 1.5.8 1.7 1.6z"/></svg>';
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
        card.appendChild(img);
        card.insertAdjacentHTML("beforeend",
            '<span class="brief-yt-chip">' + LOGO + 'YouTube</span><span class="brief-yt-play">' + PLAY + '</span>');
        frame.parentNode.replaceChild(card, frame);
    });
})();
"""
}
