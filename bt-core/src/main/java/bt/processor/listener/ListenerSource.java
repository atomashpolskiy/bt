package bt.processor.listener;

import bt.processor.ProcessingContext;
import bt.processor.ProcessingStage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class ListenerSource<C extends ProcessingContext> {

    private Class<C> contextType;
    private Map<ProcessingEvent, Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>>> listeners;

    public ListenerSource(Class<C> contextType) {
        this.contextType = contextType;
        this.listeners = new HashMap<>();
    }

    public Class<C> getContextType() {
        return contextType;
    }

    public void addListener(ProcessingEvent event, BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener) {
        listeners.computeIfAbsent(event, it -> new ArrayList<>()).add(listener);
    }

    public Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> getListeners(ProcessingEvent stageFinished) {
        Objects.requireNonNull(stageFinished);
        return listeners.getOrDefault(stageFinished, Collections.emptyList());
    }
}
