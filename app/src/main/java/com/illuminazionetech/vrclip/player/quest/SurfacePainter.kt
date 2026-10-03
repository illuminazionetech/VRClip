package com.illuminazionetech.vrclip.player.quest

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.view.Surface

/**
 * Clears a freshly created video panel surface to black. Until the decoder (or the 2D to 3D effect)
 * delivers its first frame, the compositor would otherwise show whatever the buffer held. Meta's
 * media samples do the same before handing the surface to ExoPlayer.
 */
internal object SurfacePainter {

    fun paintBlack(surface: Surface) {
        runCatching { paint(surface) }
    }

    private fun paint(surface: Surface) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes =
            intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_NONE,
            )
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) return
        val config = configs[0]?.takeIf { count[0] > 0 } ?: return

        // Whatever this thread had current is put back afterwards.
        val previousContext = EGL14.eglGetCurrentContext()
        val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)

        val context =
            EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        if (context == EGL14.EGL_NO_CONTEXT) return
        val window =
            EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        try {
            if (
                window != EGL14.EGL_NO_SURFACE &&
                    EGL14.eglMakeCurrent(display, window, window, context)
            ) {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                EGL14.eglSwapBuffers(display, window)
            }
        } finally {
            EGL14.eglMakeCurrent(display, previousDraw, previousRead, previousContext)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window)
            EGL14.eglDestroyContext(display, context)
        }
    }
}
