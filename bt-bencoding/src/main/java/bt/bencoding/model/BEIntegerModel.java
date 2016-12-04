package bt.bencoding.model;

import bt.bencoding.BEType;
import bt.bencoding.model.rule.Rule;

import java.util.List;

class BEIntegerModel extends BaseBEObjectModel {

    BEIntegerModel(List<Rule> rules) {
        super(rules);
    }

    @Override
    public BEType getType() {
        return BEType.INTEGER;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {
        return validationResult;
    }
}
