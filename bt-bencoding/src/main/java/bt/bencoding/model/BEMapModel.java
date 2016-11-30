package bt.bencoding.model;

import bt.bencoding.BEType;
import bt.bencoding.model.rule.Rule;

import java.util.List;
import java.util.Map;

class BEMapModel extends BaseBEObjectModel {

    private Map<String, BEObjectModel> entriesModel;

    BEMapModel(Map<String, BEObjectModel> entriesModel, List<Rule> rules) {
        super(rules);
        this.entriesModel = entriesModel;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    protected ValidationResult doValidate(ValidationResult validationResult, Object object) {

        if (object != null) {

            Map<?,?> map = (Map<?,?>) object;
            for (Map.Entry<String, BEObjectModel> entryModel : entriesModel.entrySet()) {

                String key = entryModel.getKey();
                entryModel.getValue().validate(map.get(key)).getMessages()
                        .forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
