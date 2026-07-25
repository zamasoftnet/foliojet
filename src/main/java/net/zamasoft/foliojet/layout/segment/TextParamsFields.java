package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.TextShadow;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;
import net.zamasoft.pdfg2d.gc.text.pipeline.Hyphenator;

/**
 * {@code Params}/{@code AbstractTextParams}が共通して持つフィールドの
 * freeze/materialize処理です(2026-07-22新設、M6d-A3b Stage1、
 * package-private)。
 *
 * <p>
 * 元は{@link LineParamsFields}が単独で持っていたが、{@code InlineParams}
 * (`AbstractTextParams`を直接継承し、`AbstractLineParams`のtext-align等
 * を持たない)のテンプレートを追加する際に、この祖先部分だけを共有する
 * 必要が生じたため切り出した(継承階層の重複実装を避ける、real
 * duplicationの解消——2つ目の具体的な必要が生じてからの抽出)。
 * {@link LineParamsFields}はこれを合成して、line固有の4フィールド
 * (textAlign/textAlignLast/textIndent/lineHeight)を追加で持つ。
 * </p>
 *
 * <p>
 * 祖先({@code Params})のフィールドは{@link ParamsFields}
 * (`InnerTableParamsTemplate`とも共有する)へ委譲する(合成、
 * さらに2つ目の具体的な必要が生じたため2026-07-22に抽出)。
 * </p>
 *
 * <p>
 * {@code textShadows}(配列、mutableな参照)はコンパクトコンストラクタで
 * freeze時に{@code clone()}し、{@link #materializeInto}呼び出しごとに
 * 新品の{@code clone()}を書き戻す(2026-07-22 Stage2、不変recordへ置換)。
 * </p>
 */
record TextParamsFields(ParamsFields common, FontStyle fontStyle, WritingMode flow, byte direction,
		FontManager fontManager, LineBreakRules lineBreakRules, Length letterSpacing, double wordSpacing,
		byte textTransform, byte whiteSpace, byte wordWrap, byte textWrapStyle, byte hyphens,
		Hyphenator hyphenator, Color color, byte decoration, double decorationThickness, double textStrokeWidth,
		Color textStrokeColor,
		TextShadow[] textShadows, byte rubyRole) {
	TextParamsFields {
		// 配列参照自体がmutableなため、freeze時にclone()する(要素の
		// TextShadowはfinalフィールドのみで実質不変)
		textShadows = textShadows == null ? null : textShadows.clone();
	}

	static TextParamsFields freeze(final AbstractTextParams source) {
		return new TextParamsFields(ParamsFields.freeze(source), source.fontStyle, source.flow, source.direction,
				source.fontManager, source.lineBreakRules, source.letterSpacing, source.wordSpacing,
				source.textTransform, source.whiteSpace, source.wordWrap, source.textWrapStyle, source.hyphens,
				source.hyphenator, source.color, source.decoration, source.decorationThickness,
				source.textStrokeWidth,
				source.textStrokeColor, source.textShadows, source.rubyRole);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。呼び出しごとに新品の
	 * {@code AffineTransform}/{@code TextShadow[]}を割り当てるため、
	 * 複数回materializeしても互いに影響しない(M6d-Aの最重要契約)。
	 */
	void materializeInto(final AbstractTextParams target) {
		this.common.materializeInto(target);
		target.fontStyle = this.fontStyle;
		target.flow = this.flow;
		target.direction = this.direction;
		target.fontManager = this.fontManager;
		target.lineBreakRules = this.lineBreakRules;
		target.letterSpacing = this.letterSpacing;
		target.wordSpacing = this.wordSpacing;
		target.textTransform = this.textTransform;
		target.whiteSpace = this.whiteSpace;
		target.wordWrap = this.wordWrap;
		target.textWrapStyle = this.textWrapStyle;
		target.hyphens = this.hyphens;
		target.hyphenator = this.hyphenator;
		target.color = this.color;
		target.decoration = this.decoration;
		target.decorationThickness = this.decorationThickness;
		target.textStrokeWidth = this.textStrokeWidth;
		target.textStrokeColor = this.textStrokeColor;
		target.textShadows = this.textShadows == null ? null : this.textShadows.clone();
		target.rubyRole = this.rubyRole;
	}
}
