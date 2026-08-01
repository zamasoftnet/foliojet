package net.zamasoft.foliojet.css.value;

/**
 * Box Alignment系プロパティのキーワード値です(Grid G5a、2026-07-31——
 * consult-codex-2026-07-31-grid-g5.txt Q2)。layout側の
 * {@code BoxAlignment}と同名対応(マッピングはBoxStyleMapper)。
 * baseline・safe/unsafe prefixはサブセット外(宣言無効——黙って捨てない)。
 * Flex F3aでflex-start/flex-end・space-*を追加(受理範囲はプロパティ側の
 * リストが決める。Grid mapperは未対応値をNORMALへ縮退)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum BoxAlignmentValue implements Value {
	AUTO("auto"), NORMAL("normal"), START("start"), CENTER("center"), END("end"), STRETCH("stretch"),
	/** flex-start/flex-end(Flex F3a——F5bのreverse導入までstart/endと同義)。 */
	FLEX_START("flex-start"), FLEX_END("flex-end"),
	/** content distribution(Flex F3a——self系プロパティでは受理しない)。 */
	SPACE_BETWEEN("space-between"), SPACE_AROUND("space-around"), SPACE_EVENLY("space-evenly");

	private final String text;

	private BoxAlignmentValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
