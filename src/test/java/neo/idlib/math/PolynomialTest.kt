package neo.idlib.math

import neo.idlib.math.Complex.idComplex
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Polynomial.idPolynomial
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.stream.Stream

class PolynomialTest {
    private var i = 0
    private var num = 0
    private var roots: FloatArray? = null
    private var value = 0f
    private var complexRoots: Array<idComplex?>? = null
    private var complexValue: idComplex? = null
    private var p: idPolynomial? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        idMath.Init()
        roots = FloatArray(4)
        complexRoots = Stream.generate { idComplex() }.limit(4).toArray { _Dummy_.__Array__() }
    }

    @Test
    fun Test1() {
        p = idPolynomial(-5.0f, 4.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots.get(i))
            Assert.assertTrue(Math.abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test2() {
        p = idPolynomial(-5.0f, 4.0f, 3.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots.get(i))
            Assert.assertTrue(Math.abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test3() {
        p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots.get(i))
            Assert.assertTrue(Math.abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test4() {
        p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots.get(i))
            Assert.assertTrue(Math.abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test5() {
        p = idPolynomial(-5.0f, 4.0f, 3.0f, 2.0f, 1.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots.get(i))
            Assert.assertTrue(Math.abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test6() {
        p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(complexRoots)
        i = 0
        while (i < num) {
            complexValue = p.GetValue(complexRoots.get(i))
            Assert.assertTrue(Math.abs(complexValue.r) < 1e-4f && Math.abs(complexValue.i) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test7() {
        p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(complexRoots)
        i = 0
        while (i < num) {
            complexValue = p.GetValue(complexRoots.get(i))
            Assert.assertTrue(Math.abs(complexValue.r) < 1e-4f && Math.abs(complexValue.i) < 1e-4f)
            i++
        }
    }
}