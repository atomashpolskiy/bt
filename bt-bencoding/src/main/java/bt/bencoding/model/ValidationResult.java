package bt.bencoding.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @since 1.0
 */
public class ValidationResult {

    private boolean success;
    private List<String> messages;

    ValidationResult() {
        success = true;
    }

    /**
     * @return true if validation is successful
     * @since 1.0
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return List of validation errors
     * @since 1.0
     */
    public List<String> getMessages() {
        return messages == null? Collections.emptyList() : Collections.unmodifiableList(messages);
    }

    void addMessage(String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        success = false;
    }
}
