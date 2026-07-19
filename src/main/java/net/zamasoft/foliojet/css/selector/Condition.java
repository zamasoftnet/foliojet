package net.zamasoft.foliojet.css.selector;


/**
 * 単純セレクタに付加される条件(クラス・ID・属性・擬似クラス等)。
 */
public interface Condition {
	public enum ConditionType {
		CLASS_CONDITION, PSEUDO_CLASS_CONDITION, ID_CONDITION, ATTRIBUTE_CONDITION,
		ONE_OF_ATTRIBUTE_CONDITION, BEGIN_HYPHEN_ATTRIBUTE_CONDITION, PREFIX_ATTRIBUTE_CONDITION,
		SUFFIX_ATTRIBUTE_CONDITION, SUBSTRING_ATTRIBUTE_CONDITION, LANG_CONDITION, NOT_CONDITION,
		IS_CONDITION, WHERE_CONDITION, NTH_CHILD_CONDITION, NTH_OF_TYPE_CONDITION, DIR_CONDITION,
		/**
		 * 要素の終了時点(:last-child系は親の終了時点)まで真偽が確定しない
		 * 疑似クラス。STRUCTURE_SCANパスが収集した{@code SelectorFacts}を
		 * 参照して解決する(docs/PLAN.md「2パス制御モード」参照)。
		 */
		LAST_CHILD_CONDITION, ONLY_CHILD_CONDITION, EMPTY_CONDITION, NTH_LAST_CHILD_CONDITION,
		NTH_LAST_OF_TYPE_CONDITION, LAST_OF_TYPE_CONDITION, ONLY_OF_TYPE_CONDITION,
		/**
		 * {@code :has()}。対象要素の部分木の終了時点まで真偽が確定しない。
		 * StyleContextが要素ごとに(elementStackを遡って)候補subjectの
		 * 判定を積み重ね、パスをまたいでSelectorFactsへ記録する
		 * (docs/PLAN.md「2パス制御モード」参照。processing.pass-count>=2が
		 * 要る点は:last-child系と同じ)。
		 */
		HAS_CONDITION
	}

	public ConditionType getConditionType();

	public String getValue();

	public String getLocalName();

	public Specificity getSpecificity();
}
