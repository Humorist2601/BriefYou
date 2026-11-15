package me.ash.reader.brief.webview

import org.json.JSONObject

/**
 * Videos and audio inside an article's text (`<video>` and `<audio>` tags) play in the app's own
 * player instead of the web view's grey one: each becomes a tile in the app's colours with a play
 * button (a video keeps its poster picture). Tapping it plays in the app: a video opens full
 * screen, audio opens the player's sheet. Embedded players (YouTube's and the like) stay as they
 * are. Brief You.
 */
object PageMedia {

    /** The name the page calls the app by. */
    const val NAME = "BriefMedia"

    /** Turns the page's video and audio tags into the app's tiles; [videoLabel] and [audioLabel] are read out for them. */
    fun script(videoLabel: String, audioLabel: String): String =
        """
;(function () {
  if (!window.$NAME) return;
  var VIDEO = ${JSONObject.quote(videoLabel)}, AUDIO = ${JSONObject.quote(audioLabel)};
  var PLAY = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.6v12.8a1 1 0 0 0 1.53.85l10.2-6.4a1 1 0 0 0 0-1.7L9.53 4.75A1 1 0 0 0 8 5.6z"/></svg>';
  function abs(u) { try { return new URL(u, document.baseURI).href; } catch (e) { return null; } }
  function srcOf(el) {
    var u = el.getAttribute('src');
    if (!u) { var s = el.querySelector('source[src]'); if (s) u = s.getAttribute('src'); }
    if (!u || /^(blob|data|javascript):/i.test(u)) return null;
    return abs(u);
  }
  document.querySelectorAll('video, audio').forEach(function (el) {
    var url = srcOf(el);
    if (!url) return;
    var video = el.tagName === 'VIDEO';
    var poster = video && el.getAttribute('poster') ? abs(el.getAttribute('poster')) : null;
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'brief-media ' + (video ? 'brief-media-video' : 'brief-media-audio');
    b.setAttribute('aria-label', video ? VIDEO : AUDIO);
    if (poster) b.style.backgroundImage = 'url("' + poster.replace(/"/g, '%22') + '")';
    b.innerHTML = '<span class="brief-media-play">' + PLAY + '</span>' +
      (video ? '' : '<span class="brief-media-label"></span>');
    if (!video) b.lastChild.textContent = AUDIO;
    b.addEventListener('click', function (e) {
      e.preventDefault(); e.stopPropagation();
      try { $NAME.play(url, video ? 'video' : 'audio', poster || ''); } catch (x) {}
    });
    try { el.pause(); } catch (x) {}
    el.replaceWith(b);
  });
})();
"""

    /**
     * The tiles' look: a video is a 16:9 tile with the video corners and padding (see
     * [MediaStyle]), audio a rounded row; both have the accent-coloured play button.
     */
    fun css(corners: String, pad: String, box: Int, onBox: Int, accent: Int, onAccent: Int): String =
        """
/* Brief You: videos and audio in the text (see PageMedia) */
.brief-media {
    display: flex; align-items: center; gap: 14px;
    border: 0; padding: 0; font: inherit; cursor: pointer;
    background-color: ${color(box)}; color: ${color(onBox)};
    background-size: cover; background-position: center;
    -webkit-tap-highlight-color: transparent;
}
.brief-media-video {
    justify-content: center;
    width: calc(100% + 2 * var(--text-margin) - 2 * $pad) !important;
    margin: 12px calc(0px - var(--text-margin) + $pad) !important;
    aspect-ratio: 16 / 9;
    border-radius: $corners;
}
.brief-media-audio {
    width: 100%; margin: 12px 0; padding: 10px 20px 10px 10px;
    border-radius: 28px; text-align: start;
}
.brief-media-play {
    display: flex; align-items: center; justify-content: center; flex: none;
    width: 64px; height: 64px; border-radius: 22px;
    background: ${color(accent)}; color: ${color(onAccent)};
    transition: transform .25s cubic-bezier(.34, 1.56, .64, 1), border-radius .25s cubic-bezier(.34, 1.56, .64, 1);
}
.brief-media-audio .brief-media-play { width: 52px; height: 52px; border-radius: 18px; }
.brief-media:active .brief-media-play { transform: scale(.92); border-radius: 32px; }
.brief-media-play svg { width: 28px; height: 28px; fill: currentColor; }
.brief-media-label { font-weight: 600; }
"""

    private fun color(argb: Int): String {
        val a = (argb ushr 24) / 255f
        return "rgba(${argb shr 16 and 0xFF}, ${argb shr 8 and 0xFF}, ${argb and 0xFF}, $a)"
    }
}
