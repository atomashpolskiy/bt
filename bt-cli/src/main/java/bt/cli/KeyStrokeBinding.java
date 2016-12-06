package bt.cli;

import com.googlecode.lanterna.input.KeyStroke;

class KeyStrokeBinding {

    private KeyStroke keyStroke;
    private Runnable binding;

    public KeyStrokeBinding(KeyStroke keyStroke, Runnable binding) {
        this.keyStroke = keyStroke;
        this.binding = binding;
    }

    public KeyStroke getKeyStroke() {
        return keyStroke;
    }

    public Runnable getBinding() {
        return binding;
    }
}
