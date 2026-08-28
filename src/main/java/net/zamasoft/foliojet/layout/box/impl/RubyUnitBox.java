package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.css.value.RubyAlignValue;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * ルビ1単位です(注釈付きテキスト方式、2026-07-25新設——仕様裁定は
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md)。
 *
 * <p>
 * 親文字列+ふりがな文字列のペアを、行内で分割不可のatomic inline
 * (インラインブロック扱い)として組みます。幅はmax(親文字幅,
 * ふりがな幅)で、狭い方は単位内で均等配置。ふりがなは親の半分の
 * フォントサイズで、横書きは行の上側、縦書きは行の右側に付きます。
 * </p>
 *
 * <p>
 * 単位の寸法(=行高への寄与)は<b>親文字のみ</b>です(2026-07-25
 * 仕様修正——F-2品質確認で「ルビを含む行だけ行送りが広がる」ことが
 * 日本語組版の原則(行送り一定、ルビは行間に置く)に反すると裁定
 * された)。ふりがなは箱の外(横書きは上端の上、縦書きは右端の右=
 * 行間の余白)へはみ出して描かれます。行間の確保はデザイナー責任
 * (ルビを使う文書はline-heightを広めに取る)。基底線は
 * {@code TextBuilder}のBLOCK経路が{@code getLastDescent()}で親文字の
 * 基底線に合わせるため、行送りは周囲のテキストと完全に一致します。
 * </p>
 *
 * <p>
 * 中身は子ボックスではなく、構築時に整形済みのグリフ列(親文字+
 * 半サイズの注釈)を自前で描画します。コンテナは空のまま
 * ({@code InlineBlockBox}の分割・finishLayout等の既存機構と衝突
 * しない)。
 * </p>
 *
 * <p>
 * {@code params.element}は<b>null</b>です。この箱はDOM要素に対応する
 * 箱ではなく、ルビ範囲の文字から合成されたものだからです。ルビ要素
 * 自身のidentity(id・ハイパーリンク・Tagged PDFロール)は、通常の
 * インラインとして残る外側の{@code InlineBox}が持ちます
 * (codex独立レビュー 2026-07-25の設計裁定(d))。rb/rt個別の
 * アンカーやPDFのRuby/RB/RT構造型は、将来の専用メタデータの課題です。
 * </p>
 */
public class RubyUnitBox extends InlineBlockBox {

	/** 単位内の均等配置で許容する最小の余りです(これ未満は配分しない)。 */
	private static final double DISTRIBUTE_EPSILON = 0.0001;

	private final TextImpl[] baseTexts;

	/** 複数段・両側を含む注釈です。 */
	private final RubyAnnotation[] annotations;

	/** 親文字の色です(nullなら継承色のまま)。 */
	private final Color baseColor;

	/**
	 * 書字方向です。ふりがなを置く側(=行の「上」側)の決定に使います。
	 * 縦書き({@link WritingMode#RL}/{@link WritingMode#LR})では、本エンジンは
	 * 基底線の左に descent・右に ascent を取る({@code TextBuilder}の
	 * 縦書き経路がRL/LRを同一に扱う)ため、どちらも<b>+x側</b>が文字の
	 * 上側になります。ふりがなはその上側=+x方向へ置きます。
	 */
	private final WritingMode flow;

	/** 親文字の基底線上側(縦書きは右側)の寸法です。 */
	private final double baseAscent;

	/** 親文字の基底線下側(縦書きは左側)の寸法です。 */
	private final double baseDescent;

	/** 注釈の整列用仮想幅と、実際のatomic inline幅との差。 */
	private final double annotationOrigin;

	/** 左右(縦組では行頭・行末)へ許した張り出し量。 */
	private final double startHang, endHang;

	/** 行頭側を予約した際に、親文字と注釈を箱内へ戻す移動量。 */
	private double contentShift = 0;

	private boolean startHangReserved = false, endHangReserved = false;

	/** テキスト抽出・禁則判定用の文字列(親文字、無ければふりがな)です。 */
	private final String text;

	/**
	 * この単位が消費したソース文字の範囲です(生成内容など出所が無ければ
	 * どちらも-1)。改ページの部分再生で「単位の途中から再開しない」ことを
	 * 保証するために使います({@code AbstractTextBox.lastCharEnd()}/
	 * {@code firstCharOffset()}・配達済み終端の前進)。
	 */
	private final int sourceStart, sourceEnd;

	private RubyUnitBox(final BlockParams params, final InlinePos pos, final RubyUnitContainer container,
			final TextImpl[] baseTexts, final RubyAnnotation[] annotations, final Color baseColor,
			final WritingMode flow, final double lineExtent, final double baseAscent, final double baseDescent,
			final double annotationOrigin, final double startHang, final double endHang, final String text,
			final int sourceStart, final int sourceEnd) {
		super(params, pos, Dimension.AUTO_DIMENSION, Dimension.ZERO_DIMENSION,
				new AbsoluteRectFrame(RectFrame.NULL_FRAME), container);
		this.baseTexts = baseTexts;
		this.annotations = annotations;
		this.baseColor = baseColor;
		this.flow = flow;
		this.baseAscent = baseAscent;
		this.baseDescent = baseDescent;
		this.annotationOrigin = annotationOrigin;
		this.startHang = startHang;
		this.endHang = endHang;
		this.text = text;
		this.sourceStart = sourceStart;
		this.sourceEnd = sourceEnd;
		// ページ方向の寸法=親文字のみ(仕様修正2026-07-25: 行送り一定。
		// ふりがなは箱の外——行間の余白——へはみ出して描く)
		final double pageExtent = baseDescent + baseAscent;
		if (flow.isVertical()) {
			// 縦書き: 行方向=縦、ページ方向=横。左からbaseDescent, 基底線,
			// baseAscent。ふりがな列は右端の右外
			this.width = pageExtent;
			this.height = lineExtent;
		} else {
			// 横書き: 上から親文字のascent、基底線、親文字のdescent。
			// ふりがな行は上端の上外
			this.width = lineExtent;
			this.height = pageExtent;
		}
		container.setup(baseAscent, baseDescent);
	}

	/**
	 * 行頭側の隣接字形と衝突しうるため、張り出しを箱内へ予約します。
	 * quadが下流へ渡る前に呼ぶことを想定します。
	 */
	public void reserveStartOverhang() {
		if (this.startHangReserved || this.startHang <= 0) {
			return;
		}
		this.startHangReserved = true;
		this.contentShift += this.startHang;
		if (this.flow.isVertical()) {
			this.height += this.startHang;
		} else {
			this.width += this.startHang;
		}
	}

	/** 行末側の隣接字形と衝突しうるため、張り出し分を幅へ戻します。 */
	public void reserveEndOverhang() {
		if (this.endHangReserved || this.endHang <= 0) {
			return;
		}
		this.endHangReserved = true;
		if (this.flow.isVertical()) {
			this.height += this.endHang;
		} else {
			this.width += this.endHang;
		}
	}

	/**
	 * 常にtrueです。ルビ単位は構築時に整形済みで寸法が確定しており、
	 * shrink-to-fitの実測(ネストしたビルダー)を必要としません。
	 */
	public boolean isPreMeasured() {
		return true;
	}

	/**
	 * この単位が消費したソース文字の先頭オフセットです(無ければ-1)。
	 */
	public int getSourceStart() {
		return this.sourceStart;
	}

	/**
	 * この単位が消費したソース文字の終端(exclusive)です(無ければ-1)。
	 */
	public int getSourceEnd() {
		return this.sourceEnd;
	}

	/** コレクタから渡す注釈入力。levelは0始まりです。 */
	public record AnnotationInput(String text, InlineParams params, int charOffset, int level) {
	}

	private static final class RubyAnnotation {
		final TextImpl[] texts;
		final Color color;
		final boolean over;
		final boolean interCharacter;
		final double ascent, descent, advance;

		RubyAnnotation(final TextImpl[] texts, final Color color, final boolean over, final boolean interCharacter) {
			this.texts = texts;
			this.color = color;
			this.over = over;
			this.interCharacter = interCharacter;
			this.ascent = texts.length == 0 ? 0 : maxAscent(texts);
			this.descent = texts.length == 0 ? 0 : maxDescent(texts);
			this.advance = totalAdvance(texts);
		}
	}

	/** 親文字と0個以上の注釈レベルからatomic inlineを組み立てます。 */
	public static RubyUnitBox create(final InlineParams container, final String baseText, final InlineParams baseParams,
			final int baseOffset, final List<AnnotationInput> annotationInputs, final int sourceStart,
			final int sourceEnd) {
		if (baseText.isEmpty() && annotationInputs.isEmpty()) {
			return null;
		}
		final InlineParams bp = baseParams == null ? container : baseParams;
		final FontStyle baseFs = bp.fontStyle;
		final TextImpl[] baseTexts = shape(bp, baseFs, baseText, baseOffset);
		final double baseAdvance = totalAdvance(baseTexts);

		final List<RubyAnnotation> built = new ArrayList<RubyAnnotation>();
		double visualExtent = baseAdvance;
		String fallbackText = "";
		boolean overhang = container.rubyOverhang;
		for (final AnnotationInput input : annotationInputs) {
			final InlineParams rp = input.params() == null ? container : input.params();
			final FontStyle rubyBaseFs = rp.fontStyle;
			final FontStyle rubyFs = new FontStyleImpl(rubyBaseFs.getFamily(), baseFs.getSize() / 2.0,
					rubyBaseFs.getStyle(), rubyBaseFs.getWeight(), rubyBaseFs.getDirection(), rubyBaseFs.getPolicy(),
					rubyBaseFs.getFeatures(), rubyBaseFs.getSynthesisWeight(), rubyBaseFs.getSynthesisStyle(),
					rubyBaseFs.getTextOrientation());
			final TextImpl[] texts = input.text().isEmpty() ? new TextImpl[0]
					: shape(rp, rubyFs, input.text(), input.charOffset());
			final boolean interCharacter = !container.flow.isVertical() && rp.rubyPosition.isInterCharacter();
			final RubyAnnotation annotation = new RubyAnnotation(texts, rp.color,
					interCharacter || rp.rubyPosition.isOver(input.level()), interCharacter);
			built.add(annotation);
			if (!interCharacter) {
				visualExtent = Math.max(visualExtent, annotation.advance);
			}
			overhang &= rp.rubyOverhang;
			if (fallbackText.isEmpty()) {
				fallbackText = input.text();
			}
		}
		if (visualExtent <= 0) {
			return null;
		}

		// CSS Rubyは張り出し量をUA裁量とする。JLREQ/JISの上限を越えない
		// よう、注釈フォントの0.5ic(=親文字の0.25em)までを各側へ許す。
		final double desiredHang = Math.max(0, (visualExtent - baseAdvance) / 2.0);
		final double hang = overhang && baseAdvance > 0 ? Math.min(baseFs.getSize() / 4.0, desiredHang) : 0;
		final double lineExtent = Math.max(baseAdvance, visualExtent - hang * 2.0);
		final double annotationOrigin = (lineExtent - visualExtent) / 2.0;

		align(baseTexts, lineExtent - baseAdvance, bp.rubyAlign);
		for (int i = 0; i < built.size(); ++i) {
			final RubyAnnotation annotation = built.get(i);
			if (annotation.interCharacter) {
				continue;
			}
			final InlineParams rp = annotationInputs.get(i).params() == null ? container : annotationInputs.get(i).params();
			align(annotation.texts, visualExtent - annotation.advance, rp.rubyAlign);
		}

		final double baseAscent;
		final double baseDescent;
		if (baseTexts.length > 0) {
			baseAscent = maxAscent(baseTexts);
			baseDescent = maxDescent(baseTexts);
		} else {
			final FontListMetrics baseFlm = bp.fontManager.getFontListMetrics(baseFs);
			baseAscent = baseFlm.getMaxAscent();
			baseDescent = baseFlm.getMaxDescent();
		}

		final BlockParams params = new BlockParams();
		params.element = null;
		params.opacity = bp.opacity;
		params.blendMode = bp.blendMode;
		params.fontStyle = baseFs;
		params.fontManager = bp.fontManager;
		params.lineBreakRules = bp.lineBreakRules;
		params.direction = bp.direction;
		params.flow = container.flow;
		params.color = bp.color;
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.lineHeight = 0;

		final InlinePos pos = new InlinePos();
		pos.lineHeight = 0;
		final String text = baseText.isEmpty() ? fallbackText : baseText;
		return new RubyUnitBox(params, pos, new RubyUnitContainer(), baseTexts,
				built.toArray(RubyAnnotation[]::new), bp.color, container.flow, lineExtent, baseAscent, baseDescent,
				annotationOrigin, hang, hang, text, sourceStart, sourceEnd);
	}

	/** 自己完結整形です(2026-08-01にRunCollector+TrimmedRunsへ一本化)。 */
	private static TextImpl[] shape(final InlineParams src, final FontStyle fontStyle, final String text,
			final int charOffset) {
		return net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(src.fontManager, fontStyle, text,
				charOffset, src.textSpacingTrimOff);
	}


	private static double totalAdvance(final TextImpl[] texts) {
		double advance = 0;
		for (final TextImpl text : texts) {
			advance += text.getAdvance();
		}
		return advance;
	}

	private static double maxAscent(final TextImpl[] texts) {
		double ascent = 0;
		for (final TextImpl text : texts) {
			ascent = Math.max(ascent, text.getAscent());
		}
		return ascent;
	}

	private static double maxDescent(final TextImpl[] texts) {
		double descent = 0;
		for (final TextImpl text : texts) {
			descent = Math.max(descent, text.getDescent());
		}
		return descent;
	}

	/** CSS {@code ruby-align}に従って、列の余りをグリフ前進量へ配分します。 */
	private static void align(final TextImpl[] texts, final double extra, final RubyAlignValue alignment) {
		if (extra <= DISTRIBUTE_EPSILON || alignment == RubyAlignValue.START) {
			return;
		}
		int glyphCount = 0;
		for (final TextImpl text : texts) {
			glyphCount += text.getGlyphCount();
		}
		if (glyphCount <= 0) {
			return;
		}
		final double per;
		if (alignment == RubyAlignValue.CENTER || glyphCount == 1) {
			per = 0;
		} else if (alignment == RubyAlignValue.SPACE_BETWEEN) {
			per = extra / (glyphCount - 1);
		} else {
			per = extra / glyphCount;
		}
		int glyph = 0;
		for (final TextImpl text : texts) {
			text.resetXAdvances();
			for (int i = 0; i < text.getGlyphCount(); ++i) {
				final double before;
				if (alignment == RubyAlignValue.CENTER || glyphCount == 1) {
					before = glyph == 0 ? extra / 2.0 : 0;
				} else if (alignment == RubyAlignValue.SPACE_BETWEEN) {
					before = glyph == 0 ? 0 : per;
				} else {
					before = glyph == 0 ? per / 2.0 : per;
				}
				if (before != 0) {
					text.addXAdvance(i, before);
				}
				++glyph;
			}
		}
	}

	/**
	 * テキスト抽出で<b>親文字</b>を返します(ふりがなは読みの注釈であり
	 * 本文ではないため出さない——リンクの代替テキスト・string-setの
	 * content()・ブックマーク見出し・target-text()に共通の方針)。
	 * 親文字が無い単位(malformed)だけはふりがなを本文の代わりに出します。
	 *
	 * <p>
	 * コンテナ({@code FlowContainer})は空なので、抽出はこの上書きだけが
	 * 担います。
	 * </p>
	 */
	public void pushGetTextSteps(final StringBuilder textBuff, final java.util.Deque<GetTextStep> worklist) {
		textBuff.append(this.text);
	}

	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			AffineTransform transform, final double contextX, final double contextY, double x, double y,
			final java.util.Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		visitor.visitBox(transform, this, drawer, x, y);
		if (this.params.opacity == 0) {
			return;
		}
		drawer.visitDrawable(new RubyUnitDrawable(pageBox, clip, transform, this), x, y);
	}

	/**
	 * 親文字グリフ列+半サイズの注釈グリフ列を描画します。(x, y)は
	 * 単位ボックスの左上です。
	 */
	protected static class RubyUnitDrawable extends AbstractDrawable {
		private final RubyUnitBox box;

		RubyUnitDrawable(final PageBox pageBox, final Shape clip, final AffineTransform transform,
				final RubyUnitBox box) {
			super(pageBox, clip, box.params.opacity, transform);
			this.blendMode = box.params.blendMode;
			this.box = box;
		}

		public String describe() {
			final StringBuilder base = new StringBuilder();
			for (final TextImpl text : this.box.baseTexts) {
				base.append(text.getChars(), 0, text.getCharCount());
			}
			final StringBuilder ruby = new StringBuilder();
			if (this.box.annotations.length > 0) {
				appendText(ruby, this.box.annotations[0].texts);
			}
			final StringBuilder extra = new StringBuilder();
			for (int i = 1; i < this.box.annotations.length; ++i) {
				final RubyAnnotation annotation = this.box.annotations[i];
				final StringBuilder value = new StringBuilder();
				appendText(value, annotation.texts);
				extra.append(" ruby").append(i + 1).append(annotation.over ? "-over=\"" : "-under=\"")
						.append(value).append('\"');
			}
			return String.format(java.util.Locale.ROOT, "RubyUnit[\"%s\" ruby=\"%s\"%s w=%.2f h=%.2f]", base,
					ruby, extra, this.box.getWidth(), this.box.getHeight());
		}

		private static void appendText(final StringBuilder buff, final TextImpl[] texts) {
			for (final TextImpl text : texts) {
				buff.append(text.getChars(), 0, text.getCharCount());
			}
		}

		public void innerDraw(final GC gc, final double x, final double y) throws GraphicsException {
			final RubyUnitBox box = this.box;
			try (final var gcState = gc.begin()) {
				if (box.flow.isVertical()) {
					// 縦書き: over=右、under=左。複数段は外側へ積む。
					final double baseX = x + box.baseDescent;
					this.drawRun(gc, box.baseTexts, box.baseColor, baseX, y + box.contentShift, true);
					double over = 0, under = 0;
					for (final RubyAnnotation annotation : box.annotations) {
						final double rubyX;
						if (annotation.over) {
							rubyX = x + box.baseDescent + box.baseAscent + over + annotation.descent;
							over += annotation.ascent + annotation.descent;
						} else {
							rubyX = x - under - annotation.ascent;
							under += annotation.ascent + annotation.descent;
						}
						this.drawRun(gc, annotation.texts, annotation.color, rubyX,
								y + box.contentShift + box.annotationOrigin, true);
					}
				} else {
					// 横書き: over=上、under=下。inter-characterは右側へ縦置き。
					double over = 0, under = 0;
					for (final RubyAnnotation annotation : box.annotations) {
						if (annotation.interCharacter) {
							final double rubyX = x + box.contentShift + box.getWidth() + annotation.descent;
							final double rubyY = y + Math.max(0,
									(box.baseAscent + box.baseDescent - annotation.advance) / 2.0);
							this.drawRun(gc, annotation.texts, annotation.color, rubyX, rubyY, true);
						} else if (annotation.over) {
							this.drawRun(gc, annotation.texts, annotation.color,
									x + box.contentShift + box.annotationOrigin,
									y - over - annotation.descent, false);
							over += annotation.ascent + annotation.descent;
						} else {
							this.drawRun(gc, annotation.texts, annotation.color,
									x + box.contentShift + box.annotationOrigin,
									y + box.baseAscent + box.baseDescent + under + annotation.ascent, false);
							under += annotation.ascent + annotation.descent;
						}
					}
					this.drawRun(gc, box.baseTexts, box.baseColor, x + box.contentShift, y + box.baseAscent, false);
				}
			}
		}

		private void drawRun(final GC gc, final TextImpl[] texts, final Color color, final double x, final double y,
				final boolean vertical) throws GraphicsException {
			if (texts.length == 0) {
				return;
			}
			try (final var gcState = gc.begin()) {
				if (color != null) {
					gc.setFillPaint(color);
				}
				double xx = x, yy = y;
				for (final TextImpl text : texts) {
					gc.drawText(text, xx, yy);
					if (vertical) {
						yy += text.getAdvance();
					} else {
						xx += text.getAdvance();
					}
				}
			}
		}
	}

	/**
	 * 空のコンテナです。基底線
	 * ({@code getFirstAscent()}/{@code getLastDescent()})は単位の親文字
	 * 基底線を返します(インラインブロックの既存機構——
	 * {@code TextBuilder}のBLOCK経路——がそのまま基底線を合わせられる
	 * ように)。
	 */
	protected static class RubyUnitContainer extends FlowContainer {
		private double firstAscent, lastDescent;

		void setup(final double firstAscent, final double lastDescent) {
			this.firstAscent = firstAscent;
			this.lastDescent = lastDescent;
		}

		public double getFirstAscent() {
			return this.firstAscent;
		}

		public double getLastDescent() {
			return this.lastDescent;
		}
	}
}
