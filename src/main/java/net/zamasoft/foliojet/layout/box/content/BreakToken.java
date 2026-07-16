package net.zamasoft.foliojet.layout.box.content;

/**
 * テキストブロックの継続状態(BreakToken)です。
 * ページ・段の切断や、同一フロー内の後続テキストブロックの再開情報を表します。
 * NONE &lt; MID_FLOW &lt; MID_LINE の全順序で、合成は強い方を採ります。
 *
 * @author MIYABE Tatsuhiko
 */
public enum BreakToken {
	/** フローの先頭(text-indent と :first-line が適用される)。 */
	NONE,
	/** フローの途中からの継続(text-indent と :first-line を抑制)。 */
	MID_FLOW,
	/** 行の途中(語中)からの継続(前の行と接続して折り返す)。 */
	MID_LINE;

	/**
	 * フロー途中の継続であればtrueを返します。
	 *
	 * @return フロー途中であればtrue
	 */
	public boolean midFlow() {
		return this != NONE;
	}

	/**
	 * 行途中の継続であればtrueを返します。
	 *
	 * @return 行途中であればtrue
	 */
	public boolean midLine() {
		return this == MID_LINE;
	}

	/**
	 * 2つの継続状態を合成します(強い方を採ります)。
	 *
	 * @param other 合成する継続状態
	 * @return 合成結果
	 */
	public BreakToken combine(BreakToken other) {
		return this.ordinal() >= other.ordinal() ? this : other;
	}
}
