package bt.bencoding.model;

import bt.bencoding.BEType;
import bt.bencoding.model.rule.Rule;

import java.util.List;

class BEListModel extends BaseBEObjectModel {

    private BEObjectModel elementModel;

    BEListModel(BEObjectModel elementModel, List<Rule> rules) {
        super(rules);
        this.elementModel = elementModel;
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {

        if (object != null) {
            List<?> list = (List<?>) object;
            for (Object element : list) {
                elementModel.validate(element).getMessages().forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
