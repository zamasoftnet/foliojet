package net.zamasoft.foliojet.css.selector;


/**
 * A condition attached to a simple selector (class, ID, attribute, pseudo-class, etc.).
 */
public interface Condition {
	public enum ConditionType {
		CLASS_CONDITION, PSEUDO_CLASS_CONDITION, ID_CONDITION, ATTRIBUTE_CONDITION,
		ONE_OF_ATTRIBUTE_CONDITION, BEGIN_HYPHEN_ATTRIBUTE_CONDITION, PREFIX_ATTRIBUTE_CONDITION,
		SUFFIX_ATTRIBUTE_CONDITION, SUBSTRING_ATTRIBUTE_CONDITION, LANG_CONDITION, NOT_CONDITION,
		IS_CONDITION, WHERE_CONDITION, NTH_CHILD_CONDITION, NTH_OF_TYPE_CONDITION, DIR_CONDITION,
		/**
		 * Pseudo-classes whose truth value is not determined until the element ends
		 * (or the parent ends for the :last-child family). Resolved using {@code SelectorFacts}
		 * collected by the STRUCTURE_SCAN pass (see the development plan, "2パス制御モード").
		 */
		LAST_CHILD_CONDITION, ONLY_CHILD_CONDITION, EMPTY_CONDITION, NTH_LAST_CHILD_CONDITION,
		NTH_LAST_OF_TYPE_CONDITION, LAST_OF_TYPE_CONDITION, ONLY_OF_TYPE_CONDITION,
		/**
		 * {@code :has()}. Its truth value is not determined until the subject element's subtree ends.
		 * For each element, StyleContext accumulates checks of candidate subjects (walking back
		 * through elementStack) and records them in SelectorFacts across passes
		 * (see the development plan, "2パス制御モード"; requires processing.pass-count>=2,
		 * as with the :last-child family).
		 */
		HAS_CONDITION
	}

	public ConditionType getConditionType();

	public String getValue();

	public String getLocalName();

	public Specificity getSpecificity();
}
