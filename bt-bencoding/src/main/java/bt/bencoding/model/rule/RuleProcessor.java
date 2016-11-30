package bt.bencoding.model.rule;

import java.util.List;
import java.util.stream.Collectors;

public class RuleProcessor {

    private List<Rule> rules;

    public RuleProcessor(List<Rule> rules) {
        this.rules = rules;
    }

    public List<String> process(Object object) {
        return rules.stream()
                .filter(rule -> !rule.validate(object))
                .map(Rule::getDescription)
                .collect(Collectors.toList());
    }
}
