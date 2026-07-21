package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.Length;

/**
 * {@code AbstractLineParams}が{@code AbstractTextParams}に加えて持つ
 * line固有フィールド(textAlign/textAlignLast/textIndent/lineHeight)の
 * freeze/materialize処理です(2026-07-22新設、M6d-A3b Stage1、
 * package-private——{@link BlockParamsTemplate}と
 * {@link FirstLineParamsTemplate}の内部実装として共有する)。
 *
 * <p>
 * {@code Params}/{@code AbstractTextParams}の共通祖先フィールドは
 * {@link TextParamsFields}に委譲する(合成)——{@code InlineParams}
 * (`AbstractTextParams`を直接継承、line固有フィールドを持たない)の
 * テンプレートが同じ祖先部分を必要としたため、重複を避けてそちらへ
 * 切り出した。
 * </p>
 *
 * <p>
 * ここで扱うのはこの段階(Stage1)の対象:「入力を再帰的にdeep copyした
 * 非公開テンプレートとしてfreezeし、再生ごとに新品のlegacy
 * {@code Params}へmaterializeする」実装(codex設計相談で確認)。
 * テンプレート自体を不変recordへ置換するのはStage2(未着手)。
 * フィールド分類の根拠は{@link TextParamsFields}のjavadocを参照。
 * </p>
 */
final class LineParamsFields {
	private final TextParamsFields text;
	final byte textAlign;
	final byte textAlignLast;
	final Length textIndent;
	final double lineHeight;

	private LineParamsFields(final TextParamsFields text, final byte textAlign, final byte textAlignLast,
			final Length textIndent, final double lineHeight) {
		this.text = text;
		this.textAlign = textAlign;
		this.textAlignLast = textAlignLast;
		this.textIndent = textIndent;
		this.lineHeight = lineHeight;
	}

	static LineParamsFields freeze(final AbstractLineParams source) {
		return new LineParamsFields(TextParamsFields.freeze(source), source.textAlign, source.textAlignLast,
				source.textIndent, source.lineHeight);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。呼び出しごとに新品の
	 * {@code AffineTransform}/{@code TextShadow[]}を割り当てるため、
	 * 複数回materializeしても互いに影響しない(M6d-Aの最重要契約)。
	 */
	void materializeInto(final AbstractLineParams target) {
		this.text.materializeInto(target);
		target.textAlign = this.textAlign;
		target.textAlignLast = this.textAlignLast;
		target.textIndent = this.textIndent;
		target.lineHeight = this.lineHeight;
	}
}
