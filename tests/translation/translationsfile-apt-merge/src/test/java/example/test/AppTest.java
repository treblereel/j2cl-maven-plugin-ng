package example.test;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@J2clTestInput(AppTest.class)
public class AppTest {

    @Test
    public void manualTranslation() {
        assertEquals(ValueHolder.getExpectedManualValue(), ManualTranslation.format());
    }

    @Test
    public void aptTranslation() {
        Messages messages = new MessagesImpl();
        assertEquals(ValueHolder.getExpectedAptValue(), messages.helloWorld());
    }
}
