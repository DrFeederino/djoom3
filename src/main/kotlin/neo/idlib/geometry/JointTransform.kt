package neo.idlib.geometry

import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Quat.idQuat
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.util.*

/**
 *
 */
class JointTransform {
    /*
     ===============================================================================

     Joint Quaternion

     ===============================================================================
     */
    class idJointQuat {
        val q: idQuat
        val t: idVec3

        constructor() {
            q = idQuat()
            t = idVec3()
        }

        constructor(quat: idJointQuat) {
            q = idQuat(quat.q)
            t = idVec3(quat.t)
        }
    }

    /*
     ===============================================================================

     Joint Matrix

     idMat3 m;
     idVec3 t;

     m[0][0], m[1][0], m[2][0], t[0]
     m[0][1], m[1][1], m[2][1], t[1]
     m[0][2], m[1][2], m[2][2], t[2]

     ===============================================================================
     */
    class idJointMat {
        private val DBG_count = DBG_counter++
        private val mat: FloatArray = FloatArray(3 * 4)

        constructor() {
            val a = 0
        }

        constructor(mat: FloatArray) {
            System.arraycopy(mat, 0, this.mat, 0, this.mat.size)
        }

        constructor(mat: idJointMat) : this(mat.mat)

        fun SetRotation(m: idMat3) {
            // NOTE: idMat3 is transposed because it is column-major
            mat[0 * 4 + 0] = m.oGet(0).oGet(0)
            mat[0 * 4 + 1] = m.oGet(1).oGet(0)
            mat[0 * 4 + 2] = m.oGet(2).oGet(0)
            mat[1 * 4 + 0] = m.oGet(0).oGet(1)
            mat[1 * 4 + 1] = m.oGet(1).oGet(1)
            mat[1 * 4 + 2] = m.oGet(2).oGet(1)
            mat[2 * 4 + 0] = m.oGet(0).oGet(2)
            mat[2 * 4 + 1] = m.oGet(1).oGet(2)
            mat[2 * 4 + 2] = m.oGet(2).oGet(2)
        }

        fun SetTranslation(t: idVec3?) {
            mat[0 * 4 + 3] = t.oGet(0)
            mat[1 * 4 + 3] = t.oGet(1)
            mat[2 * 4 + 3] = t.oGet(2)
        }

        // only rotate
        operator fun times(v: idVec3): idVec3 {
            return idVec3(
                mat[0 * 4 + 0] * v.oGet(0) + mat[0 * 4 + 1] * v.oGet(1) + mat[0 * 4 + 2] * v.oGet(2),
                mat[1 * 4 + 0] * v.oGet(0) + mat[1 * 4 + 1] * v.oGet(1) + mat[1 * 4 + 2] * v.oGet(2),
                mat[2 * 4 + 0] * v.oGet(0) + mat[2 * 4 + 1] * v.oGet(1) + mat[2 * 4 + 2] * v.oGet(2)
            )
        }

        // rotate and translate
        operator fun times(v: idVec4): idVec3 {
            return idVec3(
                mat[0 * 4 + 0] * v.oGet(0) + mat[0 * 4 + 1] * v.oGet(1) + mat[0 * 4 + 2] * v.oGet(2) + mat[0 * 4 + 3] * v.oGet(
                    3
                ),
                mat[1 * 4 + 0] * v.oGet(0) + mat[1 * 4 + 1] * v.oGet(1) + mat[1 * 4 + 2] * v.oGet(2) + mat[1 * 4 + 3] * v.oGet(
                    3
                ),
                mat[2 * 4 + 0] * v.oGet(0) + mat[2 * 4 + 1] * v.oGet(1) + mat[2 * 4 + 2] * v.oGet(2) + mat[2 * 4 + 3] * v.oGet(
                    3
                )
            )
        }

        // transform
        fun timesAssign(a: idJointMat): idJointMat {
            val dst = FloatArray(3)
            dst[0] =
                mat[0 * 4 + 0] * a.mat[0 * 4 + 0] + mat[1 * 4 + 0] * a.mat[0 * 4 + 1] + mat[2 * 4 + 0] * a.mat[0 * 4 + 2]
            dst[1] =
                mat[0 * 4 + 0] * a.mat[1 * 4 + 0] + mat[1 * 4 + 0] * a.mat[1 * 4 + 1] + mat[2 * 4 + 0] * a.mat[1 * 4 + 2]
            dst[2] =
                mat[0 * 4 + 0] * a.mat[2 * 4 + 0] + mat[1 * 4 + 0] * a.mat[2 * 4 + 1] + mat[2 * 4 + 0] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 0] = dst[0]
            mat[1 * 4 + 0] = dst[1]
            mat[2 * 4 + 0] = dst[2]
            dst[0] =
                mat[0 * 4 + 1] * a.mat[0 * 4 + 0] + mat[1 * 4 + 1] * a.mat[0 * 4 + 1] + mat[2 * 4 + 1] * a.mat[0 * 4 + 2]
            dst[1] =
                mat[0 * 4 + 1] * a.mat[1 * 4 + 0] + mat[1 * 4 + 1] * a.mat[1 * 4 + 1] + mat[2 * 4 + 1] * a.mat[1 * 4 + 2]
            dst[2] =
                mat[0 * 4 + 1] * a.mat[2 * 4 + 0] + mat[1 * 4 + 1] * a.mat[2 * 4 + 1] + mat[2 * 4 + 1] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 1] = dst[0]
            mat[1 * 4 + 1] = dst[1]
            mat[2 * 4 + 1] = dst[2]
            dst[0] =
                mat[0 * 4 + 2] * a.mat[0 * 4 + 0] + mat[1 * 4 + 2] * a.mat[0 * 4 + 1] + mat[2 * 4 + 2] * a.mat[0 * 4 + 2]
            dst[1] =
                mat[0 * 4 + 2] * a.mat[1 * 4 + 0] + mat[1 * 4 + 2] * a.mat[1 * 4 + 1] + mat[2 * 4 + 2] * a.mat[1 * 4 + 2]
            dst[2] =
                mat[0 * 4 + 2] * a.mat[2 * 4 + 0] + mat[1 * 4 + 2] * a.mat[2 * 4 + 1] + mat[2 * 4 + 2] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 2] = dst[0]
            mat[1 * 4 + 2] = dst[1]
            mat[2 * 4 + 2] = dst[2]
            dst[0] =
                mat[0 * 4 + 3] * a.mat[0 * 4 + 0] + mat[1 * 4 + 3] * a.mat[0 * 4 + 1] + mat[2 * 4 + 3] * a.mat[0 * 4 + 2]
            dst[1] =
                mat[0 * 4 + 3] * a.mat[1 * 4 + 0] + mat[1 * 4 + 3] * a.mat[1 * 4 + 1] + mat[2 * 4 + 3] * a.mat[1 * 4 + 2]
            dst[2] =
                mat[0 * 4 + 3] * a.mat[2 * 4 + 0] + mat[1 * 4 + 3] * a.mat[2 * 4 + 1] + mat[2 * 4 + 3] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 3] = dst[0]
            mat[1 * 4 + 3] = dst[1]
            mat[2 * 4 + 3] = dst[2]
            mat[0 * 4 + 3] += a.mat[0 * 4 + 3]
            mat[1 * 4 + 3] += a.mat[1 * 4 + 3]
            mat[2 * 4 + 3] += a.mat[2 * 4 + 3]
            return this
        }

        // untransform
        fun oDivSet(a: idJointMat?): idJointMat? {
            val dst = FloatArray(3)
            mat[0 * 4 + 3] -= a.mat[0 * 4 + 3]
            mat[1 * 4 + 3] -= a.mat[1 * 4 + 3]
            mat[2 * 4 + 3] -= a.mat[2 * 4 + 3]
            dst[0] =
                mat[0 * 4 + 0] * a.mat[0 * 4 + 0] + mat[1 * 4 + 0] * a.mat[1 * 4 + 0] + mat[2 * 4 + 0] * a.mat[2 * 4 + 0]
            dst[1] =
                mat[0 * 4 + 0] * a.mat[0 * 4 + 1] + mat[1 * 4 + 0] * a.mat[1 * 4 + 1] + mat[2 * 4 + 0] * a.mat[2 * 4 + 1]
            dst[2] =
                mat[0 * 4 + 0] * a.mat[0 * 4 + 2] + mat[1 * 4 + 0] * a.mat[1 * 4 + 2] + mat[2 * 4 + 0] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 0] = dst[0]
            mat[1 * 4 + 0] = dst[1]
            mat[2 * 4 + 0] = dst[2]
            dst[0] =
                mat[0 * 4 + 1] * a.mat[0 * 4 + 0] + mat[1 * 4 + 1] * a.mat[1 * 4 + 0] + mat[2 * 4 + 1] * a.mat[2 * 4 + 0]
            dst[1] =
                mat[0 * 4 + 1] * a.mat[0 * 4 + 1] + mat[1 * 4 + 1] * a.mat[1 * 4 + 1] + mat[2 * 4 + 1] * a.mat[2 * 4 + 1]
            dst[2] =
                mat[0 * 4 + 1] * a.mat[0 * 4 + 2] + mat[1 * 4 + 1] * a.mat[1 * 4 + 2] + mat[2 * 4 + 1] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 1] = dst[0]
            mat[1 * 4 + 1] = dst[1]
            mat[2 * 4 + 1] = dst[2]
            dst[0] =
                mat[0 * 4 + 2] * a.mat[0 * 4 + 0] + mat[1 * 4 + 2] * a.mat[1 * 4 + 0] + mat[2 * 4 + 2] * a.mat[2 * 4 + 0]
            dst[1] =
                mat[0 * 4 + 2] * a.mat[0 * 4 + 1] + mat[1 * 4 + 2] * a.mat[1 * 4 + 1] + mat[2 * 4 + 2] * a.mat[2 * 4 + 1]
            dst[2] =
                mat[0 * 4 + 2] * a.mat[0 * 4 + 2] + mat[1 * 4 + 2] * a.mat[1 * 4 + 2] + mat[2 * 4 + 2] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 2] = dst[0]
            mat[1 * 4 + 2] = dst[1]
            mat[2 * 4 + 2] = dst[2]
            dst[0] =
                mat[0 * 4 + 3] * a.mat[0 * 4 + 0] + mat[1 * 4 + 3] * a.mat[1 * 4 + 0] + mat[2 * 4 + 3] * a.mat[2 * 4 + 0]
            dst[1] =
                mat[0 * 4 + 3] * a.mat[0 * 4 + 1] + mat[1 * 4 + 3] * a.mat[1 * 4 + 1] + mat[2 * 4 + 3] * a.mat[2 * 4 + 1]
            dst[2] =
                mat[0 * 4 + 3] * a.mat[0 * 4 + 2] + mat[1 * 4 + 3] * a.mat[1 * 4 + 2] + mat[2 * 4 + 3] * a.mat[2 * 4 + 2]
            mat[0 * 4 + 3] = dst[0]
            mat[1 * 4 + 3] = dst[1]
            mat[2 * 4 + 3] = dst[2]
            return this
        }

        // exact compare, no epsilon
        fun Compare(a: idJointMat?): Boolean {
            var i: Int
            i = 0
            while (i < 12) {
                if (mat[i] != a.mat[i]) {
                    return false
                }
                i++
            }
            return true
        }

        // compare with epsilon
        fun Compare(a: idJointMat?, epsilon: Float): Boolean {
            var i: Int
            i = 0
            while (i < 12) {
                if (Math.abs(mat[i] - a.mat[i]) > epsilon) {
                    return false
                }
                i++
            }
            return true
        }

        //public	bool			operator==(	const idJointMat &a ) const;					// exact compare, no epsilon
        //public	bool			operator!=(	const idJointMat &a ) const;					// exact compare, no epsilon
        override fun hashCode(): Int {
            var hash = 3
            hash = 71 * hash + Arrays.hashCode(mat)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idJointMat?
            return Arrays.equals(mat, other.mat)
        }

        fun ToMat3(): idMat3? {
            return idMat3(
                mat[0 * 4 + 0], mat[1 * 4 + 0], mat[2 * 4 + 0],
                mat[0 * 4 + 1], mat[1 * 4 + 1], mat[2 * 4 + 1],
                mat[0 * 4 + 2], mat[1 * 4 + 2], mat[2 * 4 + 2]
            )
        }

        fun ToVec3(): idVec3? {
            return idVec3(
                mat[0 * 4 + 3],
                mat[1 * 4 + 3],
                mat[2 * 4 + 3]
            )
        }

        fun ToJointQuat(): idJointQuat {
            val jq = idJointQuat()
            val trace: Float
            val s: Float
            val t: Float
            var i: Int
            val j: Int
            val k: Int
            val next = intArrayOf(1, 2, 0)
            trace = mat[0 * 4 + 0] + mat[1 * 4 + 1] + mat[2 * 4 + 2]
            if (trace > 0.0f) {
                t = trace + 1.0f
                s = idMath.InvSqrt(t) * 0.5f
                jq.q.oSet(3, s * t)
                jq.q.oSet(0, (mat[1 * 4 + 2] - mat[2 * 4 + 1]) * s)
                jq.q.oSet(1, (mat[2 * 4 + 0] - mat[0 * 4 + 2]) * s)
                jq.q.oSet(2, (mat[0 * 4 + 1] - mat[1 * 4 + 0]) * s)
            } else {
                i = 0
                if (mat[1 * 4 + 1] > mat[0 * 4 + 0]) {
                    i = 1
                }
                if (mat[2 * 4 + 2] > mat[i * 4 + i]) {
                    i = 2
                }
                j = next[i]
                k = next[j]
                t = mat[i * 4 + i] - (mat[j * 4 + j] + mat[k * 4 + k]) + 1.0f
                s = idMath.InvSqrt(t) * 0.5f
                jq.q.oSet(i, s * t)
                jq.q.oSet(3, (mat[j * 4 + k] - mat[k * 4 + j]) * s)
                jq.q.oSet(j, (mat[i * 4 + j] + mat[j * 4 + i]) * s)
                jq.q.oSet(k, (mat[i * 4 + k] + mat[k * 4 + i]) * s)
            }
            jq.t.oSet(0, mat[0 * 4 + 3])
            jq.t.oSet(1, mat[1 * 4 + 3])
            jq.t.oSet(2, mat[2 * 4 + 3])
            return jq
        }

        //public	const float *	ToFloatPtr( void ) const;
        fun ToFloatPtr(): FloatArray? {
            return mat
        }

        companion object {
            const val SIZE = 12 * java.lang.Float.BYTES
            const val BYTES = SIZE / java.lang.Byte.SIZE

            //
            //
            private var DBG_counter = 0
        }
    }
}