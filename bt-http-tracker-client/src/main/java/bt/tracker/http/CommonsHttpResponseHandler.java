package bt.tracker.http;

import bt.BtException;
import bt.tracker.TrackerResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

class CommonsHttpResponseHandler implements ResponseHandler<TrackerResponse> {

    private Charset defaultHttpCharset;
    private HttpResponseHandler httpResponseHandler;

    CommonsHttpResponseHandler(HttpResponseHandler httpResponseHandler) {
        this.defaultHttpCharset = Charset.forName("ISO-8859-1");
        this.httpResponseHandler = httpResponseHandler;
    }

    @Override
    public TrackerResponse handleResponse(HttpResponse response) {

        final StatusLine statusLine = response.getStatusLine();
        final HttpEntity entity = response.getEntity();
        if (statusLine.getStatusCode() >= 300) {
            try {
                EntityUtils.consume(entity);
            } catch (IOException e) {
                // do nothing...
            }
            return TrackerResponse.exceptional(new BtException(
                    "Tracker returned error (" + statusLine.getStatusCode() + ": "
                            + statusLine.getReasonPhrase() + ")"));
        }

        if (entity == null) {
            return TrackerResponse.exceptional(new BtException("Tracker response is empty"));
        } else {
            try {

                Charset charset = null;
                ContentType contentType = ContentType.get(entity);
                if (contentType != null) {
                    charset = contentType.getCharset();
                }
                if (charset == null) {
                    charset = defaultHttpCharset;
                }

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                entity.writeTo(bytes);
                return httpResponseHandler.handleResponse(bytes.toByteArray(), charset);
            } catch (IOException e) {
                return TrackerResponse.exceptional(new BtException("Failed to read tracker response", e));
            }
        }
    }
}
