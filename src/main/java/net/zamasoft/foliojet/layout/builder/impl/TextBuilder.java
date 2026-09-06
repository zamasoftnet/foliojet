package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.fragment.BreakOpportunity;

import net.zamasoft.foliojet.layout.box.content.BreakToken;


import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.constraint.FloatExclusion;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.impl.FirstLineBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.LineBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineAbsoluteQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutContext.Flow;
import net.zamasoft.foliojet.layout.constraint.ExclusionSpace;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.Element;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;

/**
 * テキストブロックを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TextBuilder.java 1593 2019-12-03 07:02:17Z miyabe $
 */
public class TextBuilder {

	/**
	 * タブ1つの送り幅を返します(tab-size、2026-08-29)。行頭からの位置
	 * {@code lineAxis}を次のタブ位置(タブ幅の整数倍)まで進める量。
	 * タブ幅が0以下なら進めない(仕様: 0はタブを幅なしにする)。
	 */
	static double tabAdvance(final AbstractTextParams params, final double lineAxis) {
		double width = params.tabSize;
		if (params.tabSizeIsMultiple) {
			width *= params.getFontListMetrics().getFontMetrics(0).getSpaceAdvance();
		}
		if (width <= 0) {
			return 0;
		}
		return width - (lineAxis % width);
	}

	/** 制御文字が属する(最も内側の)テキストパラメータです。 */
	private AbstractTextParams currentTextParams() {
		if (this.textParamStack == null || this.textParamStack.isEmpty()) {
			return this.lineBox.getTextParams();
		}
		return ((InlineBox) this.textParamStack.get(this.textParamStack.size() - 1)).getTextParams();
	}

	/**
	 * 配置されたインラインボックスです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TextBuilder.java 1593 2019-12-03 07:02:17Z miyabe $
	 */
	protected static class Inline {
		public final InlineBox box;
		public double baseline;

		public Inline(InlineBox inline) {
			this.box = inline;
		}
	}

	private final BlockBuilder builder;
	private final boolean paragraphBidiEnabled;
	private final byte paragraphDirection;

	/**
	 * 最も近い祖先の{@code line-clamp}の状態(無ければnull。2026-08-29)。
	 * 行を{@link #addLine}するたびに数え、N行を超えた行は捨てる。
	 */
	private final net.zamasoft.foliojet.layout.builder.LineClampState lineClamp;

	/**
	 * 構築中のテキストブロック。
	 */
	TextBlockBox textBlockBox;

	/**
	 * 構築中のインラインボックスのスタック。
	 */
	private List<Inline> inlineStack = null;

	private List<InlineBox> textParamStack = null;

	/**
	 * 行頭、最初、直前での改行のユニットを示すフラグ。
	 */
	private boolean lineHead, firstUnit, last;

	/** この要素の最初の整形行をまだ確定していない。 */
	private boolean firstFormattedLine;

	/**
	 * 次のインラインまたはテキストの追加で改行する
	 */
	private boolean toLineFeed = false;

	/**
	 * スペースのつぶし、折り返し。
	 */
	private boolean collapseSpaces, wrap;

	/**
	 * 単語の分割
	 */
	private byte breakWord;

	private double textIndent, letterSpacing, minLineAxis, maxLineSize, maxPageSize, lastSpaceAdvance;

	private double pageAxis = 0;

	private double lineAxis = 0;

	private AbstractLineBox lineBox;

	private TextImpl text = null;

	private List<Element> textBuffer = new ArrayList<Element>();

	private double unitAdvance = 0;

	/**
	 * 最後に収まった分割機会。
	 */
	private BreakOpportunity opportunity = BreakOpportunity.NONE;

	/**
	 * Knuth-Plass行分割({@code text-wrap-style: pretty})の選択済み
	 * breakpoint列です(2026-07-23、M3c増分3)。{@link TotalFitSession}の
	 * 最適化再生中のみ非null——このインスタンスに束縛され、行間改ページで
	 * 新しいTextBuilderに差し替わった場合は引き継がれない(残りはlegacyの
	 * 貪欲法で組まれる)。既定(legacy)経路では常にnullで挙動不変。
	 */
	TotalFitProjection.Plan totalFitPlan = null;

	public TextBuilder(BlockBuilder builder, BreakToken breakToken) {
		this.builder = builder;
		this.lineClamp = net.zamasoft.foliojet.layout.builder.LineClampState.find(builder);
		final Flow flow = builder.getFlow();
		final BlockParams params = flow.box.getBlockParams();
		this.paragraphBidiEnabled = builder.paragraphBidiEnabled();
		this.paragraphDirection = params.direction;
		this.textBlockBox = new TextBlockBox(params, breakToken);

		if (!breakToken.midFlow()) {
			this.textIndent = flow.box.getTextIndent();
		} else {
			this.textIndent = 0;
		}
		final AbstractLineBox lineBox;
		if (!breakToken.midFlow() && params.firstLineStyle != null) {
			lineBox = new FirstLineBox(params.firstLineStyle);
		} else {
			lineBox = new LineBox(params);
		}

		this.last = !breakToken.midLine();
		this.firstFormattedLine = !breakToken.midFlow();
		this.lineBox = lineBox;
		this.lineBox.setParagraphBidi(this.paragraphBidiEnabled, this.paragraphDirection);
		this.lineHead = this.firstUnit = true;
		this.lastSpaceAdvance = 0;
		this.changeTextState(params);
	}

	/**
	 * テキストパラメータを切り替えます。
	 * 
	 * @param params
	 */
	private void changeTextState(AbstractTextParams params) {
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.collapseSpaces = false;
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.collapseSpaces = true;
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.collapseSpaces = true;
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.collapseSpaces = true;
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.collapseSpaces = false;
			this.wrap = true;
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.wrap) {
			this.breakWord = params.wordWrap;
		} else {
			this.breakWord = AbstractTextParams.WORD_WRAP_NORMAL;
		}
		this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing, this.builder.getFlowBox().getLineSize());
		// 和文詰めA2/A3/T1b: 実効フラグ・trim policyの追従(インライン境界で
		// 切替わる。pair状態は維持——境界を挟むpairは現在要素の値で判定
		// される)。縦書きも同一機構(gapは論理inline軸のxadvance——A3)
		this.autospace.setFlags(params.textAutospace);
		this.autospace.setTrimOff(params.textSpacingTrimOff);
		// 和文詰めH1: 行末句読点のぶら下げ(hanging-punctuation: allow-end)
		this.hangingEnd = params.hangingPunctuationEnd;

		// System.err.println("CHANGE_TEXT: " + this.wrap + "/" + this.breakWord);
	}

	/**
	 * テキストブロックに行を追加します。
	 */
	/** いまの行に文字(Text・目に見える Control・leader)が入ったか。 */
	private boolean lineHasText = false;

	/** いまの行に画像・インラインブロック(atomic inline)が入ったか。 */
	private boolean lineHasAtomic = false;

	/**
	 * 文字の無い行(画像やインラインブロックだけの行)へ、ブロックのフォントと
	 * line-height の strut(CSS 2.1 §10.8)を入れます(2026-09-02)。
	 *
	 * <p>
	 * 標準モード(DOCTYPE あり)の文書だけ。quirks はブラウザと同じく省く。
	 * 文字のある行は、文字がブロックのフォントの高さを既に持ち込んでいるので触らない
	 * (全行に入れる実装は 77 件の試験を動かした——文字の行の高さの式と strut の式が
	 * 一致しないため。この限定なら文字の行は 1 つも変わらない)。Acid2 の
	 * image-height-test: {@code font: 20em} の行に置いた画像が、strut が無いために
	 * 行の上端に来ていた。
	 * </p>
	 */
	private void addStrutIfTextless(final AbstractLineBox line) {
		if (this.lineHasText || !this.lineHasAtomic) {
			return;
		}
		final AbstractTextParams params = line.getTextParams();
		final double lineHeight = line.getLineParams().lineHeight;
		if (params == null || !params.strictLineBox || LayoutUtils.isNone(lineHeight) || params.fontStyle == null) {
			// quirks(DOCTYPE の無い HTML)はブラウザと同じく strut を省く
			return;
		}
		final AbstractContainerBox flowBox = this.builder.getFlowBox();
		if (flowBox instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox
				|| flowBox instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox) {
			// リストマーカーの箱の中の行(点の画像だけ)は本文の行ではない
			return;
		}
		final double[] strut = strutAscentDescent(params, lineHeight);
		line.addAscentDescent(strut[0], strut[1]);
	}

	/**
	 * ブロックのフォントと line-height から strut の ascent/descent を求めます
	 * ({@link #addStrutIfTextless} と {@link #getVirtualClosedPageAxis} で共用)。
	 */
	private static double[] strutAscentDescent(final AbstractTextParams params, final double lineHeight) {
		double ascent, descent;
		if (params.isVerticalTypesetting()) {
			// 縦組み: 字面は中央線の左右にサイズの半分ずつ
			ascent = descent = params.fontStyle.getSize() / 2.0;
		} else {
			// CSS 2.1 の strut は「最初に使えるフォント」の計量。一覧全体の最大
			// (getMaxAscent)は、フォント索引が温まる途中の並列実行で一覧が欠けると
			// 値が変わりうる(imageTest の msn.htm が 4 回に 1 回だけ違った)ので使わない
			final net.zamasoft.pdfg2d.gc.font.FontListMetrics flm = params.getFontListMetrics();
			final net.zamasoft.pdfg2d.gc.font.FontMetrics[] metrics = flm.metrics();
			if (metrics != null && metrics.length > 0) {
				ascent = metrics[0].getAscent();
				descent = metrics[0].getDescent();
			} else {
				ascent = flm.getMaxAscent();
				descent = flm.getMaxDescent();
			}
		}
		final double textHeight = ascent + descent;
		if (!LayoutUtils.isNone(lineHeight) && lineHeight != textHeight) {
			final double half = (lineHeight - textHeight) / 2.0;
			ascent += half;
			descent += half;
		}
		return new double[] { ascent, descent };
	}

	/** 計量用の行では atomic の圧縮など、入力の箱を書き換える処理を行わない。 */
	private boolean measuringLine;

	/**
	 * 先行内容だけで行を閉じた仮想位置。本文の行・run・inline は変更しない。
	 * 確定時と同じ startInline/addElement を計量用の箱に適用し、ascent/descent を
	 * 個別に最大化する。分綴待ちの単語は字形の計量だけを借り、確定・消費しない。
	 */
	double getVirtualClosedPageAxis(final java.util.function.Consumer<GlyphHandler> pending) {
		final TextBuilder measure = new TextBuilder(this.builder, BreakToken.NONE);
		measure.measuringLine = true;
		measure.lineBox = this.lineBox instanceof FirstLineBox
				? new FirstLineBox((net.zamasoft.foliojet.layout.box.params.FirstLineParams) this.lineBox.getLineParams())
				: new LineBox((BlockParams) this.lineBox.getLineParams());
		measure.lineBox.addAscentDescent(this.lineBox.getAscent(), this.lineBox.getDescent());
		measure.lineHasText = this.lineHasText;
		measure.lineHasAtomic = this.lineHasAtomic;
		measure.pageAxis = this.pageAxis;
		measure.lineAxis = this.lineAxis;
		measure.textIndent = this.textIndent;
		measure.maxLineSize = this.maxLineSize;
		measure.maxPageSize = this.maxPageSize;
		measure.minLineAxis = this.minLineAxis;
		measure.firstUnit = this.firstUnit;
		measure.last = this.last;
		measure.firstFormattedLine = this.firstFormattedLine;
		measure.lineHead = this.lineHead;
		measure.lastSpaceAdvance = this.lastSpaceAdvance;
		measure.opportunity = this.opportunity;
		measure.unitAdvance = this.unitAdvance;
		measure.fontStyle = this.fontStyle == null ? this.builder.getOpenRunFontStyle() : this.fontStyle;
		measure.fontMetrics = this.fontMetrics == null ? this.builder.getOpenRunFontMetrics() : this.fontMetrics;
		measure.textParamStack = this.textParamStack == null ? null : new ArrayList<>(this.textParamStack);
		measure.changeTextState(this.currentTextParams());
		if (this.inlineStack != null) {
			measure.inlineStack = new ArrayList<>();
			for (final Inline inline : this.inlineStack) {
				final Inline copy = new Inline(copyInlineForMeasurement(inline.box));
				copy.baseline = inline.baseline;
				measure.inlineStack.add(copy);
			}
		}
		final List<Element> buffer = new ArrayList<>(this.textBuffer);
		if (this.text != null) {
			measure.text = measureTextSlice(this.text, 0, this.text.getGlyphCount());
			buffer.set(buffer.indexOf(this.text), measure.text);
		}
		measure.textBuffer = buffer;
		measure.autospace.copyFrom(this.autospace, this.text, measure.text);
		pending.accept(new GlyphHandler() {
			public void startTextRun(final int offset, final FontStyle style, final FontMetrics metrics) {
				measure.startTextRun(style, metrics);
			}
			public void glyph(final int offset, final char[] chars, final int off, final byte len, final int gid) {
				measure.glyph(offset, chars, off, len, gid);
			}
			public void endTextRun() {
				if (measure.text != null) measure.endTextRun();
			}
			public void control(final TextControl control) {
				measure.control(measure.copyPendingControl(control));
			}
			public void flush() {
				// 正規の字間の候補だけを記録する。本文や親 builder へ行は追加しない。
				if (!measure.wrap || buffer.isEmpty()) return;
				if (measure.firstUnit && measure.lineAxis > 0) {
					measure.locateLine();
					measure.firstUnit = false;
				}
				if (measure.opportunity.elementCount() == 0
						|| LayoutUtils.compare(measure.lineAxis - measure.lastSpaceAdvance,
								measure.maxLineSize - measure.textIndent) <= 0) {
					measure.opportunity = measure.captureOpportunity();
				}
			}
			public void close() { }
		});
		final double trailingSpace = measure.lastSpaceAdvance;
		// 末尾の配達で溢れが判明しても、本文の行は確定しない。既存の分割候補だけで
		// 仮想的に先行行を閉じる(absolute の位置は新たな候補にならない)。
		int from = 0;
		boolean locate = measure.firstUnit;
		final double overflow = measure.lineAxis - trailingSpace - (measure.maxLineSize - measure.textIndent);
		if (!measure.firstUnit && measure.opportunity.elementCount() > 0 && LayoutUtils.compare(overflow, 0) > 0
				&& !measure.tryJlreqLineShrink(overflow, false)) {
			from = measure.opportunity.elementCount();
			final int glyphs = measure.opportunity.glyphCount();
			if (glyphs > 0 && buffer.get(from - 1) instanceof Text run && glyphs < run.getGlyphCount()) {
				buffer.set(from - 1, measureTextSlice(run, 0, glyphs));
				buffer.add(from, measureTextSlice(run, glyphs, run.getGlyphCount()));
			}
			for (int i = 0; i < from; ++i) {
				measure.measureElement(buffer.get(i));
			}
			measure.addStrutIfTextless(measure.lineBox);
			measure.pageAxis += measure.lineBox.getPageSize();
			measure.lineBox = new LineBox(this.textBlockBox.getBlockParams());
			measure.lineHasText = measure.lineHasAtomic = false;
			final List<Inline> inlines = measure.inlineStack;
			measure.inlineStack = null;
			if (inlines != null) {
				for (final Inline inline : inlines) {
					final InlineBox continued = inline.box.splitLine(true);
					continued.fixLineAxis(this.builder.getFlowBox());
					measure.startInline(continued);
				}
			}
			if (measure.collapseSpaces) {
				while (from < buffer.size() && buffer.get(from) instanceof WhiteSpace) {
					++from;
				}
			}
			measure.lineAxis = 0;
			for (int i = from; i < buffer.size(); ++i) {
				measure.lineAxis += buffer.get(i).getAdvance();
			}
			locate = true;
		}
		for (int i = from; i < buffer.size(); ++i) {
			measure.measureElement(buffer.get(i));
		}
		measure.addStrutIfTextless(measure.lineBox);
		if (measure.lineBox.getPageSize() == 0) {
			return measure.pageAxis;
		}
		if (locate) {
			// nowrap/pre はまだ locateLine を通っていない。排除域も同じ探索で読む。
			measure.textBuffer = buffer.subList(from, buffer.size());
			measure.locateLine();
		}
		return measure.pageAxis + measure.lineBox.getPageSize();
	}

	/** 未配達 control は下流で幅が設定されるため、可変なものを複製して計量する。 */
	private TextControl copyPendingControl(final TextControl control) {
		if (control instanceof InlineQuad quad) {
			final WritingMode flow = this.currentTextParams().flow;
			final InlineQuad copy;
			switch (quad.getType()) {
			case InlineQuad.INLINE_START:
			case InlineQuad.INLINE_END: {
				final InlineBox box = copyInlineForMeasurement((InlineBox) quad.getBox());
				final boolean start = quad.getType() == InlineQuad.INLINE_START;
				copy = start ? InlineQuad.createInlineBoxStartQuad(box) : InlineQuad.createInlineBoxEndQuad(box);
				final AbstractTextParams params = box.getTextParams();
				final boolean reverse = params.flow.isVertical() && params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant, params.direction)
								== TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
				copy.advance = start ? (reverse ? box.getFrame().getFrameBottom() : box.getFrame().getFrameLineStart(flow))
						: (reverse ? box.getFrame().getFrameTop() : box.getFrame().getFrameLineEnd(flow));
				break;
			}
			case InlineQuad.INLINE_REPLACED:
				copy = InlineQuad.createReplacedBoxQuad(((InlineReplacedQuad) quad).box);
				copy.advance = quad.getBox().getLineExtent(flow);
				break;
			case InlineQuad.INLINE_BLOCK:
				copy = InlineQuad.createInlineBlockBoxQuad(((InlineQuad.InlineBlockQuad) quad).box);
				copy.advance = quad.getBox().getLineExtent(flow);
				break;
			case InlineQuad.INLINE_ABSOLUTE:
				copy = InlineQuad.createInlineAbsoluteBoxQuad(((InlineAbsoluteQuad) quad).box);
				break;
			default:
				throw new IllegalStateException();
			}
			return copy;
		}
		if (control instanceof WhiteSpace space) {
			final var metrics = this.currentTextParams().getFontListMetrics();
			final WhiteSpace copy = new WhiteSpace(metrics, space.getCharOffset());
			copy.setWordSpacing(space.getAdvance() - metrics.getFontMetrics(0).getSpaceAdvance());
			return copy;
		}
		if (control instanceof Tab tab) {
			return new Tab(this.currentTextParams().getFontListMetrics(), tab.getCharOffset());
		}
		return control;
	}

	private static TextImpl measureTextSlice(final Text run, final int from, final int to) {
		final TextImpl copy = new TextImpl(run.getCharOffset(), run.getFontStyle(), run.getFontMetrics());
		copy.setLetterSpacing(run.getLetterSpacing());
		int charOffset = 0;
		for (int i = 0; i < to; ++i) {
			final byte length = run.getClusterLengths()[i];
			if (i >= from) {
				copy.appendGlyph(run.getChars(), charOffset, length, run.getGlyphIds()[i]);
				if (run.xAdvances() != null) {
					copy.addXAdvance(i - from, run.xAdvances().get(i));
				}
			}
			charOffset += length;
		}
		return copy;
	}

	private static InlineBox copyInlineForMeasurement(final InlineBox box) {
		final InlineBox copy = new InlineBox(box.getInlineParams(), box.getInlinePos());
		copy.getFrame().frame = box.getFrame().frame;
		copy.getFrame().margin.set(box.getFrame().margin);
		copy.getFrame().padding.set(box.getFrame().padding);
		// addAscentDescent は枠を加えるので、元の外寸から同じ枠を一度引く。
		final var frame = box.getFrame();
		if (box.getTextParams().flow.isVertical()) {
			final boolean sideways = box.getTextParams().writingModeVariant == WritingModeVariant.SIDEWAYS_CCW;
			copy.addAscentDescent(box.getAscent() - (sideways ? frame.getFrameLeft() : frame.getFrameRight()),
					box.getDescent() - (sideways ? frame.getFrameRight() : frame.getFrameLeft()));
		} else {
			copy.addAscentDescent(box.getAscent() - frame.getFrameTop(), box.getDescent() - frame.getFrameBottom());
		}
		return copy;
	}

	private void measureElement(final Element element) {
		if (element instanceof InlineQuad quad) {
			switch (quad.getType()) {
			case InlineQuad.INLINE_START:
				this.startInline(copyInlineForMeasurement((InlineBox) quad.getBox()));
				break;
			case InlineQuad.INLINE_END:
				this.endInline();
				break;
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_REPLACED:
				this.startInline((IInlineBox) quad.getBox());
				this.endInline();
				break;
			case InlineQuad.INLINE_ABSOLUTE:
				break;
			default:
				throw new IllegalStateException();
			}
		} else if (element instanceof Control control && control.getControlChar() == '\n') {
			// 処理済み br の後に新たな行を作らない。保存空白・tab・leader は addElement へ。
		} else {
			this.addElement(element);
		}
	}

	private void addLine(AbstractLineBox lineBox) {
		this.addStrutIfTextless(lineBox);
		this.lineHasText = false;
		this.lineHasAtomic = false;
		if (this.lineClamp != null) {
			if (this.lineClamp.exhausted()) {
				// line-clamp(2026-08-29): N行を超えた行は捨てる(高さにも
				// 描画にも出ない)。ここで初めて「N行目の後に内容がある」と
				// 確定するので、預かっていたN行目の省略記号を付ける
				this.lineClamp.truncatePending();
				return;
			}
			if (this.lineClamp.countLine()) {
				// N行目。後続が無ければそのまま(省略記号なし)なので、
				// 切り方だけ預ける。値は行を閉じた時点のものを固定する
				final double avail = this.maxLineSize, lineStart = this.minLineAxis;
				this.lineClamp.setPending(() -> this.applyLineClampEllipsis(lineBox, lineStart, avail));
			}
		}
		this.textBlockBox.addLine(lineBox, this.pageAxis);
		this.builder.noteBidiLine(this.textBlockBox, lineBox);
		final double pageAdvance = lineBox.getAscent() + lineBox.getDescent();
		this.pageAxis += pageAdvance;
		assert !LayoutUtils.isNone(this.pageAxis);
		if (pageAdvance > 0) {
			this.builder.poLastMargin = this.builder.neLastMargin = 0;
		}
	}

	/**
	 * run内分割境界のpair調整(autospace gap−約物詰め)を再計算します
	 * (T1a——分割で境界が行を跨ぐとき、適用済み調整を逆適用するため。
	 * 調整は純関数のため記録不要で再計算できる。autospaceのflagsは
	 * 現在値で近似——inline切替と旧run内分割が複合した場合のみ不正確、
	 * consultations記録済み)。
	 */
	private double boundaryAdjustment(final TextImpl head, final TextImpl tail) {
		final char[] headChars = head.getChars();
		final int prevCp = Character.codePointBefore(headChars, head.getCharCount());
		final int cp = Character.codePointAt(tail.getChars(), 0);
		final double fontSize = head.getFontStyle().getSize();
		final double gap = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.gapEm(prevCp, cp,
				this.autospace.getFlags()) * fontSize;
		final int prevGid = head.getGlyphIds()[head.getGlyphCount() - 1];
		final int gid = tail.getGlyphIds()[0];
		double trim = 0;
		if (!this.autospace.isTrimOff() && this.fontMetrics.getKerning(prevGid, gid) == 0) {
			final net.zamasoft.pdfg2d.gc.font.FontStyle.Direction direction = head.getFontStyle().getDirection();
			trim = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.pairTrim(prevCp,
					net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(this.fontMetrics,
							prevGid, fontSize, direction),
					cp, net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(this.fontMetrics,
							gid, fontSize, direction))
					* fontSize;
		}
		return gap - trim;
	}

	/**
	 * 行が入らなかったとき、次に試すページ方向位置です。
	 *
	 * <p>
	 * 矩形の浮動体は従来どおり下端へ飛ぶ(そこまで幅は変わらない)。
	 * {@code shape-outside}の形状を持つ浮動体は行の高さぶんだけ下りて
	 * 再探索する——円の下半分では帯が広がっていくので、下端まで飛ばすと
	 * 円の周りを回り込めない(2026-08-29)。前進は必ず1pt以上かつ
	 * 浮動体の下端が上限なので、再探索ループは有限回で終わる。
	 * </p>
	 */
	private static double nextSearchPage(final FloatExclusion exclusion, final double pageStart,
			final double lineHeight) {
		final double pageEnd = exclusion.pageSpan().end();
		if (exclusion.shape() == null) {
			return pageEnd;
		}
		return Math.min(pageEnd, pageStart + Math.max(lineHeight, 1));
	}

	/**
	 * 現在のフラグメントに、ページフロートを避けて最初の1行を置けるか。
	 * ページフロートが無い文脈では従来の行配置へ委ねて常にtrueを返す。
	 */
	static boolean hasFirstLineBandInFragment(final BlockBuilder builder, final double fragmentLimit) {
		if (builder.pageFloatExclusionsForLineLayout().isEmpty()) {
			return true;
		}
		final BlockParams params = builder.getFlowBox().getBlockParams();
		final double lineHeight = !builder.breakToken.midFlow() && params.firstLineStyle != null
				? params.firstLineStyle.lineHeight
				: params.lineHeight;
		if (LayoutUtils.compare(lineHeight, 0) <= 0) {
			return true;
		}
		double pageStart = builder.pageAxis;
		final double lineStart0 = builder.lineAxis;
		final double lineEnd0 = lineStart0 + builder.getFlowBox().getLineSize();
		for (;;) {
			final ExclusionSpace.LineScan found = builder.scanLineBandForLineLayout(pageStart, lineHeight,
					lineStart0, lineEnd0);
			double maxPageSize = fragmentLimit - pageStart;
			if (found.maxPageSizeSet()) {
				maxPageSize = Math.min(maxPageSize, found.maxPageSize());
			}
			if (LayoutUtils.compare(found.lineEnd() - found.lineStart(), lineHeight) >= 0) {
				return LayoutUtils.compare(maxPageSize, lineHeight) >= 0;
			}
			if (found.startExclusion() == null && found.endExclusion() == null) {
				// 包含ブロック自体が1行高より狭い場合は、ページフロートを
				// 理由に改ページし続けず従来のoverflow処理へ委ねる。
				return true;
			}
			if (found.endExclusion() == null) {
				pageStart = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
			} else if (found.startExclusion() == null) {
				pageStart = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
			} else {
				pageStart = Math.min(nextSearchPage(found.startExclusion(), pageStart, lineHeight),
						nextSearchPage(found.endExclusion(), pageStart, lineHeight));
			}
		}
	}

	/**
	 * 現在構築中の行の位置を調整します。
	 */
	private void locateLine() {
		double pageStart = this.builder.pageAxis + this.pageAxis;
		double lineStart = this.builder.lineAxis;
		this.maxPageSize = Double.MAX_VALUE;

		this.maxLineSize = this.builder.getFlowBox().getLineSize();
		if (this.builder.hasLineExclusions()) {
			final double lineHeight = this.lineBox.getLineParams().lineHeight;
			// System.out.println("TB-locateLine1:" + pageStart + "/"
			// + this.builder.floatings.size() + "/" + lineHeight);
			final double lineEnd0 = this.builder.lineAxis + this.maxLineSize;
			// 通常floatとページフロートは別々の不変スナップショットとして
			// 各反復で走査される。
			for (;;) {
				// ラインを入れるスペースがある部分の左右
				final ExclusionSpace.LineScan found = this.builder.scanLineBandForLineLayout(pageStart, lineHeight,
						this.builder.lineAxis, lineEnd0);
				lineStart = found.lineStart();
				if (found.maxPageSizeSet()) {
					// 既存コードはthis.maxPageSizeをこの分岐でしか更新しない
					// ——外側for(;;)の前回反復の値がそのまま残ることがある
					// (2026-07-23、BlockBuilder.addBoundと同型の「ループ間で
					// 状態が持ち越される」実挙動、必ず条件付きでのみ更新する)。
					this.maxPageSize = found.maxPageSize();
				}
				this.maxLineSize = found.lineEnd() - found.lineStart();
				if (LayoutUtils.compare(this.maxLineSize, this.lineAxis) >= 0) {
					// 幅に余裕がある
					break;
				}
				// 余裕がない場合は１つ下りて再探索
				if (found.startExclusion() == null && found.endExclusion() == null) {
					break;
				}
				if (found.endExclusion() == null) {
					pageStart = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
				} else if (found.startExclusion() == null) {
					pageStart = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
				} else {
					double startEnd = nextSearchPage(found.startExclusion(), pageStart, lineHeight);
					double endEnd = nextSearchPage(found.endExclusion(), pageStart, lineHeight);
					if (startEnd > endEnd) {
						pageStart = endEnd;
					} else {
						pageStart = startEnd;
					}
				}
			}
		}

		assert LayoutUtils.compare(pageStart - this.builder.pageAxis, this.pageAxis) >= 0;
		this.pageAxis = pageStart - this.builder.pageAxis;
		assert !LayoutUtils.isNone(this.pageAxis);
		this.minLineAxis = lineStart - this.builder.lineAxis;
		// System.out.println("NewLine:"+lineStart+"/"+this.maxLineSize);

		// 天付き(和文詰めS1/JLREQ cl-01)。横書き・縦書きとも、全角相当の
		// 始め括弧が持つ行頭側の二分アキを行外へ出す。CSS Text 4に従い
		// trim-startだけを天付きにし、normal/space-allはJLREQのもう一つの
		// 選択肢である行頭二分アキを残す。
		final AbstractLineParams lineParams = this.lineBox.getLineParams();
		// space-firstはブロック初行と強制改行直後だけ全角を保ち、
		// 自動折返しで始まった行だけ天付きにする。this.lastは直前の
		// 改行が強制/初行ならtrue、自動折返しならfalseという既存状態。
		final boolean trimStart = lineParams.textSpacingTrimStart
				|| (lineParams.textSpacingSpaceFirst && !this.last);
		for (int i = 0; i < this.textBuffer.size(); ++i) {
			Element e = (Element) this.textBuffer.get(i);
			if (e.getAdvance() == 0) {
				continue;
			}
			if (e instanceof Text) {
				final Text text = (Text) e;
				final double fontSize = text.getFontStyle().getSize();
				final boolean wide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
						.isWide(text.getFontMetrics(), text.getGlyphIds()[0], fontSize,
								text.getFontStyle().getDirection());
				final int firstCodePoint = Character.codePointAt(text.getChars(), 0);
				double headIndent = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
						.lineHeadIndent(firstCodePoint, wide, trimStart) * fontSize;
				if (lineParams.hangingPunctuationFirst && this.firstFormattedLine) {
					headIndent += net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.firstHang(
							firstCodePoint, wide, text.getFontMetrics().getAdvance(text.getGlyphIds()[0]), fontSize,
							trimStart);
				}
				if (headIndent != 0) {
					// ブロック先頭行でも天付きを適用し、作者指定の
					// text-indentは基準位置として保つ。
					this.textIndent += headIndent;
				}
			}
			break;
		}
	}

	/**
	 * 現在のテキストボックスを返します。
	 * 
	 * @return
	 */
	private AbstractTextBox getTextBox() {
		if (this.inlineStack == null || this.inlineStack.isEmpty()) {
			return this.lineBox;
		}
		Inline inline = (Inline) this.inlineStack.get(this.inlineStack.size() - 1);
		return inline.box;
	}

	private void startInline(IInlineBox box) {
		AbstractTextBox textBox = this.getTextBox();
		textBox.addInline(box);

		double baseline;
		if (this.inlineStack == null) {
			this.inlineStack = new ArrayList<Inline>();
			baseline = 0;
		} else if (this.inlineStack.isEmpty()) {
			baseline = 0;
		} else {
			// System.out.println(this.textParamStack.size()+"/"+this.inlineStack
			// .size());
			Inline parentInline = (Inline) this.inlineStack.get(this.inlineStack.size() - 1);
			baseline = parentInline.baseline;
		}

		switch (box.getType()) {
		case INLINE: {
			InlineBox inlineBox = (InlineBox) box;
			InlineParams params = inlineBox.getInlineParams();
			InlinePos pos = box.getInlinePos();
			FontListMetrics flm = params.getFontListMetrics();
			double ascent = flm.getMaxAscent();
			double descent = flm.getMaxDescent();
			inlineBox.addAscentDescent(ascent, descent);

			AbstractTextParams textParams = textBox.getTextParams();
			double start = textParams.flow.isVertical() && textParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(textParams.flow, textParams.writingModeVariant,
							textParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
						? inlineBox.getFrame().getFrameBottom()
						: inlineBox.getFrame().getFrameLineStart(textParams.flow);
			this.lineBox.addAdvance(start);
			inlineBox.addAdvance(start);
			Inline inline = new Inline(inlineBox);
			this.inlineStack.add(inline);

			// baselineの設定
			double verticalAlign = pos.verticalAlign.getVerticalAlign(textBox, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);
			inline.baseline = baseline + verticalAlign;

			if (inlineBox.getFrame().getFrameWidth() > 0) {
				// line-heightの適用
				double lineHeight = pos.lineHeight;
				lineHeight = Math.max(this.lineBox.getLineParams().lineHeight, lineHeight);
				double textHeight = ascent + descent;
				if (lineHeight != textHeight) {
					lineHeight = (lineHeight - textHeight) / 2.0;
					ascent = (ascent + lineHeight);
					descent = (descent + lineHeight);
				}
				ascent = ascent + verticalAlign + baseline;
				descent = descent - verticalAlign - baseline;
				this.lineBox.addAscentDescent(ascent, descent);
			}
		}
			break;

		case REPLACED:
		case BLOCK: {
			final IInlineBox inlineBox = box;
			final AbstractLineParams lineParams = this.lineBox.getLineParams();
			final double advance = inlineBox.getLineExtent(lineParams.flow);
			textBox.addAdvance(advance);
			if (this.lineBox != textBox) {
				this.lineBox.addAdvance(advance);
			}
			this.inlineStack.add(null);

			final InlinePos pos = box.getInlinePos();
			if (pos.verticalAlign instanceof net.zamasoft.foliojet.layout.box.content.CSSVerticalAlignPolicy va
					&& va.getVerticalAlignType() == net.zamasoft.foliojet.layout.box.content.CSSVerticalAlignPolicy.BASELINE
					&& !(box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox)
					&& !(box instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox)) {
				// strut の対象(addStrutIfTextless)。基底線揃えの箱は、後から行の
				// ascent/descent が伸びても基底線の上に留まるので位置が変わらない。
				// top/bottom/middle 揃えは「その時点の行の高さ」を基準に置かれる
				// ため後からの strut でずれる——対象にしない。リストマーカーの箱も除く
				this.lineHasAtomic = true;
			}
			double descent, ascent;
			if (box.getType() == BoxType.BLOCK) {
				// インラインブロック・テーブルの基底線
				final AbstractContainerBox inlineBlockBox = (AbstractContainerBox) box;
				final BlockParams params = inlineBlockBox.getBlockParams();
				if (!this.measuringLine && params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL
						&& lineParams.flow.isVertical() && !params.flow.isVertical()
						&& inlineBlockBox instanceof net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox stf) {
					// **縦中横(all)は1emのセルへ収める**(css-writing-modes-4
					// §9.1.3、2026-08-11)。自然幅で組み終えたこの時点で幅を
					// 1emへ差し替え、内容に水平アフィンを掛ける。行が使う
					// 見かけ幅(=下のascent/descent)もこれで1emになる
					final java.awt.geom.GeneralPath ink = new java.awt.geom.GeneralPath();
					final RootBuilder root = this.builder.getPageContext();
					if (root != null) {
						stf.textShapeQuiet(root.getCurrentPageBox(), ink, new java.awt.geom.AffineTransform(), 0, 0);
					}
					stf.compressTextCombine(params.fontStyle.getSize(), ink.getCurrentPoint() == null ? null
							: ink.getBounds2D());
				}
				switch (lineParams.flow) {
				case WritingMode.TB:
					// 横書き
					if (params.flow == WritingMode.TB) {
						descent = inlineBlockBox.getLastDescent();
						if (LayoutUtils.isNone(descent)) {
							descent = 0;
						}
					} else {
						// 横中縦
						descent = 0;
					}
					ascent = inlineBox.getHeight() - descent;
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					if (params.flow == WritingMode.RL || params.flow == WritingMode.LR) {
						descent = inlineBlockBox.getLastDescent();
						if (LayoutUtils.isNone(descent)) {
							descent = inlineBox.getWidth() / 2.0;
						}
					} else {
						// 縦中横
						descent = inlineBox.getWidth() / 2.0;
					}
					ascent = inlineBox.getWidth() - descent;
					break;
				default:
					throw new IllegalStateException();
				}
			} else {
				// 画像の基底線
				switch (lineParams.flow) {
				case WritingMode.TB:
					// 横書き
					ascent = box.getHeight();
					descent = 0;
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					ascent = box.getWidth();
					descent = ascent = ascent / 2.0;
					break;
				default:
					throw new IllegalStateException();
				}
			}

			final double verticalAlign = pos.verticalAlign.getVerticalAlign(textBox, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);

			if (box.getType() == BoxType.BLOCK) {
				// line-heightの適用
				double lineHeight = pos.lineHeight;
				lineHeight = Math.max(this.lineBox.getLineParams().lineHeight, lineHeight);
				double textHeight = ascent + descent;
				if (lineHeight > textHeight) {
					lineHeight = (lineHeight - textHeight) / 2.0;
					ascent = (ascent + lineHeight);
					descent = (descent + lineHeight);
				}
			}

			ascent = ascent + verticalAlign + baseline;
			descent = descent - verticalAlign - baseline;

			this.lineBox.addAscentDescent(ascent, descent);
		}
			break;

		default:
			throw new IllegalStateException();
		}
	}

	private void endInline() {
		// 開始のないINLINE_ENDは黙って捨てる(2026-08-17。control()の
		// INLINE_ENDと同じ理由)
		if (this.inlineStack == null || this.inlineStack.isEmpty()) {
			return;
		}
		Inline inline = (Inline) this.inlineStack.remove(this.inlineStack.size() - 1);
		if (inline != null) {
			InlineBox inlineBox = inline.box;
			inlineBox.closeInline();

			AbstractLineParams params = this.lineBox.getLineParams();
			final double end = params.flow.isVertical() && params.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
							params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP
						? inlineBox.getFrame().getFrameTop()
						: inlineBox.getFrame().getFrameLineEnd(params.flow);
			final double advance = inlineBox.getLineExtent(params.flow) + end;
			inlineBox.addAdvance(end);
			this.lineBox.addAdvance(end);
			AbstractTextBox textBox = this.getTextBox();
			if (this.lineBox != textBox) {
				textBox.addAdvance(advance);
			}
		}
	}

	private void addElement(Element e) {
		final AbstractTextBox textBox = this.getTextBox();

		final double advance = e.getAdvance();
		double ascent;
		double descent;
		if (e instanceof Text) {
			final Text text = (Text) e;
			textBox.addText(text);
			ascent = text.getAscent();
			descent = text.getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else if (e instanceof Control) {
			final Control control = (Control) e;
			textBox.addControl(control);
			if ((control.getControlChar() == ' ' || control.getControlChar() == SoftHyphen.CHAR)
					&& control.getAdvance() == 0) {
				return;
			}
			ascent = control.getAscent();
			descent = control.getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
			// leader() L1: パターンの寸法で行高さに参加する
			textBox.addLeader(leader);
			ascent = leader.runs[0].getAscent();
			descent = leader.runs[0].getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
			this.lineHasText = true;
		} else {
			throw new IllegalStateException();
		}
		textBox.addAdvance(advance);
		if (this.lineBox != textBox) {
			this.lineBox.addAdvance(advance);

			final AbstractTextBox parentText;
			final double baseline;
			if (this.inlineStack.size() >= 2) {
				final Inline parentInline = (Inline) this.inlineStack.get(this.inlineStack.size() - 2);
				baseline = parentInline.baseline;
				parentText = parentInline.box;
			} else {
				baseline = 0;
				parentText = this.lineBox;
			}
			final InlineBox inlineBox = (InlineBox) textBox;
			final InlinePos pos = inlineBox.getInlinePos();

			final double verticalAlign = pos.verticalAlign.getVerticalAlign(parentText, this.lineBox, ascent, descent,
					pos.lineHeight, baseline);
			double lineHeight = pos.lineHeight;
			// 行のline-heightを適用する
			{
				final double textHeight = this.lineBox.getLineParams().lineHeight;
				if (!LayoutUtils.isNone(textHeight)) {
					lineHeight = Math.max(textHeight, lineHeight);
				}
			}
			assert !LayoutUtils.isNone(lineHeight);
			final double textHeight = ascent + descent;
			// line-heightの適用
			if (lineHeight != textHeight) {
				lineHeight = (lineHeight - textHeight) / 2.0;
				ascent = (ascent + lineHeight);
				descent = (descent + lineHeight);
				ascent = ascent + verticalAlign + baseline;
				descent = descent - verticalAlign - baseline;
			}
			assert !LayoutUtils.isNone(ascent + descent);
		} else {
			double lineHeight = this.lineBox.getLineParams().lineHeight;
			// line-heightの適用
			final double textHeight = ascent + descent;
			if (lineHeight != textHeight) {
				lineHeight = (lineHeight - textHeight) / 2.0;
				ascent = (ascent + lineHeight);
				descent = (descent + lineHeight);
			}
			assert !LayoutUtils.isNone(ascent + descent);
		}
		this.lineBox.addAscentDescent(ascent, descent);
	}

	double getActualPageAxis() {
		return this.pageAxis + this.lineBox.getPageSize();
	}

	/**
	 * 行末側フロートの同一行配置({@code BlockBuilder.tryFloatOnCurrentLine}
	 * ——2026-08-08)のために、構築中の行の使用可能幅を絶対座標
	 * {@code newAbsLineEnd}まで狭めます。行がまだ配置されていない
	 * (locateLine前)場合や、既存の内容(textIndent+確定・未確定
	 * アドバンス)が新しい幅に収まらない場合は、状態を変えずに
	 * {@code false}を返します。
	 */
	boolean narrowCurrentLine(final double newAbsLineEnd) {
		if (this.lineBox == null || this.firstUnit) {
			return false;
		}
		final double newMaxLineSize = newAbsLineEnd - (this.builder.lineAxis + this.minLineAxis);
		if (LayoutUtils.compare(newMaxLineSize, this.textIndent + this.lineAxis) < 0) {
			return false;
		}
		if (newMaxLineSize < this.maxLineSize) {
			this.maxLineSize = newMaxLineSize;
		}
		return true;
	}

	double getPageAxis() {
		return this.pageAxis;
	}

	/**
	 * 通常フローを実際に進める量。表の先頭へ重ねる外置きマーカー専用行は
	 * 読み順上は独立したまま、後続の表と同じページ位置を使う。
	 */
	double getFlowPageAdvance() {
		return this.textBlockBox.overlaysFollowingBlock() ? 0 : this.pageAxis;
	}

	double getLineAxis() {
		return this.lineAxis;
	}

	/**
	 * 新しい行を開始します。
	 * 
	 * @param last
	 */
	private boolean newLine(boolean last) {
		// System.out.println("endLine: " + this.textBuffer);
		// 和文詰めA2: 実際の行分割はpairを断つ(行を跨ぐgapは入らない)
		this.autospace.reset();
		// 和文詰めT2/H1: この行の行末詰め/ぶら下げ量(align前に設定)
		if (this.pendingEndHang != 0) {
			this.lineBox.setEndHangAdvance(this.pendingEndHang);
			this.pendingEndHang = 0;
		}
		boolean lineAdded = false;
		if (this.drawLine(last, !last)) {
			this.firstFormattedLine = false;
			final AbstractLineBox lineBox = this.lineBox;
			final LineBox newLineBox = lineBox.splitLine(this.textBlockBox.getBlockParams());
			newLineBox.setParagraphBidi(this.paragraphBidiEnabled, this.paragraphDirection);

			// StringBuilder text = new StringBuilder();
			// lineBox.getText(text);
			// System.out.println("endLine: " + this.maxLineAxis+"/"+text);

			// 改頁で組み直された行に、前の組みで実体化したハイフンが残ることが
			// ある(2026-08-31)。ハイフンは行末にしか意味を持たないので、
			// 揃える前に落とす。詳細はAbstractTextBox#removeStrayHyphens
			final double strayHyphen = lineBox.removeStrayHyphens();
			if (strayHyphen != 0) {
				lineBox.addAdvance(-strayHyphen);
			}
			lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, last);
			this.applyTextOverflow(lineBox);
			if (this.inlineStack != null && !this.inlineStack.isEmpty()) {
				final AbstractTextParams lineParams = this.lineBox.getTextParams();
				this.lineBox = newLineBox;
				// TODO inlineStackの再生成を抑える
				List<Inline> inlineStack = this.inlineStack;
				this.inlineStack = null;

				for (int i = 0; i < inlineStack.size(); ++i) {
					Inline inline = (Inline) inlineStack.get(i);
					InlineBox oldInline = inline.box;
					InlineBox newInline = oldInline.splitLine(true);
					newInline.fixLineAxis(this.builder.getFlowBox());
					this.startInline(newInline);
				}
				for (int i = inlineStack.size() - 1; i >= 1; --i) {
					Inline inline = (Inline) inlineStack.get(i);
					Inline parent = (Inline) inlineStack.get(i - 1);
					parent.box.addAdvance(inline.box.getLineExtent(lineParams.flow));
				}
			} else {
				this.lineBox = newLineBox;
			}
			this.addLine(lineBox);
			if (last && this.paragraphBidiEnabled) {
				this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
			}
			lineAdded = true;
		}

		this.last = last;
		this.textIndent = 0;
		this.lineHead = this.firstUnit = true;
		this.lastSpaceAdvance = 0;
		if (last) {
			return lineAdded;
		}
		// 折り返し
		if (!this.collapseSpaces) {
			return lineAdded;
		}
		// 行頭のスペースのつぶし
		for (int i = 0; i < this.textBuffer.size(); ++i) {
			Element e = (Element) this.textBuffer.get(i);
			if (e instanceof WhiteSpace) {
				WhiteSpace whiteSpace = (WhiteSpace) e;
				this.lineAxis -= whiteSpace.getAdvance();
				whiteSpace.collapse();
				continue;
			}
			this.lineHead = false;
			break;
		}
		// System.out.println("nextLine: " + this.textBuffer);
		return lineAdded;
	}

	/**
	 * {@code text-overflow: ellipsis}(css-overflow-3 §4、2026-08-29)。
	 *
	 * <p>
	 * ブロックの{@code overflow}がvisible以外で、確定した行の内容が
	 * 利用可能幅を超えるとき(nowrapの1行、または分割できない長い語の
	 * 行)、行末を「利用可能幅−省略記号の幅」でクリップし、その位置に
	 * 省略記号を追加描画する({@link AbstractLineBox#setEllipsis})。
	 * テキストを組み直さずクリップで済ませるので、行の内容・グリフは
	 * そのまま(PDFのテキスト抽出には切れた文字も残る)。省略記号の
	 * フォントはブロックのfont-familyから"…"(U+2026)を持つ最初のもの、
	 * 無ければ"..."。右横書き(direction: rtl)の行頭側省略は未対応
	 * (何もしない)。行数で切る(line-clamp相当)機能ではない。
	 * </p>
	 */
	private void applyTextOverflow(final AbstractLineBox lineBox) {
		final BlockParams bp = this.textBlockBox.getBlockParams();
		if (bp.textOverflow != BlockParams.TEXT_OVERFLOW_ELLIPSIS || !bp.overflow.clipsPaint()
				|| bp.direction != AbstractTextParams.DIRECTION_LTR || bp.fontManager == null
				|| bp.fontStyle == null) {
			return;
		}
		final double avail = this.maxLineSize;
		// align()の実効行幅と同じ定義(行末ぶら下げ分を除き、インデントを含む)
		final double used = lineBox.getLineSize() - lineBox.getEndHangAdvance() + this.textIndent;
		if (LayoutUtils.compare(used, avail) <= 0) {
			return;
		}
		final TextImpl ellipsis = ellipsisText(bp);
		if (ellipsis == null) {
			return;
		}
		final double clipExtent = this.minLineAxis + avail - ellipsis.getAdvance();
		lineBox.setEllipsis(ellipsis, Math.max(0, clipExtent));
	}

	/**
	 * {@code line-clamp}のN行目を省略記号で切ります(2026-08-29)。
	 * {@link #applyTextOverflow}と同じ機構(クリップ+追加描画)だが、
	 * 行が幅いっぱいでなくてもよい: 切る位置は「内容の末尾(行揃えの
	 * ずれ込み)」と「利用可能幅−省略記号幅」の小さい方。内容が短ければ
	 * 省略記号は内容の直後に付き、長ければ末尾の字形がクリップされて
	 * 省略記号に置き換わる(Chromeのように最後の語を組み直しはしない)。
	 * rtlは未対応(何もしない)。
	 */
	private void applyLineClampEllipsis(final AbstractLineBox lineBox, final double lineStart, final double avail) {
		final BlockParams bp = this.textBlockBox.getBlockParams();
		if (bp.direction != AbstractTextParams.DIRECTION_LTR || bp.fontManager == null || bp.fontStyle == null) {
			return;
		}
		final TextImpl ellipsis = ellipsisText(bp);
		if (ellipsis == null) {
			return;
		}
		final double contentEnd = lineBox.getLineAlign() + lineBox.getLineSize() - lineBox.getEndHangAdvance();
		final double boxEnd = lineStart + avail - ellipsis.getAdvance();
		lineBox.setEllipsis(ellipsis, Math.max(0, Math.min(contentEnd, boxEnd)));
	}

	/** 省略記号のグリフ列(U+2026、無ければ"...")。作れなければnull。 */
	private static TextImpl ellipsisText(final BlockParams bp) {
		final FontListMetrics flm = bp.fontManager.getFontListMetrics(bp.fontStyle);
		String s = "…";
		FontMetrics fm = ellipsisFont(flm, 0x2026);
		if (fm == null) {
			s = "...";
			fm = ellipsisFont(flm, '.');
		}
		if (fm == null) {
			return null;
		}
		final net.zamasoft.pdfg2d.font.Font font = fm instanceof net.zamasoft.pdfg2d.font.FontMetricsImpl impl
				? impl.getFont()
				: fm.getFontSource().createFont();
		final TextImpl text = new TextImpl(-1, bp.fontStyle, fm);
		for (int i = 0; i < s.length(); ++i) {
			final char c = s.charAt(i);
			text.appendGlyph(new char[] { c }, 0, (byte) 1, font.toGID(c));
		}
		text.pack();
		return text;
	}

	private static FontMetrics ellipsisFont(final FontListMetrics flm, final int c) {
		for (int i = 0; i < flm.getLength(); ++i) {
			final FontMetrics fm = flm.getFontMetrics(i);
			final net.zamasoft.pdfg2d.font.FontSource source = fm.getFontSource();
			// 欠落グリフ用の代替フォントは何でも表示できると答えるので除く
			if (source instanceof net.zamasoft.pdfg2d.pdf.font.cid.missing.MissingCIDFontSource
					|| !source.canDisplay(c)) {
				continue;
			}
			return fm;
		}
		return null;
	}

	/**
	 * 行を生成します。
	 *
	 * @param last 最終行として揃え、バッファ全体を消費する場合は {@code true}
	 * @param materializeBreakHyphen 確定した分断位置のソフトハイフンを
	 *                               実体化する場合は {@code true}
	 * @return
	 */

	private boolean drawLine(final boolean last, final boolean materializeBreakHyphen) {
		if (this.firstUnit) {
			this.locateLine();
			this.firstUnit = false;
		}
		final int count;
		if (last) {
			count = this.textBuffer.size();
		} else {
			count = this.opportunity.elementCount();
		}
		// TODO 本来はここで assert count > 0 が成立するようにする。
		assert count > 0 || last;

		boolean content;
		if (count > 0) {
			this.allocateLeaders(count, last);
			TextImpl trimEndCandidate = null;
			for (int i = 0; i < count; ++i) {
				Element e = (Element) this.textBuffer.get(i);
				if (e instanceof Text) {
					final TextImpl text = (TextImpl) e;
					if (i == count - 1) {
						if (last || this.opportunity.glyphCount() == 0 || this.opportunity.glyphCount() == text.getGlyphCount()) {
							// 最後の行or
							// テキストで終わっていない場合
							// １ユニットしか幅がない場合
							text.pack();
							if (this.text == text) {
								this.text = null;
							}
						} else {
							// 分割可能な箇所で分割
							e = text.split(this.opportunity.glyphCount());
							TextImpl prevText = (TextImpl) e;
							// 分割部分のカーニングを取り消して位置を計算する
							// (T1a: font層kernはGPOSのみ——約物詰め/autospaceの
							// 逆適用は下で行う)
							this.lineAxis += this.fontMetrics.getKerning(prevText.glyphIds[prevText.glyphCount - 1],
									text.glyphIds[0]);
							// 分割境界のpair調整の逆適用(旧splitのkern復元と
							// 等価)。調整は現在glyph=分割後のtail先頭の
							// xadvance[0]に載っている
							final double edgeAdjustment = this.boundaryAdjustment(prevText, text);
							if (edgeAdjustment != 0) {
								text.addXAdvance(0, -edgeAdjustment);
								this.lineAxis -= edgeAdjustment;
							}
							this.textBuffer.add(i, e);
						}
					}
					this.addElement(e);
					trimEndCandidate = (TextImpl) e;
				} else if (e instanceof TextControl) {
					final TextControl quad = (TextControl) e;
					if (materializeBreakHyphen && i == count - 1 && quad == this.opportunity.hyphen()) {
						// ソフトハイフンの分割機会で行が切られたのでハイフンを実体化する。
						//
						// ページ分割で閉じたブロック終端は last=true でここを通るため、
						// !last だけでは拾えない。確定した分断かを引数で渡して実体化する。
						final TextImpl hyphen = this.opportunity.hyphen().getText();
						if (hyphen.getGlyphCount() > 0) {
							// hyphenate-character:""は分割だけ行い文字を表示しない
							this.addElement(hyphen);
						}
					} else if (quad instanceof InlineQuad) {
						// インラインボックス
						final InlineQuad inlineQuad = (InlineQuad) quad;
						switch (inlineQuad.getType()) {
						case InlineQuad.INLINE_START: {
							// インライン開始
							final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
							this.startInline(inlineStartQuad.box);
						}
							break;

						case InlineQuad.INLINE_END: {
							// インライン終了
							this.endInline();
						}
							break;

						case InlineQuad.INLINE_REPLACED: {
							// 置換されたボックス
							final InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
							this.startInline((IInlineBox) inlineReplacedQuad.box);
							this.endInline();
						}
							break;

						case InlineQuad.INLINE_BLOCK: {
							// ブロックボックス
							this.startInline((IInlineBox) inlineQuad.getBox());
							this.endInline();
						}
							break;

						case InlineQuad.INLINE_ABSOLUTE: {
							// 絶対配置ボックス
							final InlineAbsoluteQuad inlineAbsoluteQuad = (InlineAbsoluteQuad) inlineQuad;
							this.getTextBox().addAbsolute(inlineAbsoluteQuad.box);
						}
							break;

						default:
							throw new IllegalStateException();
						}
					} else if (quad instanceof Control) {
						final Control control = (Control) quad;
						this.addElement(control);
					} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leaderQuad) {
						// leader() L1: 割り付け済みの幅で行へ格納する
						this.addElement(leaderQuad);
					} else {
						throw new IllegalStateException();
					}
					// 折りたたまれる行末空白と幅0の境界は、直前の約物が
					// 行末であることを妨げない。それ以外のインライン要素・
					// leaderが後ろにあれば約物は行末ではない。
					if (!(e instanceof Control) && e.getAdvance() != 0) {
						trimEndCandidate = null;
					}
				} else {
					throw new IllegalStateException();
				}
				this.lineAxis -= e.getAdvance();
			}

			if (trimEndCandidate != null) {
				double endHang = this.lineBox.getEndHangAdvance();
				if (this.lineBox.getLineParams().textSpacingTrimEnd) {
					endHang = Math.max(endHang, this.endTrim(trimEndCandidate));
				}
				if (this.lineBox.getLineParams().hangingPunctuationForceEnd) {
					endHang = Math.max(endHang, this.forceEndHang(trimEndCandidate));
				}
				this.lineBox.setEndHangAdvance(endHang);
			}

			double lastSpaceAdvance = 0;
			for (int i = count - 1; i >= 0; --i) {
				Element e = (Element) this.textBuffer.get(i);
				if (e instanceof Control) {
					Control c = (Control) e;
					lastSpaceAdvance += c.getAdvance();
					continue;
				}
				if (e.getAdvance() <= 0) {
					continue;
				}
				break;
			}
			this.lineBox.addAdvance(-lastSpaceAdvance);
			int remainder = this.textBuffer.size() - count;
			for (int i = 0; i < remainder; ++i) {
				this.textBuffer.set(i, this.textBuffer.get(count + i));
			}
			for (int i = 0; i < count; ++i) {
				this.textBuffer.remove(this.textBuffer.size() - 1);
			}
			// 前進保証ガード後の再生では、対応する開始を見ていない
			// INLINE_ENDだけが届くことがある。要素を消費した事実ではなく、
			// 実際に行へ内容が入ったかで判定する。空のままalign()すると
			// AbstractLineBoxの不変条件に反する。
			content = this.lineBox.getContentCount() > 0;
		} else {
			content = false;
		}

		this.opportunity = this.captureOpportunity();
		this.builder.checkFloatings();
		return content;
	}

	/**
	 * 選択済みの行範囲のleaderへ残余幅を割り付けます(leader() L1——
	 * consult-codex-2026-07-31-leader.txt Q2)。
	 *
	 * <p>
	 * 必ず全leaderを最小幅へ戻してから配分する(TwoPassの記録再生で同一
	 * インスタンスが再駆動されても前回の割り付けが漏れないように)。行が
	 * テキスト途中で分割される場合(=行が満杯)は残余が定義上≈0なので
	 * 最小幅のまま。行末スペースのつぶし分は残余に含める(align前に
	 * 取り除かれるため)。justifyより先にleaderが残余を消費するので、
	 * leader行の文字間justifyは自然に≈0になる。
	 * </p>
	 */
	private void allocateLeaders(final int count, final boolean last) {
		List<net.zamasoft.foliojet.layout.text.LeaderQuad> leaders = null;
		double natural = 0;
		for (int i = 0; i < count; ++i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
				leader.advance = leader.minAdvance;
				leader.endOffset = 0;
				if (leaders == null) {
					leaders = new ArrayList<>();
				}
				leaders.add(leader);
			}
			natural += e.getAdvance();
		}
		if (leaders == null) {
			return;
		}
		if (!last && count > 0 && this.textBuffer.get(count - 1) instanceof Text tail
				&& this.opportunity.glyphCount() > 0 && this.opportunity.glyphCount() != tail.getGlyphCount()) {
			// 行がテキスト途中で分割される=満杯。残余≈0なので最小幅のまま
			return;
		}
		// 行末スペース(align前につぶされる)は行幅に数えない
		double trailing = 0;
		for (int i = count - 1; i >= 0; --i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof Control control) {
				trailing += control.getAdvance();
				continue;
			}
			if (e.getAdvance() <= 0) {
				continue;
			}
			break;
		}
		final double extra = (this.maxLineSize - this.textIndent) - (natural - trailing);
		if (extra > 0) {
			final double share = extra / leaders.size();
			for (final net.zamasoft.foliojet.layout.text.LeaderQuad leader : leaders) {
				leader.advance += share;
				this.lineAxis += share;
			}
		}
		// 行末位相揃えの原点: 各leaderの終端から行内容の終端までの距離
		double after = -trailing;
		for (int i = count - 1; i >= 0; --i) {
			final Element e = (Element) this.textBuffer.get(i);
			if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
				leader.endOffset = Math.max(0, after);
			}
			after += e.getAdvance();
		}
	}

	/**
	 * 現在のバッファ状態を分割機会として捕捉します。バッファ末尾がソフト
	 * ハイフンの場合は切断時の実体化対象として保持します(残余末尾に
	 * 持ち越された場合を含む)。
	 */
	private BreakOpportunity captureOpportunity() {
		final SoftHyphen hyphen = !this.textBuffer.isEmpty()
				&& this.textBuffer.get(this.textBuffer.size() - 1) instanceof SoftHyphen sh ? sh : null;
		return new BreakOpportunity(this.textBuffer.size(), this.text != null ? this.text.getGlyphCount() : 0, hyphen);
	}

	FontStyle fontStyle;
	FontMetrics fontMetrics;

	/** 和文詰めA2: text-autospaceのpair追跡。 */
	private final net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker autospace = new net.zamasoft.foliojet.layout.text.spacing.AutospaceTracker();

	/** 和文詰めH1: hanging-punctuation: allow-endの有効フラグ。 */
	private boolean hangingEnd;

	/** 和文詰めT2/H1: 次のnewLineで完了する行の行末詰め/ぶら下げ量。 */
	private double pendingEndHang;

	/** JLREQ 3.8.3の追込み点。hangだけは描画幅を変えず実効行末を外へ出す。 */
	private static final class JlreqShrinkPoint {
		final TextImpl text;
		final int glyphIndex;
		final double capacity;
		final boolean hang;

		JlreqShrinkPoint(final TextImpl text, final int glyphIndex, final double capacity, final boolean hang) {
			this.text = text;
			this.glyphIndex = glyphIndex;
			this.capacity = capacity;
			this.hang = hang;
		}
	}

	private static final class JlreqGlyph {
		final TextImpl text;
		final int glyphIndex;
		final int codePoint;
		final int gid;
		final double fontSize;

		JlreqGlyph(final TextImpl text, final int glyphIndex, final int codePoint) {
			this.text = text;
			this.glyphIndex = glyphIndex;
			this.codePoint = codePoint;
			this.gid = text.getGlyphIds()[glyphIndex];
			this.fontSize = text.getFontStyle().getSize();
		}
	}

	/**
	 * 現在の分割候補をJLREQ 3.8.3の六段階（欧文語間→行末約物→行末中点→
	 * 内部中点→括弧・読点→和欧間）で追い込む。全容量で収まらない場合は
	 * 一切変更せず、従来どおり直前候補へ追い出す。
	 */
	private boolean tryJlreqLineShrink(final double overflow) {
		return this.tryJlreqLineShrink(overflow, true);
	}

	/** 静的位置の仮想閉鎖では、追込みの可否だけを調べて字送りを変更しない。 */
	@SuppressWarnings("unchecked")
	private boolean tryJlreqLineShrink(final double overflow, final boolean apply) {
		if (!(overflow > 0)) {
			return true;
		}
		final List<JlreqShrinkPoint>[] stages = new List[8];
		for (int i = 1; i < stages.length; ++i) {
			stages[i] = new ArrayList<>();
		}

		JlreqGlyph prev = null;
		JlreqGlyph beforeSpace = null;
		WhiteSpace pendingSpace = null;
		JlreqGlyph tail = null;
		for (final Element element : this.textBuffer) {
			if (element instanceof TextImpl text) {
				final char[] chars = text.getChars();
				final byte[] clusterLengths = text.getClusterLengths();
				int charIndex = 0;
				for (int glyphIndex = 0; glyphIndex < text.getGlyphCount(); ++glyphIndex) {
					final int cp = Character.codePointAt(chars, charIndex);
					final JlreqGlyph current = new JlreqGlyph(text, glyphIndex, cp);
					if (pendingSpace != null) {
						if (beforeSpace != null && isWestern(beforeSpace.codePoint) && isWestern(cp)) {
							final double min = Math.min(beforeSpace.fontSize, current.fontSize) / 4.0;
							addJlreqShrinkPoint(stages[1], current, Math.max(0, pendingSpace.getAdvance() - min));
						}
						pendingSpace = null;
						beforeSpace = null;
						prev = null;
					}
					if (prev != null) {
						addJlreqBoundaryShrinkPoints(stages, prev, current, !this.autospace.isTrimOff());
					}
					prev = tail = current;
					charIndex += clusterLengths[glyphIndex];
				}
				continue;
			}
			if (element instanceof WhiteSpace whiteSpace) {
				beforeSpace = prev;
				pendingSpace = whiteSpace;
				prev = null;
				continue;
			}
			if (element instanceof SoftHyphen) {
				continue;
			}
			if (element instanceof InlineQuad inline
					&& (inline.getType() == InlineQuad.INLINE_START || inline.getType() == InlineQuad.INLINE_END)
					&& inline.getAdvance() == 0) {
				continue;
			}
			prev = null;
			pendingSpace = null;
			beforeSpace = null;
		}

		if (tail != null) {
			addJlreqLineEndShrinkPoints(stages, tail);
		}

		double total = 0;
		for (int stage = 1; stage < stages.length; ++stage) {
			for (final JlreqShrinkPoint point : stages[stage]) {
				total += point.capacity;
			}
		}
		if (LayoutUtils.compare(total, overflow) < 0) {
			return false;
		}
		if (!apply) {
			return true;
		}

		double remainder = overflow;
		double physical = 0;
		double hang = 0;
		for (int stage = 1; stage < stages.length && remainder > 0.0001; ++stage) {
			double capacity = 0;
			for (final JlreqShrinkPoint point : stages[stage]) {
				capacity += point.capacity;
			}
			if (capacity <= 0) {
				continue;
			}
			final double used = Math.min(remainder, capacity);
			for (final JlreqShrinkPoint point : stages[stage]) {
				final double amount = used * point.capacity / capacity;
				if (point.hang) {
					hang += amount;
				} else if (amount != 0) {
					point.text.addXAdvance(point.glyphIndex, -amount);
					physical += amount;
				}
			}
			remainder -= used;
		}
		this.lineAxis -= physical;
		this.pendingEndHang = hang;
		return true;
	}

	private static void addJlreqShrinkPoint(final List<JlreqShrinkPoint> points, final JlreqGlyph glyph,
			final double capacity) {
		if (capacity > 0.0001) {
			points.add(new JlreqShrinkPoint(glyph.text, glyph.glyphIndex, capacity, false));
		}
	}

	private static void addJlreqBoundaryShrinkPoints(final List<JlreqShrinkPoint>[] stages,
			final JlreqGlyph prev, final JlreqGlyph current, final boolean punctuationTrim) {
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass pc = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(prev.codePoint);
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass cc = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(current.codePoint);

		// 第4段階: cl-05の前後四分アキをベタまで詰める。
		if (punctuationTrim) {
			double middleDot = 0;
			if (pc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT) {
				middleDot += prev.fontSize / 4.0;
			}
			if (cc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT) {
				middleDot += current.fontSize / 4.0;
			}
			addJlreqShrinkPoint(stages[4], current, middleDot);
		}

		// 第5段階: cl-01の前、cl-02/cl-07の後の二分アキ。cl-06の後は詰めない。
		if (punctuationTrim) {
			double punctuation = 0;
			if (cc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.OPENING) {
				punctuation += current.fontSize / 2.0;
			}
			if (pc == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.CLOSING
					|| net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isComma(prev.codePoint)) {
				punctuation += prev.fontSize / 2.0;
			}
			addJlreqShrinkPoint(stages[5], current, punctuation);
		}

		// 第6段階: text-autospaceの四分アキを最小八分まで詰める。
		final boolean japaneseLatin = isJapaneseLatinBoundary(prev.codePoint, current.codePoint);
		final net.zamasoft.pdfg2d.gc.text.GlyphAdvances xa = current.text.xAdvances();
		final double existing = xa == null ? 0 : xa.get(current.glyphIndex);
		if (japaneseLatin && existing > 0) {
			final double ideographSize = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
					.of(prev.codePoint) == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
							? prev.fontSize : current.fontSize;
			addJlreqShrinkPoint(stages[6], current, Math.min(existing, ideographSize / 8.0));
		}
	}

	private void addJlreqLineEndShrinkPoints(final List<JlreqShrinkPoint>[] stages, final JlreqGlyph tail) {
		final net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass cls = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass
				.of(tail.codePoint);
		final boolean wide = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(
				tail.text.getFontMetrics(), tail.gid, tail.fontSize, tail.text.getFontStyle().getDirection());
		final double advance = tail.text.getFontMetrics().getAdvance(tail.gid);
		final boolean force = this.lineBox.getLineParams().hangingPunctuationForceEnd
				&& cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.PUNCTUATION;
		if (force) {
			stages[2].add(new JlreqShrinkPoint(null, -1, advance, true));
			return;
		}
		double trim = 0;
		if (!this.autospace.isTrimOff()) {
			trim = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.endTrim(tail.codePoint,
					wide, tail.fontSize);
			if (trim > 0) {
				final int stage = cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.MIDDLE_DOT
						? 3 : 2;
				stages[stage].add(new JlreqShrinkPoint(null, -1, trim, true));
			}
		}
		if (wide && this.hangingEnd
				&& cls == net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingClass.PUNCTUATION
				&& advance > trim) {
			// allow-endはJLREQの六段階を使い切った後の追加救済。
			stages[7].add(new JlreqShrinkPoint(null, -1, advance - trim, true));
		}
	}

	private static boolean isWestern(final int codePoint) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind kind = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(codePoint);
		return kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.ALPHA
				|| kind == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.NUMERIC;
	}

	private static boolean isJapaneseLatinBoundary(final int prev, final int current) {
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind pk = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(prev);
		final net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind ck = net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses
				.of(current);
		return pk == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH && isWestern(current)
				|| ck == net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind.IDEOGRAPH
						&& isWestern(prev);
	}

	/** trim-both/autoの無条件行末詰め量です。 */
	private double endTrim(final TextImpl text) {
		if (text.getGlyphCount() <= 0) {
			return 0;
		}
		final int cp = Character.codePointBefore(text.getChars(), text.getCharCount());
		final int gid = text.getGlyphIds()[text.getGlyphCount() - 1];
		final double fontSize = text.getFontStyle().getSize();
		return net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.endTrim(cp,
				net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(text.getFontMetrics(), gid,
						fontSize, text.getFontStyle().getDirection()),
				fontSize);
	}

	/** hanging-punctuation: force-endの無条件ぶら下げ量です。 */
	private double forceEndHang(final TextImpl text) {
		if (text.getGlyphCount() <= 0) {
			return 0;
		}
		final int cp = Character.codePointBefore(text.getChars(), text.getCharCount());
		final int gid = text.getGlyphIds()[text.getGlyphCount() - 1];
		return net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.forceEndHang(cp,
				text.getFontMetrics().getAdvance(gid));
	}

	public void startTextRun(FontStyle fontStyle, FontMetrics fontMetrics) {
		// System.err.println("TBBR: "+fontStyle);
		assert this.text == null;
		// assert fontStyle != null;
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		// **フォントを引き継げていないときは開いているランから復元する**
		// (2026-08-17)。前進保証ガードが自動改ページを放棄すると、途中で
		// 作り直されたTextBuilderがstartTextRunを受け取らないまま字を受け取る
		// ことがある。BlockBuilder.glyphがTextBuilderを遅延生成するときに使う
		// のと同じ値なので、ここで補っても組み方は変わらない。
		// **落としてはいけない**——ライブロックは版面の劣化で済ませ、変換は
		// 完走させる(ARCHITECTURE §5.13)。
		if (this.fontStyle == null || this.fontMetrics == null) {
			final FontStyle openStyle = this.builder.getOpenRunFontStyle();
			final FontMetrics openMetrics = this.builder.getOpenRunFontMetrics();
			if (openStyle != null && openMetrics != null) {
				this.fontStyle = openStyle;
				this.fontMetrics = openMetrics;
			} else {
				// 復元元も無い。字を測れないので捨てる(内容は既に
				// ライブロックの放棄で落ちている範囲)
				return;
			}
		}
		// if (this.breakWord && this.unitAdvance > 0) {
		// if (this.firstUnit) {
		// this.locateLine();
		// this.firstUnit = false;
		// }
		// this.flush();
		// }
		// 和文詰めA2/T1a: 直前clusterとの境界の調整——autospace gap(正)と
		// 約物詰め(負、同一run内のみ=移管元のfont層kernと同範囲)
		final double fontSize = this.fontStyle == null ? 0 : this.fontStyle.getSize();
		double autospaceGap = this.autospace.gapBefore(ch, coff, fontSize);
		double punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize,
				this.fontStyle.getDirection());
		if (!this.measuringLine && this.breakWord == AbstractTextParams.WORD_WRAP_BREAK_WORD && this.unitAdvance > 0) {
			if (this.firstUnit) {
				this.locateLine();
				this.firstUnit = false;
			}
			// 折返し予測(式の定義はGlyphMeasureStep。加算順は従来を保存)
			double lineAxis = this.unitAdvance + this.letterSpacing + autospaceGap - punctuationTrim;
			if (this.text == null) {
				lineAxis += this.fontMetrics.getAdvance(gid);
			} else {
				lineAxis += this.text.glyphAdvance(gid);
			}
			final double maxLineAxis = this.maxLineSize - this.textIndent;
			if (LayoutUtils.compare(lineAxis, maxLineAxis) > 0) {
				this.flush();
				// flushが実際に行を分割した場合はtrackerがリセット済み——
				// 行を跨ぐpairに調整は入らない(再計算)
				autospaceGap = this.autospace.gapBefore(ch, coff, fontSize);
				punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize,
						this.fontStyle.getDirection());
			}
		}

		if (this.text == null) {
			assert this.fontStyle != null;
			assert this.fontMetrics != null;
			this.text = new TextImpl(charOffset, this.fontStyle, this.fontMetrics);
			this.text.setLetterSpacing(this.letterSpacing);
			this.textBuffer.add(this.text);
		}

		// CSS幅式の唯一の定義(GlyphMeasureStep)を通す。行会計への加算は
		// 従来どおりbaseAndSpacing→adjustmentの2段(浮動小数点順を保存)
		final net.zamasoft.foliojet.layout.text.GlyphMeasureStep step = new net.zamasoft.foliojet.layout.text.GlyphMeasureStep(
				this.text.appendGlyph(ch, coff, clen, gid), this.letterSpacing, autospaceGap, punctuationTrim);
		final double advance = step.baseAndSpacing();
		this.unitAdvance += advance;
		this.lineAxis += advance;
		final double adjustment = step.adjustment();
		if (adjustment != 0) {
			// 現在glyphのxadvance(=そのglyphの手前のアキ——CIDKeyedFont/
			// ルビdistributeと同じ規約)へ焼き込み+行会計へ加算(A2/T1a)
			this.text.addXAdvance(this.text.getGlyphCount() - 1, adjustment);
			this.unitAdvance += adjustment;
			this.lineAxis += adjustment;
		}
		this.autospace.glyphAdded(this.text, fontSize, ch, coff, clen, gid);
		this.lastSpaceAdvance = 0;
		this.lineHead = false;

		if (LayoutUtils.compare(this.text.getAscent() + this.text.getDescent(), this.maxPageSize) > 0) {
			// 行高さの制限を超えたら強制折り返し
			this.maxLineSize = 0;
		}

		if (this.text.getGlyphCount() > 10000) {
			// あまりにも長いランができるのを防止する
			this.endTextRun();
			this.startTextRun(this.fontStyle, this.fontMetrics);
		}
		// System.err.println("TB glyph: " + this.breakWord + ":" + advance + "/" + new String(ch, coff, clen));
	}

	public void endTextRun() {
		assert this.text.getGlyphCount() > 0;
		this.text.pack();
		this.text = null;
	}

	public void control(TextControl quad) {
		assert this.text == null;
		// 和文詰めA2: 制御(空白・改行・置換要素等)はpairを断つ(明示
		// 空白のあるpairへautospaceは入らない)。ただし幅0のインライン
		// 開始/終了は単なる境界でpairを維持する(spanを跨ぐ和欧境界も
		// autospaceの対象——仕様どおり)
		if (!(quad instanceof InlineQuad inlineQuad
				&& (inlineQuad.getType() == InlineQuad.INLINE_START
						|| inlineQuad.getType() == InlineQuad.INLINE_END)
				&& inlineQuad.getAdvance() == 0)) {
			this.autospace.reset();
		}
		if (quad instanceof Control) {
			// 制御コード
			Control control = (Control) quad;
			switch (control.getControlChar()) {
			case SoftHyphen.CHAR:
				break;

			case '\n':
				// 改行文字
				this.toLineFeed = true;
				break;

			case '\t':
				// タブ文字。幅はtab-size(css-text-3、2026-08-29): 倍数なら
				// 現在のフォントの空白1文字の送り幅×倍数、長さならそのまま。
				// タブ位置は行頭からタブ幅の整数倍(2026-08-29までは固定24pt)
				Tab tab = (Tab) control;
				tab.advance = tabAdvance(this.currentTextParams(), this.lineAxis);
				break;

			case '\u0020':
				// 空白
				if (!this.collapseSpaces) {
					break;
				}
				WhiteSpace whiteSpace = (WhiteSpace) control;
				if (this.lineHead) {
					// 先頭のつぶし
					whiteSpace.collapse();
				} else {
					// 末尾のつぶし
					this.lastSpaceAdvance = whiteSpace.getAdvance();
				}
				break;

			default:
				throw new IllegalStateException();
			}
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad) {
			// leader() L1: 最小幅(パターン1周期)で行分割判断に参加する。
			// 幅の割り付けはdrawLineの先頭
			this.lineHead = false;
		} else {
			AbstractTextParams params;
			if (this.textParamStack == null || this.textParamStack.isEmpty()) {
				params = this.lineBox.getTextParams();
			} else {
				final InlineBox box = (InlineBox) this.textParamStack.get(this.textParamStack.size() - 1);
				params = box.getTextParams();
			}

			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				params = inlineStartQuad.box.getTextParams();
				this.changeTextState(params);
				if (this.textParamStack == null) {
					this.textParamStack = new ArrayList<InlineBox>();
				}
				assert this.textParamStack.isEmpty() || ((IBox) this.textParamStack.get(this.textParamStack.size() - 1))
						.getParams().element != params.element : params.element;
				this.textParamStack.add(inlineStartQuad.box);
				if (inlineStartQuad.getAdvance() != 0) {
					this.lastSpaceAdvance = 0;
				}
			}
				break;

			case InlineQuad.INLINE_END: {
				final InlineEndQuad inlineEndQuad = (InlineEndQuad) inlineQuad;
				// **開始のないINLINE_ENDを受け取りうる**(2026-08-17)。
				// 前進保証ガードが自動改ページを放棄すると、内容はその場へ
				// はみ出して配置され、途中で作り直されたビルダーが対応する
				// INLINE_STARTを見ないまま閉じだけを受け取る。ガードの契約
				// (ARCHITECTURE §5.13)は<b>変換を失敗させずに劣化させる</b>
				// ことなので、ここで落ちてはいけない——実測: w3c-jlreqの
				// 用語表がNullPointerExceptionで変換ごと失敗していた
				if (this.textParamStack != null && !this.textParamStack.isEmpty()) {
					this.textParamStack.remove(this.textParamStack.size() - 1);
				}
				if (this.textParamStack == null || this.textParamStack.isEmpty()) {
					params = this.lineBox.getTextParams();
				} else {
					final InlineBox box = (InlineBox) this.textParamStack.get(this.textParamStack.size() - 1);
					params = box.getTextParams();
				}

				this.changeTextState(params);
				if (inlineEndQuad.getAdvance() != 0) {
					this.lastSpaceAdvance = 0;
				}
			}
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
				final double lineHeight = inlineQuad.getBox().getPageExtent(params.flow);
				if (LayoutUtils.compare(lineHeight, this.maxPageSize) > 0) {
					// 行高さの制限を超えたら強制折り返し
					this.maxLineSize = 0;
				}
				this.lineHead = false;
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		this.unitAdvance += quad.getAdvance();
		this.lineAxis += quad.getAdvance();
		this.textBuffer.add(quad);
	}

	/**
	 * 改行されるとtrueを返します。
	 * 
	 * @return
	 */
	public boolean flush() {
		if (this.totalFitPlan != null) {
			// M3c: 最適化再生中はK-Pが選択したflushでのみ改行する
			return this.plannedFlush();
		}
		//System.err.println("TB FLUSH: " + this.wrap);
		this.unitAdvance = 0;
		if (this.textBuffer.isEmpty()) {
			return false;
		}
		if (this.lineAxis > 0) {
			//System.err.println("TB flush: " + lineAxis + "/" + textUnitElementCount);
			if (this.firstUnit) {
				this.locateLine();
				this.firstUnit = false;
			}
			if (this.opportunity.elementCount() > 0) {
				double lineAxis = this.lineAxis - this.lastSpaceAdvance;
				double maxLineAxis = this.maxLineSize - this.textIndent;
				// System.err.println("TB flush: " + lineAxis + "/" + maxLineAxis);
				if (LayoutUtils.compare(lineAxis, maxLineAxis) > 0) {
					// JLREQ 3.8.3: 現候補が優先段階どおりの追込みで収まるなら
					// バッファ全体をこの行へ残す。収まらなければ変更せず従来候補へ送る。
					if (this.tryJlreqLineShrink(lineAxis - maxLineAxis)) {
						this.opportunity = this.captureOpportunity();
					}
					// テキストブロックの途中での折り返し
					final boolean ret = this.newLine(false);
					return ret;
				}
			}
		}
		if (this.toLineFeed) {
			// 改行コード
			final boolean ret = this.newLine(true);
			this.toLineFeed = false;
			return ret;
		}
		if (!this.firstUnit && this.textBuffer.get(this.textBuffer.size() - 1) instanceof SoftHyphen) {
			// ソフトハイフンでの分割は、ハイフンを実体化しても行が溢れない場合のみ許す。
			// ただし手前の部分だけで既に溢れている(他に分割点が無い)場合は許容する。
			final SoftHyphen softHyphen = (SoftHyphen) this.textBuffer.get(this.textBuffer.size() - 1);
			final double lineAxis = this.lineAxis - this.lastSpaceAdvance;
			final double maxLineAxis = this.maxLineSize - this.textIndent;
			if (LayoutUtils.compare(lineAxis, maxLineAxis) <= 0
					&& LayoutUtils.compare(lineAxis + softHyphen.getText().getAdvance(), maxLineAxis) > 0) {
				return false;
			}
		}
		//if (this.wrap) {
			this.opportunity = this.captureOpportunity();
		//}
		return false;
	}

	/**
	 * K-Pの選択計画に従うflushです(M3c、{@link #totalFitPlan}非null時
	 * のみ)。溢れ判定・ソフトハイフン適合判定はK-P側で済んでいるため
	 * 行わず、選択されたflushではバッファ全体を1行として確定する
	 * (consume-onceのため{@code while(flush())}ループの再入では改行
	 * しない)。物理生成(禁則済みバッファの行化・ハイフン実体化・
	 * インライン再生成・justification)は既存の{@link #newLine}系が担う。
	 */
	private boolean plannedFlush() {
		this.unitAdvance = 0;
		if (this.textBuffer.isEmpty()) {
			return false;
		}
		if (!this.totalFitPlan.takeBreakAtCursor()) {
			return false;
		}
		if (this.toLineFeed) {
			// 明示改行(forced breakpoint)はlegacyと同じ最終行扱い
			final boolean ret = this.newLine(true);
			this.toLineFeed = false;
			return ret;
		}
		this.opportunity = this.captureOpportunity();
		return this.newLine(false);
	}

	void finish(final boolean fragmentBreak) {
		// テキストブロックの末尾
		// assert this.textParamStack == null || this.textParamStack.isEmpty();
		// fragmentBreak=true は本文の終端ではなく、版面が満杯になって
		// 後続断片へテキストが続くことを呼び出し側が確定した状態。
		if (!this.drawLine(true, fragmentBreak)) {
			// 開始のないINLINE_ENDだけを回復的に捨てたTextBuilderは、
			// 1行も持たずに終了してよい。
			if (this.paragraphBidiEnabled) {
				if (fragmentBreak) {
					this.builder.previewBidiParagraph(this.textBlockBox.getBlockParams());
				} else {
					this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
				}
			}
			return;
		}
		this.lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, true);
		// ブロック末尾の行(nowrapの1行はここだけを通る)にもtext-overflowを適用
		this.applyTextOverflow(this.lineBox);
		this.addLine(this.lineBox);
		if (this.paragraphBidiEnabled) {
			if (fragmentBreak) {
				this.builder.previewBidiParagraph(this.textBlockBox.getBlockParams());
			} else {
				this.builder.resolveBidiParagraph(this.textBlockBox.getBlockParams());
			}
		}
	}
}
