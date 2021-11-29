package neo.idlib.math

import neo.idlib.Lib
import neo.idlib.math.Complex.idComplex
import neo.idlib.math.Math_h.idMath
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object Polynomial {
    const val EPSILON = 1e-6f

    /*
     ===============================================================================

     Polynomial of arbitrary degree with real coefficients.

     ===============================================================================
     */
    class idPolynomial {
        private var allocated: Int
        private var coefficient: FloatArray?
        private var degree: Int

        //
        //
        constructor() {
            degree = -1
            allocated = 0
            coefficient = null
        }

        constructor(d: Int) {
            degree = -1
            allocated = 0
            coefficient = null
            Resize(d, false)
        }

        constructor(a: Float, b: Float) {
            degree = -1
            allocated = 0
            coefficient = null
            Resize(1, false)
            coefficient.get(0) = b
            coefficient.get(1) = a
        }

        constructor(a: Float, b: Float, c: Float) {
            degree = -1
            allocated = 0
            coefficient = null
            Resize(2, false)
            coefficient.get(0) = c
            coefficient.get(1) = b
            coefficient.get(2) = a
        }

        constructor(a: Float, b: Float, c: Float, d: Float) {
            degree = -1
            allocated = 0
            coefficient = null
            Resize(3, false)
            coefficient.get(0) = d
            coefficient.get(1) = c
            coefficient.get(2) = b
            coefficient.get(3) = a
        }

        constructor(a: Float, b: Float, c: Float, d: Float, e: Float) {
            degree = -1
            allocated = 0
            coefficient = null
            Resize(4, false)
            coefficient.get(0) = e
            coefficient.get(1) = d
            coefficient.get(2) = c
            coefficient.get(3) = b
            coefficient.get(4) = a
        }

        constructor(p: idPolynomial?) {
            allocated = p.allocated
            System.arraycopy(p.coefficient, 0, coefficient, 0, p.coefficient.size)
            degree = p.degree
        }

        fun oGet(index: Int): Float {
            assert(index >= 0 && index <= degree)
            return coefficient.get(index)
        }

        fun oNegative(): idPolynomial? {
            var i: Int
            val n = idPolynomial()

//            n = new idPolynomial(this);
            n.oSet(this)
            i = 0
            while (i <= degree) {
                n.coefficient.get(i) = -n.coefficient.get(i)
                i++
            }
            return n
        }

        fun oSet(p: idPolynomial?): idPolynomial? {
            Resize(p.degree, false)
            System.arraycopy(p.coefficient, 0, coefficient, 0, degree + 1)
            return this
        }

        fun oPlus(p: idPolynomial?): idPolynomial? {
            var i: Int
            val n = idPolynomial()
            if (degree > p.degree) {
                n.Resize(degree, false)
                i = 0
                while (i <= p.degree) {
                    n.coefficient.get(i) = coefficient.get(i) + p.coefficient.get(i)
                    i++
                }
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i)
                    i++
                }
                n.degree = degree
            } else if (p.degree > degree) {
                n.Resize(p.degree, false)
                i = 0
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i) + p.coefficient.get(i)
                    i++
                }
                while (i <= p.degree) {
                    n.coefficient.get(i) = p.coefficient.get(i)
                    i++
                }
                n.degree = p.degree
            } else {
                n.Resize(degree, false)
                n.degree = 0
                i = 0
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i) + p.coefficient.get(i)
                    if (n.coefficient.get(i) != 0.0f) {
                        n.degree = i
                    }
                    i++
                }
            }
            return n
        }

        fun oMinus(p: idPolynomial?): idPolynomial? {
            var i: Int
            val n = idPolynomial()
            if (degree > p.degree) {
                n.Resize(degree, false)
                i = 0
                while (i <= p.degree) {
                    n.coefficient.get(i) = coefficient.get(i) - p.coefficient.get(i)
                    i++
                }
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i)
                    i++
                }
                n.degree = degree
            } else if (p.degree >= degree) {
                n.Resize(p.degree, false)
                i = 0
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i) - p.coefficient.get(i)
                    i++
                }
                while (i <= p.degree) {
                    n.coefficient.get(i) = -p.coefficient.get(i)
                    i++
                }
                n.degree = p.degree
            } else {
                n.Resize(degree, false)
                n.degree = 0
                i = 0
                while (i <= degree) {
                    n.coefficient.get(i) = coefficient.get(i) - p.coefficient.get(i)
                    if (n.coefficient.get(i) != 0.0f) {
                        n.degree = i
                    }
                    i++
                }
            }
            return n
        }

        fun oMultiply(s: Float): idPolynomial? {
            val n = idPolynomial()
            if (s == 0.0f) {
                n.degree = 0
            } else {
                n.Resize(degree, false)
                for (i in 0..degree) {
                    n.coefficient.get(i) = coefficient.get(i) * s
                }
            }
            return n
        }

        fun oDivide(s: Float): idPolynomial? {
            val invs: Float
            val n = idPolynomial()
            assert(s != 0.0f)
            n.Resize(degree, false)
            invs = 1.0f / s
            for (i in 0..degree) {
                n.coefficient.get(i) = coefficient.get(i) * invs
            }
            return n
        }

        fun oPluSet(p: idPolynomial?): idPolynomial? {
            var i: Int
            if (degree > p.degree) {
                i = 0
                while (i <= p.degree) {
                    coefficient.get(i) += p.coefficient.get(i)
                    i++
                }
            } else if (p.degree > degree) {
                Resize(p.degree, true)
                i = 0
                while (i <= degree) {
                    coefficient.get(i) += p.coefficient.get(i)
                    i++
                }
                while (i <= p.degree) {
                    coefficient.get(i) = p.coefficient.get(i)
                    i++
                }
            } else {
                i = 0
                while (i <= degree) {
                    coefficient.get(i) += p.coefficient.get(i)
                    if (coefficient.get(i) != 0.0f) {
                        degree = i
                    }
                    i++
                }
            }
            return this
        }

        //public	boolean			operator==(	const idPolynomial &p ) const;					// exact compare, no epsilon
        //public	boolean			operator!=(	const idPolynomial &p ) const;					// exact compare, no epsilon
        fun oMinSet(p: idPolynomial?): idPolynomial? {
            var i: Int
            if (degree > p.degree) {
                i = 0
                while (i <= p.degree) {
                    coefficient.get(i) -= p.coefficient.get(i)
                    i++
                }
            } else if (p.degree > degree) {
                Resize(p.degree, true)
                i = 0
                while (i <= degree) {
                    coefficient.get(i) -= p.coefficient.get(i)
                    i++
                }
                while (i <= p.degree) {
                    coefficient.get(i) = -p.coefficient.get(i)
                    i++
                }
            } else {
                i = 0
                while (i <= degree) {
                    coefficient.get(i) -= p.coefficient.get(i)
                    if (coefficient.get(i) != 0.0f) {
                        degree = i
                    }
                    i++
                }
            }
            return this
        }

        fun oMulSet(s: Float): idPolynomial? {
            if (s == 0.0f) {
                degree = 0
            } else {
                for (i in 0..degree) {
                    coefficient.get(i) *= s
                }
            }
            return this
        }

        fun oDivSet(s: Float): idPolynomial? {
            val invs: Float
            assert(s != 0.0f)
            invs = 1.0f / s
            for (i in 0..degree) {
                coefficient.get(i) = invs
            }
            return this
        }

        fun Compare(p: idPolynomial?): Boolean { // exact compare, no epsilon
            if (degree != p.degree) {
                return false
            }
            for (i in 0..degree) {
                if (coefficient.get(i) != p.coefficient.get(i)) {
                    return false
                }
            }
            return true
        }

        fun Compare(p: idPolynomial?, epsilon: Float): Boolean { // compare with epsilon
            if (degree != p.degree) {
                return false
            }
            for (i in 0..degree) {
                if (Math.abs(coefficient.get(i) - p.coefficient.get(i)) > epsilon) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = 7
            hash = 43 * hash + degree
            hash = 43 * hash + Arrays.hashCode(coefficient)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as idPolynomial?
            return if (degree != other.degree) {
                false
            } else Arrays.equals(coefficient, other.coefficient)
        }

        fun Zero() {
            degree = 0
        }

        fun Zero(d: Int) {
            Resize(d, false)
            for (i in 0..degree) {
                coefficient.get(i) = 0.0f
            }
        }

        fun GetDimension(): Int { // get the degree of the polynomial
            return degree
        }

        fun GetDegree(): Int { // get the degree of the polynomial
            return degree
        }

        fun GetValue(x: Float): Float { // evaluate the polynomial with the given real value
            var y: Float
            var z: Float
            y = coefficient.get(0)
            z = x
            for (i in 1..degree) {
                y += coefficient.get(i) * z
                z *= x
            }
            return y
        }

        fun GetValue(x: idComplex?): idComplex? { // evaluate the polynomial with the given complex value
            val y = idComplex()
            val z = idComplex()
            y.Set(coefficient.get(0), 0.0f)
            z.oSet(x)
            for (i in 1..degree) {
                y.plusAssign(z.times(coefficient.get(i)))
                z.timesAssign(x)
            }
            return y
        }

        fun GetDerivative(): idPolynomial? { // get the first derivative of the polynomial
            val n = idPolynomial()
            if (degree == 0) {
                return n
            }
            n.Resize(degree - 1, false)
            for (i in 1..degree) {
                n.coefficient.get(i - 1) = i * coefficient.get(i)
            }
            return n
        }

        fun GetAntiDerivative(): idPolynomial? { // get the anti derivative of the polynomial
            val n = idPolynomial()
            if (degree == 0) {
                return n
            }
            n.Resize(degree + 1, false)
            n.coefficient.get(0) = 0.0f
            for (i in 0..degree) {
                n.coefficient.get(i + 1) = coefficient.get(i) / (i + 1)
            }
            return n
        }

        fun GetRoots(roots: Array<idComplex?>?): Int { // get all roots
            var i: Int
            var j: Int
            val x = idComplex()
            val b = idComplex()
            val c = idComplex()
            val coef: Array<idComplex?>
            coef =
                arrayOfNulls<idComplex?>(degree + 1) //	coef = (idComplex *) _alloca16( ( degree + 1 ) * sizeof( idComplex ) );
            i = 0
            while (i <= degree) {
                coef[i] = idComplex(coefficient.get(i), 0.0f)
                i++
            }
            i = degree - 1
            while (i >= 0) {
                x.Zero()
                Laguer(coef, i + 1, x)
                if (Math.abs(x.i) < 2.0f * Polynomial.EPSILON * Math.abs(x.r)) {
                    x.i = 0.0f
                }
                roots.get(i).oSet(x)
                b.oSet(coef[i + 1])
                j = i
                while (j >= 0) {
                    c.oSet(coef[j])
                    coef[j].oSet(b)
                    b.oSet(x.times(b).plus(c))
                    j--
                }
                i--
            }
            i = 0
            while (i <= degree) {
                coef[i].Set(coefficient.get(i), 0.0f)
                i++
            }
            i = 0
            while (i < degree) {
                Laguer(coef, degree, roots.get(i))
                i++
            }
            i = 1
            while (i < degree) {
                x.oSet(roots.get(i))
                j = i - 1
                while (j >= 0) {
                    if (roots.get(j).r <= x.r) {
                        break
                    }
                    roots.get(j + 1).oSet(roots.get(j))
                    j--
                }
                roots.get(j + 1).oSet(x)
                i++
            }
            return degree
        }

        //
        //public	const float *	ToFloatPtr( void ) const;
        //public	float *			ToFloatPtr( void );
        //public	const char *	ToString( int precision = 2 ) const;
        //
        //public	static void		Test( void );
        //
        fun GetRoots(roots: FloatArray?): Int { // get the real roots
            var i: Int
            var num: Int
            val complexRoots: Array<idComplex?>
            when (degree) {
                0 -> return 0
                1 -> return GetRoots1(coefficient.get(1), coefficient.get(0), roots)
                2 -> return GetRoots2(coefficient.get(2), coefficient.get(1), coefficient.get(0), roots)
                3 -> return GetRoots3(
                    coefficient.get(3),
                    coefficient.get(2),
                    coefficient.get(1),
                    coefficient.get(0),
                    roots
                )
                4 -> return GetRoots4(
                    coefficient.get(4),
                    coefficient.get(3),
                    coefficient.get(2),
                    coefficient.get(1),
                    coefficient.get(0),
                    roots
                )
            }

            // The Abel-Ruffini theorem states that there is no general solution
            // in radicals to polynomial equations of degree five or higher.
            // A polynomial equation can be solved by radicals if and only if
            // its Galois group is a solvable group.
//	complexRoots = (idComplex *) _alloca16( degree * sizeof( idComplex ) );
            complexRoots = arrayOfNulls<idComplex?>(degree)
            GetRoots(complexRoots)
            num = 0.also { i = it }
            while (i < degree) {
                if (complexRoots[i].i == 0.0f) {
                    roots.get(i) = complexRoots[i].r
                    num++
                }
                i++
            }
            return num
        }

        private fun Resize(d: Int, keep: Boolean) {
            val alloc = d + 1 + 3 and 3.inv()
            if (alloc > allocated) {
                val ptr = FloatArray(alloc) //float *ptr = (float *) Mem_Alloc16( alloc * sizeof( float ) );
                if (coefficient != null) {
                    if (keep) {
                        System.arraycopy(coefficient, 0, ptr, 0, degree + 1)
                    }
                    //			Mem_Free16( coefficient );
                }
                allocated = alloc
                coefficient = ptr
            }
            degree = d
        }

        private fun Laguer(coef: Array<idComplex?>?, degree: Int, x: idComplex?): Int {
            val MT = 10
            val MAX_ITERATIONS = MT * 8
            val frac = floatArrayOf(0.0f, 0.5f, 0.25f, 0.75f, 0.13f, 0.38f, 0.62f, 0.88f, 1.0f)
            var i: Int
            var j: Int
            var abx: Float
            var abp: Float
            var abm: Float
            var err: Float
            var dx: idComplex?
            var cx: idComplex?
            var b: idComplex?
            var d: idComplex? = idComplex()
            var f: idComplex? = idComplex()
            var g: idComplex?
            var s: idComplex
            var gps: idComplex?
            var gms: idComplex?
            var g2: idComplex?
            i = 1
            while (i <= MAX_ITERATIONS) {
                b = coef.get(degree)
                err = b.Abs()
                d.Zero()
                f.Zero()
                abx = x.Abs()
                j = degree - 1
                while (j >= 0) {
                    f = x.times(f).plus(d)
                    d = x.times(d).plus(b)
                    b = x.times(b).plus(coef.get(j))
                    err = b.Abs() + abx * err
                    j--
                }
                if (b.Abs() < err * Polynomial.EPSILON) {
                    return i
                }
                g = d.div(b)
                g2 = g.times(g)
                s = g2.minus(f.div(b).times(2.0f)).times(degree.toFloat()).minus(g2)
                    .times((degree - 1).toFloat()).Sqrt()
                gps = g.plus(s)
                gms = g.minus(s)
                abp = gps.Abs()
                abm = gms.Abs()
                if (abp < abm) {
                    gps = gms
                }
                dx = if (Lib.Companion.Max(abp, abm) > 0.0f) {
                    idComplex.Companion.div(degree.toFloat(), gps)
                } else {
                    idComplex(
                        idMath.Cos(i.toFloat()),
                        idMath.Sin(i.toFloat())
                    ).times(idMath.Exp(idMath.Log(1.0f + abx)))
                }
                cx = x.minus(dx)
                if (x === cx) {
                    return i
                }
                if (i % MT == 0) {
                    x.oSet(cx)
                } else {
                    x.minusAssign(dx.times(frac[i / MT]))
                }
                i++
            }
            return i
        }

        companion object {
            //
            //public	float			operator[]( int index ) const;
            fun GetRoots1(a: Float, b: Float, roots: FloatArray?): Int {
                assert(a != 0.0f)
                roots.get(0) = -b / a
                return 1
            }

            fun GetRoots2(a: Float, b: Float, c: Float, roots: FloatArray?): Int {
                var b = b
                var c = c
                val inva: Float
                var ds: Float
                if (a != 1.0f) {
                    assert(a != 0.0f)
                    inva = 1.0f / a
                    c *= inva
                    b *= inva
                }
                ds = b * b - 4.0f * c
                return if (ds < 0.0f) {
                    0
                } else if (ds > 0.0f) {
                    ds = idMath.Sqrt(ds)
                    roots.get(0) = 0.5f * (-b - ds)
                    roots.get(1) = 0.5f * (-b + ds)
                    2
                } else {
                    roots.get(0) = 0.5f * -b
                    1
                }
            }

            fun GetRoots3(a: Float, b: Float, c: Float, d: Float, roots: FloatArray?): Int {
                var b = b
                var c = c
                var d = d
                val inva: Float
                val f: Float
                val g: Float
                val halfg: Float
                val ofs: Float
                var ds: Float
                val dist: Float
                val angle: Float
                val cs: Float
                val ss: Float
                var t: Float
                if (a != 1.0f) {
                    assert(a != 0.0f)
                    inva = 1.0f / a
                    d *= inva
                    c *= inva
                    b *= inva
                }
                f = 1.0f / 3.0f * (3.0f * c - b * b)
                g = 1.0f / 27.0f * (2.0f * b * b * b - 9.0f * c * b + 27.0f * d)
                halfg = 0.5f * g
                ofs = 1.0f / 3.0f * b
                ds = 0.25f * g * g + 1.0f / 27.0f * f * f * f
                return if (ds < 0.0f) {
                    dist = idMath.Sqrt(-1.0f / 3.0f * f)
                    angle = 1.0f / 3.0f * idMath.ATan(idMath.Sqrt(-ds), -halfg)
                    cs = idMath.Cos(angle)
                    ss = idMath.Sin(angle)
                    roots.get(0) = 2.0f * dist * cs - ofs
                    roots.get(1) = -dist * (cs + idMath.SQRT_THREE * ss) - ofs
                    roots.get(2) = -dist * (cs - idMath.SQRT_THREE * ss) - ofs
                    3
                } else if (ds > 0.0f) {
                    ds = idMath.Sqrt(ds)
                    t = -halfg + ds
                    if (t >= 0.0f) {
                        roots.get(0) = idMath.Pow(t, 1.0f / 3.0f)
                    } else {
                        roots.get(0) = -idMath.Pow(-t, 1.0f / 3.0f)
                    }
                    t = -halfg - ds
                    if (t >= 0.0f) {
                        roots.get(0) += idMath.Pow(t, 1.0f / 3.0f)
                    } else {
                        roots.get(0) -= idMath.Pow(-t, 1.0f / 3.0f)
                    }
                    roots.get(0) -= ofs
                    1
                } else {
                    t = if (halfg >= 0.0f) {
                        -idMath.Pow(halfg, 1.0f / 3.0f)
                    } else {
                        idMath.Pow(-halfg, 1.0f / 3.0f)
                    }
                    roots.get(0) = 2.0f * t - ofs
                    roots.get(1) = -t - ofs
                    roots.get(2) = roots.get(1)
                    3
                }
            }

            fun GetRoots4(a: Float, b: Float, c: Float, d: Float, e: Float, roots: FloatArray?): Int {
                var b = b
                var c = c
                var d = d
                var e = e
                val count: Int
                val inva: Float
                val y: Float
                val ds: Float
                val r: Float
                val s1: Float
                val s2: Float
                val t1: Float
                var t2: Float
                val tp: Float
                val tm: Float
                val roots3 = FloatArray(3)
                if (a != 1.0f) {
                    assert(a != 0.0f)
                    inva = 1.0f / a
                    e *= inva
                    d *= inva
                    c *= inva
                    b *= inva
                }
                count = 0
                GetRoots3(1.0f, -c, b * d - 4.0f * e, -b * b * e + 4.0f * c * e - d * d, roots3)
                y = roots3[0]
                ds = 0.25f * b * b - c + y
                return if (ds < 0.0f) {
                    0
                } else if (ds > 0.0f) {
                    r = idMath.Sqrt(ds)
                    t1 = 0.75f * b * b - r * r - 2.0f * c
                    t2 = (4.0f * b * c - 8.0f * d - b * b * b) / (4.0f * r)
                    tp = t1 + t2
                    tm = t1 - t2
                    if (tp >= 0.0f) {
                        s1 = idMath.Sqrt(tp)
                        roots.get(count++) = -0.25f * b + 0.5f * (r + s1)
                        roots.get(count++) = -0.25f * b + 0.5f * (r - s1)
                    }
                    if (tm >= 0.0f) {
                        s2 = idMath.Sqrt(tm)
                        roots.get(count++) = -0.25f * b + 0.5f * (s2 - r)
                        roots.get(count++) = -0.25f * b - 0.5f * (s2 + r)
                    }
                    count
                } else {
                    t2 = y * y - 4.0f * e
                    if (t2 >= 0.0f) {
                        t2 = 2.0f * idMath.Sqrt(t2)
                        t1 = 0.75f * b * b - 2.0f * c
                        if (t1 + t2 >= 0.0f) {
                            s1 = idMath.Sqrt(t1 + t2)
                            roots.get(count++) = -0.25f * b + 0.5f * s1
                            roots.get(count++) = -0.25f * b - 0.5f * s1
                        }
                        if (t1 - t2 >= 0.0f) {
                            s2 = idMath.Sqrt(t1 - t2)
                            roots.get(count++) = -0.25f * b + 0.5f * s2
                            roots.get(count++) = -0.25f * b - 0.5f * s2
                        }
                    }
                    count
                }
            }

            fun Test() {
                var i: Int
                var num: Int
                val roots = FloatArray(4)
                var value: Float
                val complexRoots = Stream.generate { idComplex() }.limit(4).toArray<idComplex?> { _Dummy_.__Array__() }
                var complexValue: idComplex
                var p: idPolynomial
                p = idPolynomial(-5.0f, 4.0f)
                num = p.GetRoots(roots)
                i = 0
                while (i < num) {
                    value = p.GetValue(roots[i])
                    assert(Math.abs(value) < 1e-4f)
                    i++
                }
                p = idPolynomial(-5.0f, 4.0f, 3.0f)
                num = p.GetRoots(roots)
                i = 0
                while (i < num) {
                    value = p.GetValue(roots[i])
                    assert(Math.abs(value) < 1e-4f)
                    i++
                }
                p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
                num = p.GetRoots(roots)
                i = 0
                while (i < num) {
                    value = p.GetValue(roots[i])
                    assert(Math.abs(value) < 1e-4f)
                    i++
                }
                p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
                num = p.GetRoots(roots)
                i = 0
                while (i < num) {
                    value = p.GetValue(roots[i])
                    assert(Math.abs(value) < 1e-4f)
                    i++
                }
                p = idPolynomial(-5.0f, 4.0f, 3.0f, 2.0f, 1.0f)
                num = p.GetRoots(roots)
                i = 0
                while (i < num) {
                    value = p.GetValue(roots[i])
                    assert(Math.abs(value) < 1e-4f)
                    i++
                }
                p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
                num = p.GetRoots(complexRoots)
                i = 0
                while (i < num) {
                    complexValue = p.GetValue(complexRoots[i])
                    assert(Math.abs(complexValue.r) < 1e-4f && Math.abs(complexValue.i) < 1e-4f)
                    i++
                }
                p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
                num = p.GetRoots(complexRoots)
                i = 0
                while (i < num) {
                    complexValue = p.GetValue(complexRoots[i])
                    assert(Math.abs(complexValue.r) < 1e-4f && Math.abs(complexValue.i) < 1e-4f)
                    i++
                }
            }
        }
    }
}