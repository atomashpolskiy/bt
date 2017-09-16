/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
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

package bt.tracker;

import bt.BtException;
import bt.module.TrackerFactories;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class TrackerService implements ITrackerService {

    private Map<String, TrackerFactory> trackerFactories;
    private ConcurrentMap<String, Tracker> knownTrackers;

    @Inject
    public TrackerService(@TrackerFactories Map<String, TrackerFactory> trackerFactories) {
        this.trackerFactories = trackerFactories;
        this.knownTrackers = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isSupportedProtocol(String trackerUrl) {
        return trackerFactories.containsKey(getProtocol(trackerUrl));
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return getOrCreateTracker(trackerUrl);
    }

    @Override
    public Tracker getTracker(AnnounceKey announceKey) {
        if (announceKey.isMultiKey()) {
            return new MultiTracker(this, announceKey);
        } else {
            return getOrCreateTracker(announceKey.getTrackerUrl());
        }
    }

    private Tracker getOrCreateTracker(String trackerUrl) {
        Tracker tracker = knownTrackers.get(trackerUrl);
        if (tracker == null) {
            tracker = createTracker(trackerUrl);
            Tracker existing = knownTrackers.putIfAbsent(trackerUrl, tracker);
            if (existing != null) {
                tracker = existing;
            }
        }
        return tracker;
    }

    private Tracker createTracker(String trackerUrl) {
        String protocol = getProtocol(trackerUrl);
        TrackerFactory factory = trackerFactories.get(protocol);
        if (factory == null) {
            throw new BtException("Unsupported tracker protocol: " + protocol);
        }
        return factory.getTracker(trackerUrl);
    }

    private static String getProtocol(String url) {
        int schemaDelimiterIndex = url.indexOf("://");
        if (schemaDelimiterIndex < 1) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        return url.substring(0, schemaDelimiterIndex);
    }
}
