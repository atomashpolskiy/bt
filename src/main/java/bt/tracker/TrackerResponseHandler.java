package bt.tracker;

import bt.BtException;
import bt.bencoding.BEParser;
import bt.bencoding.model.BEMap;
import bt.bencoding.model.BEObject;
import bt.bencoding.model.BEObjectModel;
import bt.bencoding.model.ValidationResult;
import bt.bencoding.model.YamlBEObjectModelLoader;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

import static bt.bencoding.model.ClassUtil.cast;

public class TrackerResponseHandler {

    private static final String FAILURE_REASON_KEY = "failure reason";
    private static final String WARNING_MESSAGE_KEY = "warning message";
    private static final String INTERVAL_KEY = "interval";
    private static final String MIN_INTERVAL_KEY = "min interval";
    private static final String TRACKER_ID_KEY = "tracker id";
    private static final String COMPLETE_KEY = "complete";
    private static final String INCOMPLETE_KEY = "incomplete";
    private static final String PEERS_KEY = "peers";

    private BEObjectModel trackerResponseModel;

    public TrackerResponseHandler() {

        try {
            try (InputStream in = TrackerResponseHandler.class.getResourceAsStream("/tracker_response.yml")) {
                trackerResponseModel = new YamlBEObjectModelLoader().load(in);
            }
        } catch (IOException e) {
            throw new BtException("Failed to create tracker response handler", e);
        }
    }

    /**
     * @param charset Encoding to use for building strings from binary source
     */
    public TrackerResponse handleResponse(InputStream in, Charset charset) {
        try (BEParser parser = new BEParser(in)) {
            return handleResponse(parser, charset);
        }
    }

    /**
     * @param charset Encoding to use for building strings from binary source
     */
    public TrackerResponse handleResponse(byte[] bytes, Charset charset) {
        try (BEParser parser = new BEParser(bytes)) {
            return handleResponse(parser, charset);
        }
    }

    private TrackerResponse handleResponse(BEParser parser, Charset charset) {

        BEMap responseMap = parser.readMap();

        ValidationResult validationResult = trackerResponseModel.validate(responseMap);
        if (!validationResult.isSuccess()) {
            throw new BtException("Validation failed for tracker response: "
                    + Arrays.toString(validationResult.getMessages().toArray()));
        }

        try {
            return buildResponse(responseMap, charset);
        } catch (Exception e) {
            throw new BtException("Invalid tracker response format", e);
        }
    }

    private TrackerResponse buildResponse(BEMap root, Charset charset) throws Exception {

        TrackerResponse response;

        Map<String, BEObject> responseMap = root.getValue();
        if (responseMap.get(FAILURE_REASON_KEY) != null) {
            response = new TrackerResponse(false);

            byte[] failureReason = cast(byte[].class, FAILURE_REASON_KEY, responseMap.get(FAILURE_REASON_KEY).getValue());
            response.setErrorMessage(new String(failureReason, charset));

        } else {
            response = new TrackerResponse(true);

            if (responseMap.get(WARNING_MESSAGE_KEY) != null) {
                byte[] warningMessage = cast(byte[].class, WARNING_MESSAGE_KEY, responseMap.get(WARNING_MESSAGE_KEY).getValue());
                response.setErrorMessage(new String(warningMessage, charset));
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
            response.setPeers(peers);
        }

        return response;
    }
}
