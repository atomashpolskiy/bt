package bt.bencoding.model.rule;

import bt.BtException;
import bt.bencoding.model.ClassUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExclusiveRule implements Rule {

    boolean shouldCheckRequired;
    private RequiredRule exclusiveRequired;
    private RequiredRule otherRequired;
    private Collection<Set<String>> exclusives;

    public ExclusiveRule(Collection<Set<String>> exclusives, List<String> required) {

        shouldCheckRequired = true;

        List<String> allExclusives = exclusives.stream().flatMap(Collection::stream).collect(Collectors.toList());

        List<String> otherRequiredKeys = new ArrayList<>();
        otherRequiredKeys.addAll(required);
        otherRequiredKeys.removeAll(allExclusives);
        this.otherRequired = new RequiredRule(otherRequiredKeys);

        List<String> exclusiveRequiredKeys = new ArrayList<>();
        exclusiveRequiredKeys.addAll(required);
        exclusiveRequiredKeys.removeAll(otherRequiredKeys);
        this.exclusiveRequired = new RequiredRule(exclusiveRequiredKeys);

        this.exclusives = exclusives;
    }

    public ExclusiveRule(Collection<Set<String>> exclusives) {
        this.exclusives = exclusives;
    }

    @Override
    public boolean validate(Object object) {
        try {
            Map map = ClassUtil.cast(Map.class, null, object);
            long count = exclusives.stream()
                    .map(exclusive -> {
                        List<Object> found = new ArrayList<>(exclusive.size() + 1);
                        exclusive.forEach(key -> {
                            Object obj = map.get(key);
                            if (obj != null) {
                                found.add(obj);
                            }
                        });
                        return found;
                    })
                    .filter(found -> !found.isEmpty())
                    .count();

            if (count > 1) {
                return false;
            } else if (shouldCheckRequired) {
                if (count == 0) {
                    return exclusiveRequired.validate(object) && otherRequired.validate(object);
                } else {
                    return otherRequired.validate(object);
                }
            } else {
                return true;
            }

        } catch (Exception e) {
            throw new BtException("Unexpected validation exception", e);
        }
    }

    @Override
    public String getDescription() {
        // TODO: do not print brackets for singleton exclusive sets
        String description = "properties are mutually exclusive: " + Arrays.toString(exclusives.toArray());
        if (exclusiveRequired != null) {
            description += "; " + exclusiveRequired.toString();
        }
        if (otherRequired != null) {
            description += "; " + otherRequired.toString();
        }
        return description;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
