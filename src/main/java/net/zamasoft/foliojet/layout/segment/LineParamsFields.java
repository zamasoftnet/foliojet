package net.zamasoft.foliojet.layout.segment;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
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
 * {@code Params}/{@code AbstractTextParams}/{@code AbstractLineParams}が
 * 共通して持つフィールドのfreeze/materialize処理です(2026-07-22新設、
 * M6d-A3b Stage1、package-private——{@link BlockParamsTemplate}と
 * {@link FirstLineParamsTemplate}の内部実装として共有する)。
 *
 * <p>
 * ここで扱うのはこの段階(Stage1)の対象:「入力を再帰的にdeep copyした
 * 非公開テンプレートとしてfreezeし、再生ごとに新品のlegacy
 * {@code Params}へmaterializeする」実装(codex設計相談で確認)。
 * テンプレート自体を不変recordへ置換するのはStage2(未着手)。
 * </p>
 *
 * <p>
 * フィールドの分類(codex設計相談で確認した方針どおり):
 * </p>
 * <ul>
 * <li>{@code AffineTransform}: 真にmutableなJDKクラスのため、freeze時に
 * 防御的コピーを取る({@code new AffineTransform(original)})。</li>
 * <li>{@code TextShadow[]}: 配列参照自体がmutableなため、freeze時に
 * {@code clone()}する(要素の{@code TextShadow}自体は{@code final}
 * フィールドのみで実質不変なので、要素の深いコピーは不要)。</li>
 * <li>{@code FontManager}・{@code LineBreakRules}・{@code Hyphenator}:
 * 状態を持たない共有サービスとして扱い、コピーせず参照をそのまま
 * 保持する(codexの「共有可能な不変サービス」分類)。</li>
 * <li>{@code CSSElement}・{@code FontStyle}・{@code Color}・
 * {@code Length}・{@code Offset}・{@code WritingMode}: 既存コードの
 * 実装(finalフィールドのみ・setterなし)を確認済みで実質不変のため、
 * コピーせず参照をそのまま保持する。</li>
 * </ul>
 */
final class LineParamsFields {
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
	final byte textAlign;
	final byte textAlignLast;
	final Length textIndent;
	final double lineHeight;

	private LineParamsFields(final CSSElement element, final int zIndexValue, final byte zIndexType,
			final float opacity, final AffineTransform transform, final Offset transformOrigin,
			final FontStyle fontStyle, final WritingMode flow, final byte direction, final FontManager fontManager,
			final LineBreakRules lineBreakRules, final Length letterSpacing, final double wordSpacing,
			final byte textTransform, final byte whiteSpace, final byte wordWrap, final byte hyphens,
			final Hyphenator hyphenator, final Color color, final byte decoration, final double decorationThickness,
			final double textStrokeWidth, final Color textStrokeColor, final TextShadow[] textShadows,
			final byte textAlign, final byte textAlignLast, final Length textIndent, final double lineHeight) {
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
		this.textAlign = textAlign;
		this.textAlignLast = textAlignLast;
		this.textIndent = textIndent;
		this.lineHeight = lineHeight;
	}

	static LineParamsFields freeze(final AbstractLineParams source) {
		return new LineParamsFields(source.element, source.zIndexValue, source.zIndexType, source.opacity,
				source.transform, source.transformOrigin, source.fontStyle, source.flow, source.direction,
				source.fontManager, source.lineBreakRules, source.letterSpacing, source.wordSpacing,
				source.textTransform, source.whiteSpace, source.wordWrap, source.hyphens, source.hyphenator,
				source.color, source.decoration, source.decorationThickness, source.textStrokeWidth,
				source.textStrokeColor, source.textShadows, source.textAlign, source.textAlignLast, source.textIndent,
				source.lineHeight);
	}

	/**
	 * {@code target}へ全フィールドを書き戻す。呼び出しごとに新品の
	 * {@code AffineTransform}/{@code TextShadow[]}を割り当てるため、
	 * 複数回materializeしても互いに影響しない(M6d-Aの最重要契約)。
	 */
	void materializeInto(final AbstractLineParams target) {
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
		target.textAlign = this.textAlign;
		target.textAlignLast = this.textAlignLast;
		target.textIndent = this.textIndent;
		target.lineHeight = this.lineHeight;
	}
}
