package bt.bencoding.model;

import bt.bencoding.BEType;
import bt.bencoding.model.rule.Rule;

import java.util.List;

class BEStringModel extends BaseBEObjectModel {

    private boolean binary;

    BEStringModel(boolean binary, List<Rule> rules) {
        super(rules);
        this.binary = binary;
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }

    @Override
    protected ValidationResult doValidate(ValidationResult validationResult, Object object) {
        return validationResult;
    }
}
