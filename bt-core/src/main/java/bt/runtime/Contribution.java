package bt.runtime;

import com.google.inject.Module;

import java.util.function.Consumer;

class Contribution<T extends Module> {

    private Class<T> moduleType;
    private Consumer<T> contributor;

    Contribution(Class<T> moduleType, Consumer<T> contributor) {
        this.moduleType = moduleType;
        this.contributor = contributor;
    }

    public Class<T> getModuleType() {
        return moduleType;
    }

    public void apply(T module) {
        contributor.accept(module);
    }
}
