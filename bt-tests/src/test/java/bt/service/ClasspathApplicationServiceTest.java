package bt.service;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class ClasspathApplicationServiceTest {

    @Test
    public void testDetermineVersion() {
        ClasspathApplicationService applicationService = new ClasspathApplicationService();
        Version version = applicationService.getVersion();
        assertNotNull(version);
        assertNotEquals(0, version.getMajor());
    }
}
