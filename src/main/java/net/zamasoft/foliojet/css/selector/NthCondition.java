package net.zamasoft.foliojet.css.selector;

/**
 * An+B構文を引数に取る条件(:nth-child() / :nth-of-type())。
 *
 * <p>
 * 判定は先行兄弟チェーン(CSSElement.precedingElement)を先頭側へ反復的に
 * 辿って通し番号を数えるだけで完結し(1P、先読み不要)、要素スタックへの
 * 追加状態は持たない。チェーン長ぶんの反復はあるが再帰は使わない。
 * </p>
 */
public final class NthCondition implements Condition {
	private final ConditionType type;

	private final int a;

	private final int b;

	private final String raw;

	public NthCondition(ConditionType type, int a, int b, String raw) {
		assert type == ConditionType.NTH_CHILD_CONDITION || type == ConditionType.NTH_OF_TYPE_CONDITION
				|| type == ConditionType.NTH_LAST_CHILD_CONDITION
				|| type == ConditionType.NTH_LAST_OF_TYPE_CONDITION : "対象外の ConditionType: " + type;
		this.type = type;
		this.a = a;
		this.b = b;
		this.raw = raw;
	}

	public ConditionType getConditionType() {
		return this.type;
	}

	public int getA() {
		return this.a;
	}

	public int getB() {
		return this.b;
	}

	public String getValue() {
		return this.raw;
	}

	public String getLocalName() {
		return null;
	}

	public Specificity getSpecificity() {
		// 擬似クラスとしての詳細度(クラス相当)
		return new Specificity(0, 1, 0);
	}

	/**
	 * An+B式が指定の通し番号(1始まり)にマッチするか判定します。
	 *
	 * @param position 1始まりの通し番号
	 */
	public boolean matches(int position) {
		assert position >= 1 : "position は1始まりです: " + position;
		int diff = position - this.b;
		if (this.a == 0) {
			return diff == 0;
		}
		// diff = a*k を満たす非負整数 k が存在するか
		return diff % this.a == 0 && diff / this.a >= 0;
	}

	public String toString() {
		final String fname;
		switch (this.type) {
		case NTH_CHILD_CONDITION:
			fname = ":nth-child(";
			break;
		case NTH_OF_TYPE_CONDITION:
			fname = ":nth-of-type(";
			break;
		case NTH_LAST_CHILD_CONDITION:
			fname = ":nth-last-child(";
			break;
		default:
			fname = ":nth-last-of-type(";
			break;
		}
		return fname + this.raw + ")";
	}
}
