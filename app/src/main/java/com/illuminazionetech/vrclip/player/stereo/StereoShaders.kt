package com.illuminazionetech.vrclip.player.stereo

/**
 * GLSL shared by the 2D to 3D effect (live on Meta Quest and for permanent conversions) and by the
 * phone renderer, so every output draws the views the same way.
 *
 * Depth convention: 0 is the farthest point of the scene, 1 the nearest; the convergence depth
 * stays on the screen plane. Seen from a camera moved by `offset` (in units of the half parallax
 * range, x to the right, y up), a point at depth d shifts on screen by -(d - convergence) * offset:
 * near points move against the camera, far points with it. Each output pixel searches backwards for
 * the source point that lands on it.
 */
internal object StereoShaders {

    const val VERTEX =
        """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
        }
        """

    /**
     * The frame at the depth model's input size, nine bilinear taps per pixel so even a 4K frame
     * reduced tenfold does not alias. Kept in GL orientation: it is also the color guide for
     * [UPSAMPLE], and the rows are reversed when it is read back for the model.
     */
    const val DOWNSCALE =
        """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform vec2 uStep;
        varying vec2 vTexSamplingCoord;
        void main() {
            vec3 sum = vec3(0.0);
            for (int j = -1; j <= 1; j++) {
                for (int i = -1; i <= 1; i++) {
                    sum += texture2D(uTexSampler, vTexSamplingCoord + vec2(float(i), float(j)) * uStep).rgb;
                }
            }
            gl_FragColor = vec4(sum / 9.0, 1.0);
        }
        """

    /**
     * Joint bilateral upsampling of the model's depth to the frame's resolution: each pixel
     * averages the nearby depth samples, weighted by distance and by how close their color is to
     * its own color in the current frame. Depth edges snap to the picture's edges (also when the
     * depth map is a few frames old, as in live playback), instead of the blurred, misplaced edge a
     * plain bilinear upsampling leaves around every object. [uRamp] fades the depth in from the
     * screen plane when it first appears.
     */
    const val UPSAMPLE =
        """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform sampler2D uGuide;
        uniform sampler2D uLowDepth;
        uniform vec2 uLowSize;
        uniform float uRamp;
        uniform float uConvergence;
        varying vec2 vTexSamplingCoord;
        void main() {
            vec2 uv = vTexSamplingCoord;
            vec3 center = texture2D(uTexSampler, uv).rgb;
            vec2 p = uv * uLowSize - 0.5;
            vec2 base = floor(p);
            vec2 halfTexel = 0.5 / uLowSize;
            float sumW = 0.0;
            float sumD = 0.0;
            for (int j = -1; j <= 2; j++) {
                for (int i = -1; i <= 2; i++) {
                    vec2 q = base + vec2(float(i), float(j));
                    vec2 quv = clamp((q + 0.5) / uLowSize, halfTexel, 1.0 - halfTexel);
                    // Depth rows were uploaded top first.
                    float d = texture2D(uLowDepth, vec2(quv.x, 1.0 - quv.y)).r;
                    vec3 dc = center - texture2D(uGuide, quv).rgb;
                    vec2 dp = p - q;
                    float spatial = exp(-0.5 * dot(dp, dp));
                    float w = spatial * (exp(-24.0 * dot(dc, dc)) + 1e-4);
                    sumW += w;
                    sumD += w * d;
                }
            }
            float depth = sumD / sumW;
            depth = uConvergence + (depth - uConvergence) * uRamp;
            gl_FragColor = vec4(depth, depth, depth, 1.0);
        }
        """

    /**
     * `vec4 synthesize(vec2 uv, vec2 offset)`: the color the camera moved by `offset` sees at `uv`,
     * with alpha 0 where that point comes from outside the frame. Needs `colorAt(vec2)`,
     * `depthAt(vec2)` and the uniforms `uHalfRange` (half the parallax range, as a fraction of the
     * width), `uConvergence`, `uAspect` (width / height) and `uTexel` (1 / depth size).
     *
     * The search walks from the nearest possible source point to the farthest in [steps] samples:
     * the first one past the crossing is the visible surface (nearer points hide farther ones), and
     * [refine] halvings place it precisely. Where the hit sits on a depth step the pixel was hidden
     * in the original view (a disocclusion), so the color comes from the far side of the step: the
     * background stretches into the gap instead of the object's edge.
     */
    fun synthesis(steps: Int, refine: Int) =
        """
        uniform float uHalfRange;
        uniform float uConvergence;
        uniform float uAspect;
        uniform vec2 uTexel;

        float hitFunction(vec2 uv, vec2 k, float t) {
            return t - (depthAt(uv + k * t) - uConvergence);
        }

        vec4 synthesize(vec2 uv, vec2 offset) {
            vec2 k = offset * uHalfRange;
            k.y *= uAspect;
            if (dot(k, k) < 1e-12) return vec4(colorAt(uv), 1.0);
            float tNear = 1.0 - uConvergence;
            float tFar = -uConvergence;
            float lo = tFar;
            float hi = tNear;
            float previous = tNear;
            for (int i = 1; i <= $steps; i++) {
                float t = mix(tNear, tFar, float(i) / float($steps));
                if (hitFunction(uv, k, t) <= 0.0) {
                    lo = t;
                    hi = previous;
                    break;
                }
                previous = t;
            }
            for (int r = 0; r < $refine; r++) {
                float mid = 0.5 * (lo + hi);
                if (hitFunction(uv, k, mid) <= 0.0) lo = mid; else hi = mid;
            }
            vec2 s = uv + k * (0.5 * (lo + hi));
            vec2 side = normalize(k) * uTexel * 1.5;
            float ahead = depthAt(s + side);
            float behind = depthAt(s - side);
            if (abs(ahead - behind) > 0.08) {
                s += (ahead < behind ? side : -side) * 2.0;
            }
            float inside = step(0.0, s.x) * step(s.x, 1.0) * step(0.0, s.y) * step(s.y, 1.0);
            return vec4(colorAt(s), inside);
        }
        """

    /** Side-by-side output: left eye in the left half, right eye in the right half. */
    fun sideBySide(steps: Int, refine: Int) =
        """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform sampler2D uDepth;
        varying vec2 vTexSamplingCoord;

        vec3 colorAt(vec2 uv) {
            return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)).rgb;
        }

        float depthAt(vec2 uv) {
            return texture2D(uDepth, clamp(uv, 0.0, 1.0)).r;
        }
        """ +
            synthesis(steps, refine) +
            """
        void main() {
            vec2 uv = vTexSamplingCoord;
            bool left = uv.x < 0.5;
            vec2 eyeUv = vec2(left ? uv.x * 2.0 : uv.x * 2.0 - 1.0, uv.y);
            vec4 color = synthesize(eyeUv, vec2(left ? -1.0 : 1.0, 0.0));
            // Points from outside the frame stay black: a floating window at the edges.
            gl_FragColor = vec4(color.rgb * color.a, 1.0);
        }
        """

    /** The picture in the top half and its depth in the bottom half, for the phone renderer. */
    const val COLOR_AND_DEPTH =
        """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform sampler2D uDepth;
        varying vec2 vTexSamplingCoord;
        void main() {
            vec2 uv = vTexSamplingCoord;
            if (uv.y >= 0.5) {
                gl_FragColor = vec4(texture2D(uTexSampler, vec2(uv.x, uv.y * 2.0 - 1.0)).rgb, 1.0);
            } else {
                float d = texture2D(uDepth, vec2(uv.x, uv.y * 2.0)).r;
                gl_FragColor = vec4(d, d, d, 1.0);
            }
        }
        """
}
