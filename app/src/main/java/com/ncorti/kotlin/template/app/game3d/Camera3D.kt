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
        val fx = cos(yaw)
        val fz = sin(yaw)
        val tx = target[0]
        val ty = target[1] + 0.35f

        // Pick the most pleasant camera spot that sees the head without a wall or roof in between:
        // normally behind and above; steeper and closer where space is tight; low inside tunnels.
        val cands = if (inTunnel) TUNNEL_VIEWS else OPEN_VIEWS
        var wantDist = cands[cands.size - 2]
        var wantHeight = cands[cands.size - 1]
        var i = 0
        while (i < cands.size) {
            val d = cands[i]
            val hgt = cands[i + 1]
            if (clearView(map, tx, ty, target[2], tx - fx * d, ty + hgt, target[2] - fz * d)) {
                wantDist = d
                wantHeight = hgt
                break
            }
            i += 2
        }
        val kd = 1f - exp(-(if (wantDist < dist) 9f else 2.5f) * dt)
        dist += (wantDist - dist) * kd
        val kh = 1f - exp(-(if (wantHeight > height && wantDist < dist) 9f else 3f) * dt)
        height += (wantHeight - height) * kh

        val tz = target[2]
        val dx = tx - fx * dist
        val dy = ty + height
        val dz = tz - fz * dist

        // Safety: march from the snake toward the camera and stop short of anything solid.
        var okT = 1f
        val steps = 14
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val px = tx + (dx - tx) * t
            val py = ty + (dy - ty) * t
            val pz = tz + (dz - tz) * t
            if (map.solidAt(px, py, pz) || map.solidAt(px, py - 0.18f, pz) || map.solidAt(px, py + 0.16f, pz)) {
                okT = ((s - 1) / steps.toFloat()).coerceAtLeast(0.04f)
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

    private fun clearView(map: GameMap, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Boolean {
        val n = 14
        for (s in 1..n) {
            val t = s / n.toFloat()
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            val pz = az + (bz - az) * t
            if (map.solidAt(px, py, pz) || map.solidAt(px, py - 0.18f, pz) || map.solidAt(px, py + 0.16f, pz)) return false
        }
        return true
    }

    companion object {
        // Pairs of (distance behind, height above), most preferred first.
        private val OPEN_VIEWS = floatArrayOf(5.0f, 3.1f, 4.2f, 3.6f, 3.2f, 4.0f, 2.4f, 4.5f, 1.5f, 4.8f, 0.8f, 5.0f)
        private val TUNNEL_VIEWS = floatArrayOf(2.7f, 0.95f, 2.4f, 0.7f, 2.2f, 0.55f, 2.0f, 0.8f, 2.0f, 0.4f, 1.8f, 0.3f, 1.4f, 0.7f, 1.4f, 0.3f, 1.2f, 0.2f, 0.9f, 0.6f, 0.9f, 0.2f, 0.7f, 0.15f, 0.5f, 0.5f)
    }
}
