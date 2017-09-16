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

package bt.tracker.http;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.ValidationResult;
import bt.bencoding.model.YamlBEObjectModelLoader;
import bt.tracker.CompactPeerInfo;
import bt.tracker.TrackerResponse;
import bt.tracker.CompactPeerInfo.AddressType;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

import static bt.bencoding.model.ClassUtil.cast;

/**
 * Basic HTTP tracker response handler,
 * that is expecting a response in the format specified in BEP-3.
 *
 * @since 1.0
 */
class HttpResponseHandler {

    private static final String FAILURE_REASON_KEY = "failure reason";
    private static final String WARNING_MESSAGE_KEY = "warning message";
    private static final String INTERVAL_KEY = "interval";
    private static final String MIN_INTERVAL_KEY = "min interval";
    private static final String TRACKER_ID_KEY = "tracker id";
    private static final String COMPLETE_KEY = "complete";
    private static final String INCOMPLETE_KEY = "incomplete";
    private static final String PEERS_KEY = "peers";
    private static final String CRYPTO_FLAGS_KEY = "crypto_flags";

    private BEObjectModel trackerResponseModel;

    /**
     * @since 1.0
     */
    public HttpResponseHandler() {

        try {
            try (InputStream in = HttpResponseHandler.class.getResourceAsStream("/tracker_response.yml")) {
                trackerResponseModel = new YamlBEObjectModelLoader().load(in);
            }
        } catch (IOException e) {
            throw new BtException("Failed to create tracker response handler", e);
        }
    }

    /**
     * Read response from an input stream.
     *
     * @param charset Encoding to use for reading a response from the input stream
     * @since 1.0
     */
    public TrackerResponse handleResponse(InputStream in, Charset charset) {
        try (BEParser parser = new BEParser(in)) {
            return handleResponse(parser, charset);
        } catch (Exception e) {
            return TrackerResponse.exceptional(e);
        }
    }

    /**
     * Read response from a byte array.
     *
     * @param charset Encoding to use for reading a response from the byte array
     * @since 1.0
     */
    public TrackerResponse handleResponse(byte[] bytes, Charset charset) {
        try (BEParser parser = new BEParser(bytes)) {
            return handleResponse(parser, charset);
        } catch (Exception e) {
            return TrackerResponse.exceptional(e);
        }
    }

    private TrackerResponse handleResponse(BEParser parser, Charset charset) {

        BEMap responseMap = parser.readMap();

        ValidationResult validationResult = trackerResponseModel.validate(responseMap);
        if (!validationResult.isSuccess()) {
            return TrackerResponse.exceptional(new BtException("Validation failed for tracker response: "
                    + Arrays.toString(validationResult.getMessages().toArray())));
        }

        try {
            return buildResponse(responseMap, charset);
        } catch (Exception e) {
            return TrackerResponse.exceptional(new BtException("Invalid tracker response format", e));
        }
    }

    private TrackerResponse buildResponse(BEMap root, Charset charset) throws Exception {

        TrackerResponse response;

        Map<String, BEObject<?>> responseMap = root.getValue();
        if (responseMap.get(FAILURE_REASON_KEY) != null) {

            byte[] failureReason = cast(byte[].class, FAILURE_REASON_KEY, responseMap.get(FAILURE_REASON_KEY).getValue());
            response = TrackerResponse.failure(new String(failureReason, charset));

        } else {
            response = TrackerResponse.ok();

            if (responseMap.get(WARNING_MESSAGE_KEY) != null) {
                byte[] warningMessage = cast(byte[].class, WARNING_MESSAGE_KEY, responseMap.get(WARNING_MESSAGE_KEY).getValue());
                response.setWarningMessage(new String(warningMessage, charset));
            }

            // possible truncation of integer values is not a problem
            BigInteger interval = cast(BigInteger.class, INTERVAL_KEY, responseMap.get(INTERVAL_KEY).getValue());
            response.setInterval(interval.intValue());

            if (responseMap.get(MIN_INTERVAL_KEY) != null) {
                BigInteger minInterval = cast(BigInteger.class, MIN_INTERVAL_KEY, responseMap.get(MIN_INTERVAL_KEY).getValue());
                response.setMinInterval(minInterval.intValue());
            }

            if (responseMap.get(TRACKER_ID_KEY) != null) {
                byte[] trackerId = cast(byte[].class, TRACKER_ID_KEY, responseMap.get(TRACKER_ID_KEY).getValue());
                response.setTrackerId(trackerId);
            }

            if (responseMap.get(COMPLETE_KEY) != null) {
                BigInteger complete = cast(BigInteger.class, COMPLETE_KEY, responseMap.get(COMPLETE_KEY).getValue());
                response.setSeederCount(complete.intValue());
            }

            if (responseMap.get(INCOMPLETE_KEY) != null) {
                BigInteger incomplete = cast(BigInteger.class, INCOMPLETE_KEY, responseMap.get(INCOMPLETE_KEY).getValue());
                response.setLeecherCount(incomplete.intValue());
            }

            byte[] peers = cast(byte[].class, PEERS_KEY, responseMap.get(PEERS_KEY).getValue());
            if (responseMap.get(CRYPTO_FLAGS_KEY) != null) {
                byte[] cryptoFlags = cast(byte[].class, CRYPTO_FLAGS_KEY, responseMap.get(CRYPTO_FLAGS_KEY).getValue());
                response.setPeers(new CompactPeerInfo(peers, AddressType.IPV4, cryptoFlags));
            } else {
                response.setPeers(new CompactPeerInfo(peers, AddressType.IPV4));
            }
        }

        return response;
    }
}
