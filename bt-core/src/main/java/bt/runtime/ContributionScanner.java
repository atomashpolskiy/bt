package bt.runtime;

import bt.BtException;
import bt.module.Contribute;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class ContributionScanner {

    private static final ContributionScanner instance = new ContributionScanner();

    public static ContributionScanner scanner() {
        return instance;
    }

    // TODO: check for mutual dependencies
    public List<Contribution<?>> scan(Object object) {

        List<Contribution<?>> contributions = null;

        Method[] methods = Objects.requireNonNull(object).getClass().getMethods();
        for (Method method : methods) {
            Contribute annotation = method.getAnnotation(Contribute.class);
            if (annotation != null) {
                if (contributions == null) {
                    contributions = new ArrayList<>();
                }
                contributions.add(new Contribution<>(annotation.value(),
                        module -> {
                            try {
                                method.invoke(object, module);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                String name = method.getDeclaringClass().getName() + "." + method.getName();
                                throw new BtException("Failed to apply module contribution: " + name);
                            }
                        }));
            }
        }
        return (contributions == null) ? Collections.emptyList() : contributions;
    }
}
