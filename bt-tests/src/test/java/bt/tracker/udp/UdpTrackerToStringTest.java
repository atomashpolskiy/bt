/*
 * Copyright (c) 2016—2018 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.tracker.udp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Oleg Ermolaev Date: 17.02.2018 1:28
 */
public class UdpTrackerToStringTest {
    private MockRuntimeLifecycleBinder mockRuntimeLifecycleBinder;

    @Before
    public void setUp() throws Exception {
        mockRuntimeLifecycleBinder = new MockRuntimeLifecycleBinder();
    }

    @After
    public void tearDown() {
        mockRuntimeLifecycleBinder.shutdown();
    }

    @Test
    public void testNoQuery() {
        final String trackerUrl = "udp://tracker.local";
        final UdpTracker udpTracker = createTracker(trackerUrl);
        assertFalse(udpTracker.toString().contains("http://"));
        assertTrue(udpTracker.toString().contains(trackerUrl));
    }

    @Test
    public void testQuery() {
        final String trackerUrl = "udp://tracker.local?";
        final String query = "key=value";
        final String fullTrackerUrl = trackerUrl + query;
        final UdpTracker udpTracker = createTracker(fullTrackerUrl);
        assertTrue(udpTracker.toString().contains(trackerUrl));
        assertFalse(udpTracker.toString().contains(query));
    }

    @Test
    public void testSensitiveQuery() {
        final String sensitive = "HideMePlease";
        final String trackerUrl = "udp://tracker.local?";
        final String fullTrackerUrl = trackerUrl + "private-key=" + sensitive;
        final UdpTracker udpTracker = createTracker(fullTrackerUrl);
        assertTrue(udpTracker.toString().contains(trackerUrl));
        assertFalse(udpTracker.toString().contains(sensitive));
    }

    private UdpTracker createTracker(String trackerUrl) {
        return new UdpTracker(null, mockRuntimeLifecycleBinder, null, 0, 0, trackerUrl);
    }
}
