package bt.bencoding.model.rule;

public interface Rule {
    boolean validate(Object object);
    String getDescription();
}
