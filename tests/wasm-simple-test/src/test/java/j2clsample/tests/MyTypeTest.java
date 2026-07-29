package j2clsample.tests;

import com.google.j2cl.junit.apt.J2clTestInput;
import j2clsample.nontest.MyType;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(MyTypeTest.class)
public class MyTypeTest {

    @Test
    public void testOutput() {
        Assert.assertEquals("foo", new MyType().testMe());
    }

    @Test
    public void testAdd() {
        Assert.assertEquals(5, new MyType().add(2, 3));
    }
}