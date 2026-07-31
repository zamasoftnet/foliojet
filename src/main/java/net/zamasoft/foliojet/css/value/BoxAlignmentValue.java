package net.zamasoft.foliojet.css.value;

/**
 * Box Alignment系プロパティのキーワード値です(Grid G5a、2026-07-31——
 * consult-codex-2026-07-31-grid-g5.txt Q2)。layout側の
 * {@code BoxAlignment}と同名対応(マッピングはBoxStyleMapper)。
 * baseline・space-*・safe/unsafe prefixはサブセット外(宣言無効——
 * 黙って捨てない)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum BoxAlignmentValue implements Value {
	AUTO("auto"), NORMAL("normal"), START("start"), CENTER("center"), END("end"), STRETCH("stretch");

	private final String text;

	private BoxAlignmentValue(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return this.text;
	}
}
