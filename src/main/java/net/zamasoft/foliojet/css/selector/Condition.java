package net.zamasoft.foliojet.css.selector;


/**
 * 単純セレクタに付加される条件(クラス・ID・属性・擬似クラス等)。
 */
public interface Condition {
	public enum ConditionType {
		CLASS_CONDITION, PSEUDO_CLASS_CONDITION, ID_CONDITION, ATTRIBUTE_CONDITION,
		ONE_OF_ATTRIBUTE_CONDITION, BEGIN_HYPHEN_ATTRIBUTE_CONDITION, PREFIX_ATTRIBUTE_CONDITION,
		SUFFIX_ATTRIBUTE_CONDITION, SUBSTRING_ATTRIBUTE_CONDITION, LANG_CONDITION, NOT_CONDITION,
		IS_CONDITION
	}

	public ConditionType getConditionType();

	public String getValue();

	public String getLocalName();

	public Specificity getSpecificity();
}
