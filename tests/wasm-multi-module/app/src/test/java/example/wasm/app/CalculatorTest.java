package example.wasm.app;

import com.google.j2cl.junit.apt.J2clTestInput;
import example.wasm.lib1.MathUtils;
import example.wasm.lib2.StringUtils;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(CalculatorTest.class)
public class CalculatorTest {

    @Test
    public void testCompute() {
        Assert.assertEquals(12, Calculator.compute(2, 5));
    }

    @Test
    public void testDescribe() {
        String result = Calculator.describe(3, 7);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains("10"));
    }

    @Test
    public void testFactorialSum() {
        Assert.assertEquals(144, Calculator.factorialSum(5, 4));
    }

    @Test
    public void testTransitiveLibAdd() {
        Assert.assertEquals(15, MathUtils.add(7, 8));
    }

    @Test
    public void testTransitiveLibMultiply() {
        Assert.assertEquals(42, MathUtils.multiply(6, 7));
    }

    @Test
    public void testStringRepeat() {
        String result = StringUtils.repeat("abc", 3);
        Assert.assertNotNull(result);
        Assert.assertEquals(9, result.length());
    }
}
