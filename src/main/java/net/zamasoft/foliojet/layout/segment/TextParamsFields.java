package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
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
 * フィールド分類の根拠は{@link LineParamsFields}のjavadocを参照。
 * </p>
 */
final class TextParamsFields {
	final CSSElement element;
	final int zIndexValue;
	final byte zIndexType;
	final float opacity;
	private final AffineTransform transform;
	final Offset transformOrigin;
	final FontStyle fontStyle;
	final WritingMode flow;
	final byte direction;
	final FontManager fontManager;
	final LineBreakRules lineBreakRules;
	final Length letterSpacing;
	final double wordSpacing;
	final byte textTransform;
	final byte whiteSpace;
	final byte wordWrap;
	final byte hyphens;
	final Hyphenator hyphenator;
	final Color color;
	final byte decoration;
	final double decorationThickness;
	final double textStrokeWidth;
	final Color textStrokeColor;
	private final TextShadow[] textShadows;

	private TextParamsFields(final CSSElement element, final int zIndexValue, final byte zIndexType,
			final float opacity, final AffineTransform transform, final Offset transformOrigin,
			final FontStyle fontStyle, final WritingMode flow, final byte direction, final FontManager fontManager,
			final LineBreakRules lineBreakRules, final Length letterSpacing, final double wordSpacing,
			final byte textTransform, final byte whiteSpace, final byte wordWrap, final byte hyphens,
			final Hyphenator hyphenator, final Color color, final byte decoration, final double decorationThickness,
			final double textStrokeWidth, final Color textStrokeColor, final TextShadow[] textShadows) {
		this.element = element;
		this.zIndexValue = zIndexValue;
		this.zIndexType = zIndexType;
		this.opacity = opacity;
		// 防御的コピー(freeze時に一度だけ)。AffineTransformはmutableなJDK
		// クラスのため、呼び出し元の元インスタンスをそのまま握るとwrite-once
		// 契約が壊れる
		this.transform = new AffineTransform(transform);
		this.transformOrigin = transformOrigin;
		this.fontStyle = fontStyle;
		this.flow = flow;
		this.direction = direction;
		this.fontManager = fontManager;
		this.lineBreakRules = lineBreakRules;
		this.letterSpacing = letterSpacing;
		this.wordSpacing = wordSpacing;
		this.textTransform = textTransform;
		this.whiteSpace = whiteSpace;
		this.wordWrap = wordWrap;
		this.hyphens = hyphens;
		this.hyphenator = hyphenator;
		this.color = color;
		this.decoration = decoration;
		this.decorationThickness = decorationThickness;
		this.textStrokeWidth = textStrokeWidth;
		this.textStrokeColor = textStrokeColor;
		// 配列参照自体がmutableなため、freeze時にclone()する(要素の
		// TextShadowはfinalフィールドのみで実質不変)
		this.textShadows = textShadows == null ? null : textShadows.clone();
	}

	static TextParamsFields freeze(final AbstractTextParams source) {
		return new TextParamsFields(source.element, source.zIndexValue, source.zIndexType, source.opacity,
				source.transform, source.transformOrigin, source.fontStyle, source.flow, source.direction,
				source.fontManager, source.lineBreakRules, source.letterSpacing, source.wordSpacing,
				source.textTransform, source.whiteSpace, source.wordWrap, source.hyphens, source.hyphenator,
				source.color, source.decoration, source.decorationThickness, source.textStrokeWidth,
				source.textStrokeColor, source.textShadows);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。呼び出しごとに新品の
	 * {@code AffineTransform}/{@code TextShadow[]}を割り当てるため、
	 * 複数回materializeしても互いに影響しない(M6d-Aの最重要契約)。
	 */
	void materializeInto(final AbstractTextParams target) {
		target.element = this.element;
		target.zIndexValue = this.zIndexValue;
		target.zIndexType = this.zIndexType;
		target.opacity = this.opacity;
		target.transform = new AffineTransform(this.transform);
		target.transformOrigin = this.transformOrigin;
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
		target.hyphens = this.hyphens;
		target.hyphenator = this.hyphenator;
		target.color = this.color;
		target.decoration = this.decoration;
		target.decorationThickness = this.decorationThickness;
		target.textStrokeWidth = this.textStrokeWidth;
		target.textStrokeColor = this.textStrokeColor;
		target.textShadows = this.textShadows == null ? null : this.textShadows.clone();
	}
}
