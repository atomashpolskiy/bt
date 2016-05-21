package bt.bencoding.model;

import bt.bencoding.TypesMapping;
import bt.bencoding.model.rule.Rule;
import bt.bencoding.model.rule.RuleProcessor;

import java.util.List;

public abstract class BaseBEObjectModel implements BEObjectModel {

    private RuleProcessor rules;

    BaseBEObjectModel(List<Rule> rules) {
        this.rules = new RuleProcessor(rules);
    }

    @Override
    public final ValidationResult validate(Object object) {

        ValidationResult result = new ValidationResult();
        if (object != null) {
            Class<?> javaType = TypesMapping.getJavaTypeForBEType(getType());
            if (!javaType.isAssignableFrom(object.getClass())) {
                result.addMessage("Wrong type -- expected " + javaType.getName()
                        + ", actual: " + object.getClass().getName());
                return result;
            }
        }

        rules.process(object).forEach(result::addMessage);
        // fail-fast if any rules failed
        return result.isSuccess()? doValidate(result, object) : result;
    }

    protected abstract ValidationResult doValidate(ValidationResult validationResult, Object object);
}
