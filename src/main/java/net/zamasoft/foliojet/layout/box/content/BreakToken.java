package net.zamasoft.foliojet.layout.box.content;

/**
 * テキストブロックの継続トークン(BreakToken)です。
 * ページ・段の切断や、同一フロー内の後続テキストブロックの再開情報を表します。
 *
 * <p>
 * M6b: enum から位置を運べる sealed 型に拡張。None &lt; MidFlow &lt; MidLine
 * の全順序で、合成は強い方を採ります。MidFlow/MidLine の charOffset は
 * 再開位置のソース文字オフセット(pdfg2d Text.getCharOffset 由来)で、
 * セグメント再駆動(segment-restyle)の再開位置の対応付けに使います。
 * 位置が不明な合成トークンは -1 を持ちます。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface BreakToken {
	/** フローの先頭(text-indent と :first-line が適用される)。 */
	public static final BreakToken NONE = new None();

	/** 位置不明のフロー途中継続(合成用)。 */
	public static final BreakToken MID_FLOW = new MidFlow(-1);

	record None() implements BreakToken {
	}

	/**
	 * フローの途中からの継続(text-indent と :first-line を抑制)。
	 *
	 * @param charOffset 再開位置のソース文字オフセット(不明なら -1)
	 */
	record MidFlow(int charOffset) implements BreakToken {
	}

	/**
	 * 行の途中(語中)からの継続(前の行と接続して折り返す)。
	 *
	 * @param charOffset 再開位置のソース文字オフセット(不明なら -1)
	 */
	record MidLine(int charOffset) implements BreakToken {
	}

	/**
	 * フロー途中の継続であればtrueを返します。
	 *
	 * @return フロー途中であればtrue
	 */
	public default boolean midFlow() {
		return !(this instanceof None);
	}

	/**
	 * 行途中の継続であればtrueを返します。
	 *
	 * @return 行途中であればtrue
	 */
	public default boolean midLine() {
		return this instanceof MidLine;
	}

	private int rank() {
		return switch (this) {
		case None none -> 0;
		case MidFlow midFlow -> 1;
		case MidLine midLine -> 2;
		};
	}

	/**
	 * 2つの継続状態を合成します(強い方を採ります)。
	 *
	 * @param other 合成する継続状態
	 * @return 合成結果
	 */
	public default BreakToken combine(BreakToken other) {
		return this.rank() >= other.rank() ? this : other;
	}
}
