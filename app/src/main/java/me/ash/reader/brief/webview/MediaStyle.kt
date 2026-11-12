package me.ash.reader.brief.webview

import me.ash.reader.infrastructure.preference.SAME_AS_IMAGES

/**
 * The reading page's picture and video settings that Read You's own web view style doesn't know:
 * Maximize for pictures, and the Videos page (corners, padding, maximize). Added after
 * WebViewStyle's styles by Brief You's web view, so Read You's file stays as it is.
 *
 * - Maximize (pictures): a picture narrower than the text, which would be centred, runs the full
 *   width instead (icons and emoji stay in the line).
 * - Videos: corners and padding follow the Images settings until set apart ([SAME_AS_IMAGES]);
 *   Maximize makes embedded players as wide as the text allows, in a 16:9 frame.
 */
object MediaStyle {

    fun css(
        imageMaximize: Boolean,
        videoCorners: Int,
        videoPadding: Int,
        videoMaximize: Boolean,
    ): String {
        val corners = if (videoCorners == SAME_AS_IMAGES) "var(--img-border-radius)" else "${videoCorners}px"
        val pad = if (videoPadding == SAME_AS_IMAGES) "var(--img-margin)" else "${videoPadding}px"
        val width = "calc(100% + 2 * var(--text-margin) - 2 * $pad)"
        return buildString {
            append(
                """
/* Brief You: videos (the Videos page) */
iframe, video, embed, object {
    margin-left: calc(0px - var(--text-margin) + $pad) !important;
    margin-right: calc(0px - var(--text-margin) + $pad) !important;
    max-width: $width !important;
    border-radius: $corners !important;
}
"""
            )
            if (videoMaximize) {
                append(
                    """
iframe, video {
    width: $width !important;
    height: auto !important;
    aspect-ratio: 16 / 9;
}
"""
                )
            }
            if (imageMaximize) {
                append(
                    """
/* Brief You: Maximize for pictures */
img.brief-center, .brief-box.brief-center {
    width: calc(100% + 2 * var(--text-margin) - 2 * var(--img-margin)) !important;
    margin-left: calc(0px - var(--text-margin) + var(--img-margin)) !important;
    margin-right: calc(0px - var(--text-margin) + var(--img-margin)) !important;
}
"""
                )
            }
        }
    }
}
