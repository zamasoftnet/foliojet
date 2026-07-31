package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.fragment.BreakOpportunity;

import net.zamasoft.foliojet.layout.box.content.BreakToken;


import net.zamasoft.foliojet.layout.box.params.WritingMode;

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
	 * タブの幅です。
	 */
	private static final double TAB_WIDTH = 24.0;

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
		final Flow flow = builder.getFlow();
		final BlockParams params = flow.box.getBlockParams();
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
		this.lineBox = lineBox;
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
	private void addLine(AbstractLineBox lineBox) {
		this.textBlockBox.addLine(lineBox, this.pageAxis);
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
		if (!this.autospace.isTrimOff()
				&& head.getFontStyle().getDirection() != net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB
				&& this.fontMetrics.getKerning(prevGid, gid) == 0) {
			trim = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.pairTrim(prevCp,
					net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(this.fontMetrics,
							prevGid, fontSize),
					cp, net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(this.fontMetrics,
							gid, fontSize))
					* fontSize;
		}
		return gap - trim;
	}

	/**
	 * 現在構築中の行の位置を調整します。
	 */
	private void locateLine() {
		double pageStart = this.builder.pageAxis + this.pageAxis;
		double lineStart = this.builder.lineAxis;
		this.maxPageSize = Double.MAX_VALUE;

		this.maxLineSize = this.builder.getFlowBox().getLineSize();
		if (this.builder.floatings != null) {
			final double lineHeight = this.lineBox.getLineParams().lineHeight;
			// System.out.println("TB-locateLine1:" + pageStart + "/"
			// + this.builder.floatings.size() + "/" + lineHeight);
			final double lineEnd0 = this.builder.lineAxis + this.maxLineSize;
			// floatingsはこのループ中不変なのでスナップショットはループ外で
			// 1回だけ取る。
			final ExclusionSpace snapshot = this.snapshotExclusions();
			for (;;) {
				// ラインを入れるスペースがある部分の左右
				final ExclusionSpace.LineScan found = snapshot.scanLineBand(pageStart, lineHeight,
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
					pageStart = found.startExclusion().pageSpan().end();
				} else if (found.startExclusion() == null) {
					pageStart = found.endExclusion().pageSpan().end();
				} else {
					double startEnd = found.startExclusion().pageSpan().end();
					double endEnd = found.endExclusion().pageSpan().end();
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

		// 天付き(和文詰めS1: 判定はJapaneseSpacingResolverへ移管——
		// consult-codex-2026-07-31-text-spacing.txt。挙動は不変)
		if (!this.last && this.lineBox.getLineParams().flow.isVertical()) {
			for (int i = 0; i < this.textBuffer.size(); ++i) {
				Element e = (Element) this.textBuffer.get(i);
				if (e.getAdvance() == 0) {
					continue;
				}
				if (e instanceof Text) {
					final Text text = (Text) e;
					final double headIndent = net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver
							.verticalHeadIndent(text.getChars()[0]);
					if (headIndent != 0) {
						this.textIndent = headIndent * text.getFontStyle().getSize();
					}
				}
				break;
			}
		}
	}

				/**
	 * {@link BlockBuilder#snapshotExclusions}への委譲です(2026-07-23、
	 * codexレビュー指摘——変換処理が両クラスに重複していると将来の
	 * 変換規則ずれを生むため一本化した)。
	 */
	private ExclusionSpace snapshotExclusions() {
		return this.builder.snapshotExclusions();
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
			double start = inlineBox.getFrame().getFrameLineStart(textParams.flow);
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
			double descent, ascent;
			if (box.getType() == BoxType.BLOCK) {
				// インラインブロック・テーブルの基底線
				final AbstractContainerBox inlineBlockBox = (AbstractContainerBox) box;
				final BlockParams params = inlineBlockBox.getBlockParams();
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
		Inline inline = (Inline) this.inlineStack.remove(this.inlineStack.size() - 1);
		if (inline != null) {
			InlineBox inlineBox = inline.box;
			inlineBox.closeInline();

			AbstractLineParams params = this.lineBox.getLineParams();
			final double end = inlineBox.getFrame().getFrameLineEnd(params.flow);
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
		} else if (e instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
			// leader() L1: パターンの寸法で行高さに参加する
			textBox.addLeader(leader);
			ascent = leader.runs[0].getAscent();
			descent = leader.runs[0].getDescent();
			assert !LayoutUtils.isNone(ascent + descent);
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

	double getPageAxis() {
		return this.pageAxis;
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
		if (this.drawLine(last)) {
			final AbstractLineBox lineBox = this.lineBox;
			final LineBox newLineBox = lineBox.splitLine(this.textBlockBox.getBlockParams());

			// StringBuilder text = new StringBuilder();
			// lineBox.getText(text);
			// System.out.println("endLine: " + this.maxLineAxis+"/"+text);

			lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, last);
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
	 * 行を生成します。
	 * 
	 * @param last
	 * @return
	 */
	private boolean drawLine(boolean last) {
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
				} else if (e instanceof TextControl) {
					final TextControl quad = (TextControl) e;
					if (!last && i == count - 1 && this.opportunity.hyphen() != null) {
						// ソフトハイフンの分割機会で行が切られたのでハイフンを実体化する
						this.addElement(this.opportunity.hyphen().getText());
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
				} else {
					throw new IllegalStateException();
				}
				this.lineAxis -= e.getAdvance();
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
			content = true;
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

	/**
	 * 追い込み(T2)/ぶら下げ(H1)の行末許容量です(和文詰め——
	 * consult-codex-2026-07-31-text-spacing.txt T2/H1)。バッファ末尾の
	 * glyphが対象約物のとき、行に収まる方を優先順(trim→hang)で返す。
	 * 対象外・どちらでも収まらないときは0(従来の追い出しへ)。
	 */
	private double endAllowance(final double lineAxis, final double maxLineAxis) {
		if (this.textBuffer.isEmpty()) {
			return 0;
		}
		final Element tail = (Element) this.textBuffer.get(this.textBuffer.size() - 1);
		if (!(tail instanceof TextImpl text) || text.getGlyphCount() <= 0) {
			return 0;
		}
		if (text.getFontStyle().getDirection() == net.zamasoft.pdfg2d.gc.font.FontStyle.Direction.TB) {
			return 0; // 縦書きは対象外(trim=T1a同、hang=横確立後の次増分)
		}
		final int cp = Character.codePointBefore(text.getChars(), text.getCharCount());
		final int gid = text.getGlyphIds()[text.getGlyphCount() - 1];
		final double fontSize = text.getFontStyle().getSize();
		final net.zamasoft.pdfg2d.gc.font.FontMetrics metrics = text.getFontMetrics();
		return net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.endAllowance(cp,
				net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolver.isWide(metrics, gid, fontSize),
				this.autospace.isTrimOff(), this.hangingEnd, metrics.getAdvance(gid), fontSize,
				lineAxis - maxLineAxis);
	}

	public void startTextRun(FontStyle fontStyle, FontMetrics fontMetrics) {
		// System.err.println("TBBR: "+fontStyle);
		assert this.text == null;
		// assert fontStyle != null;
		this.fontStyle = fontStyle;
		this.fontMetrics = fontMetrics;
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
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
		double punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize);
		if (this.breakWord == AbstractTextParams.WORD_WRAP_BREAK_WORD && this.unitAdvance > 0) {
			if (this.firstUnit) {
				this.locateLine();
				this.firstUnit = false;
			}
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
				punctuationTrim = this.autospace.trimBefore(ch, coff, gid, this.text, this.fontMetrics, fontSize);
			}
		}

		if (this.text == null) {
			assert this.fontStyle != null;
			assert this.fontMetrics != null;
			this.text = new TextImpl(charOffset, this.fontStyle, this.fontMetrics);
			this.text.setLetterSpacing(this.letterSpacing);
			this.textBuffer.add(this.text);
		}

		final double advance = this.text.appendGlyph(ch, coff, clen, gid) + this.letterSpacing;
		this.unitAdvance += advance;
		this.lineAxis += advance;
		final double adjustment = autospaceGap - punctuationTrim;
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
				// タブ文字
				Tab tab = (Tab) control;
				tab.advance = (TAB_WIDTH - (this.lineAxis % TAB_WIDTH));
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
				this.textParamStack.remove(this.textParamStack.size() - 1);
				if (this.textParamStack.isEmpty()) {
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
					// 和文詰めT2/H1: 行末の追い込み(trim)/ぶら下げ(hang)で
					// 収まるなら、この位置(バッファ全体)で改行して行末
					// 許容量を行へ渡す(従来=前のopportunityへの追い出し)
					final double allowance = this.endAllowance(lineAxis, maxLineAxis);
					if (allowance > 0) {
						this.opportunity = this.captureOpportunity();
						this.pendingEndHang = allowance;
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

	void finish() {
		// テキストブロックの末尾
		// assert this.textParamStack == null || this.textParamStack.isEmpty();
		if (!this.drawLine(true)) {
			assert this.textBlockBox.getLineCount() > 0 : this.text;
			return;
		}
		this.lineBox.align(this.textIndent, this.minLineAxis, this.maxLineSize, true);
		this.addLine(this.lineBox);
	}
}
