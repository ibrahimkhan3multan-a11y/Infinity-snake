package com.ncorti.kotlin.template.app.game3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Third-person chase camera: behind and above the snake, turning smoothly with it, following
 * climbs and drops, pulling in inside tunnels and never passing through walls or roofs.
 */
class Camera3D {
    var yaw = 0f
        private set
    val pos = FloatArray(3)
    private val target = FloatArray(3)
    private var dist = 5f
    private var height = 3.2f
    private var allowed = 1f
    private var inited = false
    private var firstFrame = true

    // Orthonormal basis for projection, updated by [update].
    val fwd = FloatArray(3)
    val right = FloatArray(3)
    val up = FloatArray(3)

    /** True while the camera is inside a tunnel (a roof overhead). */
    var inTunnel = false
        private set

    private val head = FloatArray(3)

    private fun angleDiff(a: Float, b: Float): Float {
        var d = b - a
        val tp = (2 * PI).toFloat()
        while (d > PI) d -= tp
        while (d < -PI) d += tp
        return d
    }

    fun reset(g: Game3D) {
        inited = false
        firstFrame = true
        update(g, 0.016f)
    }

    fun update(g: Game3D, dt: Float) {
        g.segmentPos(0, head)
        val wantYaw = g.headingAngle()
        if (!inited) {
            yaw = wantYaw
            target[0] = head[0]; target[1] = head[1]; target[2] = head[2]
            inited = true
        }
        yaw += angleDiff(yaw, wantYaw) * (1f - exp(-3.6f * dt))
        val kxz = 1f - exp(-14f * dt)
        val ky = 1f - exp(-4.5f * dt)
        target[0] += (head[0] - target[0]) * kxz
        target[2] += (head[2] - target[2]) * kxz
        target[1] += (head[1] - target[1]) * ky

        val map = g.map
        val hc = g.headCell
        inTunnel = map.hasUpper(map.cx(hc), map.cz(hc), map.cl(hc))
        val wantDist = if (inTunnel) 2.7f else 5.0f
        val wantHeight = if (inTunnel) 0.95f else 3.1f
        val kd = 1f - exp(-3.5f * dt)
        dist += (wantDist - dist) * kd
        height += (wantHeight - height) * kd

        val fx = cos(yaw)
        val fz = sin(yaw)
        val tx = target[0]
        val ty = target[1] + 0.35f
        val tz = target[2]
        val dx = tx - fx * dist
        val dy = ty + height
        val dz = tz - fz * dist

        // Keep the camera out of solid things: march from the snake toward the camera.
        var okT = 1f
        val steps = 14
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val px = tx + (dx - tx) * t
            val py = ty + (dy - ty) * t
            val pz = tz + (dz - tz) * t
            if (map.solidAt(px, py, pz) || map.solidAt(px, py - 0.18f, pz)) {
                okT = ((s - 1) / steps.toFloat()).coerceAtLeast(0.18f)
                break
            }
        }
        // Pull in quickly, relax out slowly, so it never snaps.
        if (firstFrame) {
            allowed = okT
            firstFrame = false
        } else {
            allowed += (okT - allowed) * (if (okT < allowed) 1f - exp(-24f * dt) else 1f - exp(-2.2f * dt))
        }
        pos[0] = tx + (dx - tx) * allowed
        pos[1] = ty + (dy - ty) * allowed
        pos[2] = tz + (dz - tz) * allowed

        // Look slightly ahead of the head.
        val lx = tx + fx * 1.5f
        val ly = ty - 0.05f
        val lz = tz + fz * 1.5f
        var vx = lx - pos[0]
        var vy = ly - pos[1]
        var vz = lz - pos[2]
        val len = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-4f)
        vx /= len; vy /= len; vz /= len
        fwd[0] = vx; fwd[1] = vy; fwd[2] = vz
        // right = fwd x worldUp, normalised
        var rx = vy * 0f - vz * 1f
        var ry = vz * 0f - vx * 0f
        var rz = vx * 1f - vy * 0f
        val rl = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-4f)
        rx /= rl; ry /= rl; rz /= rl
        right[0] = rx; right[1] = ry; right[2] = rz
        // up = right x fwd
        up[0] = ry * vz - rz * vy
        up[1] = rz * vx - rx * vz
        up[2] = rx * vy - ry * vx
    }
}
