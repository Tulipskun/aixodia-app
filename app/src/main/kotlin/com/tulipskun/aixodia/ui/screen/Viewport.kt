package com.tulipskun.aixodia.ui.screen

import kotlin.math.max
import kotlin.math.min

/**
 * Where the picture sits on the phone: fitted to the screen, zoomed, panned.
 *
 * This is out of the view on purpose. Every defect the screen pane has had in
 * its zoom was in this arithmetic and none of them needed a phone to see: a pan
 * worked out from the *running* pan and zoom, so each event fed the next and the
 * picture drifted, then snapped back when it reached an edge; and a redial
 * throwing the reader's zoom away a second after they pinched into it. Pure
 * arithmetic in a class of its own can be pinned by tests; the same arithmetic
 * living in an `onTouchEvent` cannot.
 *
 * It also owns the one rectangle everything else agrees on — drawing, turning a
 * touch into a picture pixel, and how far the picture may slide — because two of
 * those disagreeing is another way to make an arrow land somewhere the finger is
 * not.
 */
class Viewport {

    /** 1 means the picture exactly fits the view. */
    var zoom = 1f
        private set

    /** Offset of the picture within the view, in view pixels, after the fit. */
    var panX = 0f
        private set
    var panY = 0f
        private set

    // The state the current pinch began in. A pinch is always measured from
    // here, never from the event before it.
    private var baseZoom = 1f
    private var basePanX = 0f
    private var basePanY = 0f
    private var anchorX = 0f
    private var anchorY = 0f

    /** The zoom the current pinch began from. */
    val pinchBase: Float get() = baseZoom

    /** True once the picture is bigger than the view, so it has somewhere to pan. */
    val isZoomed: Boolean get() = zoom > 1.001f

    /** Remembers where a pinch begins: its point, the zoom and the pan then. */
    fun beginPinch(anchorX: Float, anchorY: Float) {
        this.anchorX = anchorX
        this.anchorY = anchorY
        baseZoom = zoom
        basePanX = panX
        basePanY = panY
    }

    /**
     * One step of a pinch.
     *
     * [wanted] is the zoom the fingers are asking for, and [slideX]/[slideY] how
     * far the pair has travelled since they landed. Both are measured from the
     * start of the pinch by the caller, which is what makes this a function of
     * the gesture rather than of the last event.
     */
    fun pinch(
        wanted: Float,
        slideX: Float,
        slideY: Float,
        viewWidth: Int,
        viewHeight: Int,
        pictureWidth: Int,
        pictureHeight: Int,
    ) {
        val fit = fitOf(viewWidth, viewHeight, pictureWidth, pictureHeight) ?: return
        val zoom = wanted.coerceIn(MIN_ZOOM, MAX_ZOOM)
        this.zoom = zoom
        panX = Pinch.pan(
            anchor = anchorX,
            centre = viewWidth / 2f,
            drawnAtBase = pictureWidth * fit * baseZoom,
            basePan = basePanX + slideX,
            baseZoom = baseZoom,
            zoom = zoom,
        )
        panY = Pinch.pan(
            anchor = anchorY,
            centre = viewHeight / 2f,
            drawnAtBase = pictureHeight * fit * baseZoom,
            basePan = basePanY + slideY,
            baseZoom = baseZoom,
            zoom = zoom,
        )
        clamp(viewWidth, viewHeight, pictureWidth, pictureHeight)
    }

    /** The whole desktop, small. */
    fun fit() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    /**
     * One picture pixel per screen pixel, centred.
     *
     * A fitted 1280-wide desktop on a 900-wide phone is at 70%, where small text
     * is unreadable; this is the reader saying "show it real" and panning around.
     */
    fun actualSize(viewWidth: Int, viewHeight: Int, pictureWidth: Int, pictureHeight: Int) {
        val fit = fitOf(viewWidth, viewHeight, pictureWidth, pictureHeight) ?: return
        zoom = (1f / fit).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = 0f
        panY = 0f
        clamp(viewWidth, viewHeight, pictureWidth, pictureHeight)
    }

    /** True when the picture is as big as its own pixels allow. */
    fun isActualSize(viewWidth: Int, viewHeight: Int, pictureWidth: Int, pictureHeight: Int): Boolean {
        val fit = fitOf(viewWidth, viewHeight, pictureWidth, pictureHeight) ?: return false
        return zoom >= (1f / fit).coerceIn(MIN_ZOOM, MAX_ZOOM) - 0.01f
    }

    /**
     * The rectangle the picture is drawn in, in view pixels.
     *
     * Everything that needs to know where the picture is asks this: the drawing,
     * the arrow's place, and the touch that becomes a pointer. They cannot
     * disagree, because there is one answer.
     */
    fun box(viewWidth: Int, viewHeight: Int, pictureWidth: Int, pictureHeight: Int): FloatBox {
        val fit = fitOf(viewWidth, viewHeight, pictureWidth, pictureHeight)
            ?: return FloatBox(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val scale = fit * zoom
        val drawnWidth = pictureWidth * scale
        val drawnHeight = pictureHeight * scale
        val left = (viewWidth - drawnWidth) / 2f + panX
        val top = (viewHeight - drawnHeight) / 2f + panY
        return FloatBox(left, top, drawnWidth, drawnHeight)
    }

    /**
     * Slides the picture so the pointer stays in the middle of the view.
     *
     * This is what the viewer this one imitates does, measured on the phone
     * against this engine: a drag that took the pointer 141 picture pixels
     * rightward panned the picture 281 screen pixels and left the arrow exactly
     * where it was, at the view's centre, 1 pixel off. The arrow only moves once
     * the picture has nowhere left to slide — at its edge — and then it is the
     * arrow that travels and the picture that stays.
     *
     * The pan that puts the arrow in the middle is `drawnWidth / 2 - x * scale`
     * and no more than the slack allows, so a picture with nowhere to slide
     * leaves the pan at zero and the arrow moves on its own.
     */
    fun followPointer(
        pointerX: Float,
        pointerY: Float,
        viewWidth: Int,
        viewHeight: Int,
        pictureWidth: Int,
        pictureHeight: Int,
    ) {
        val fit = fitOf(viewWidth, viewHeight, pictureWidth, pictureHeight) ?: return
        val scale = fit * zoom
        panX = pictureWidth * scale / 2f - pointerX * scale
        panY = pictureHeight * scale / 2f - pointerY * scale
        clamp(viewWidth, viewHeight, pictureWidth, pictureHeight)
    }

    /**
     * The picture pixel drawn at a point of the view.
     *
     * The inverse of [box], and clamped to the picture: asked for the middle of
     * the pane it answers with whatever is drawn there, which is how the arrow
     * can be pulled to the middle without moving the picture at all.
     */
    fun pictureAt(
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int,
        pictureWidth: Int,
        pictureHeight: Int,
    ): Pair<Float, Float>? {
        val box = box(viewWidth, viewHeight, pictureWidth, pictureHeight)
        if (box.width <= 0f || box.height <= 0f) return null
        val px = (x - box.left) * pictureWidth / box.width
        val py = (y - box.top) * pictureHeight / box.height
        return px.coerceIn(0f, pictureWidth - 1f) to py.coerceIn(0f, pictureHeight - 1f)
    }

    /** Keeps the picture from being dragged off into empty space. */
    fun clamp(viewWidth: Int, viewHeight: Int, pictureWidth: Int, pictureHeight: Int) {
        val box = box(viewWidth, viewHeight, pictureWidth, pictureHeight)
        // How far it may slide: half the overflow each way, so every part of a
        // zoomed picture can be brought to the middle of the view and none of
        // it can be dragged away into empty space.
        val slackX = max(0f, (box.width - viewWidth) / 2f)
        val slackY = max(0f, (box.height - viewHeight) / 2f)
        panX = panX.coerceIn(-slackX, slackX)
        panY = panY.coerceIn(-slackY, slackY)
    }

    private fun fitOf(viewWidth: Int, viewHeight: Int, pictureWidth: Int, pictureHeight: Int): Float? {
        if (viewWidth <= 0 || viewHeight <= 0 || pictureWidth <= 0 || pictureHeight <= 0) return null
        return min(viewWidth.toFloat() / pictureWidth, viewHeight.toFloat() / pictureHeight)
    }

    companion object {
        /** Zoomed out to the whole desktop; it never goes smaller than that. */
        const val MIN_ZOOM = 1f

        /** Four times the fit: enough to read small type, not so far it means nothing. */
        const val MAX_ZOOM = 4f
    }
}

/** A rectangle in view pixels: left and top, with its size. */
data class FloatBox(val left: Float, val top: Float, val width: Float, val height: Float)
