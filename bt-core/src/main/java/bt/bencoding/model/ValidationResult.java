package bt.bencoding.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private boolean success;
    private List<String> messages;

    ValidationResult() {
        success = true;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getMessages() {
        return messages == null? Collections.emptyList() : Collections.unmodifiableList(messages);
    }

    public void addMessage(String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        success = false;
    }
}
