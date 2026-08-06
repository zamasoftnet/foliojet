package net.zamasoft.foliojet.layout.builder.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineBlockQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.text.GlyphMeasureStep;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;

/**
 * 固有寸法(IntrinsicSizes)の計測器です。TwoPassBlockBuilder(レコーダ)から
 * イベントを受け取り、min/max の内容寸法を累積します(理想設計 §5.2b の
 * SizingMode 消費者スロットの実体化。M2c で実レイアウト再生に置換予定)。
 */
final class IntrinsicMeasurer {
	/** flowStack 由来の文脈参照用。 */
	private final TwoPassBlockBuilder builder;

	/**
	 * 最小行幅、最大行幅、最小ページ高さ
	 */
	private double minLineSize = 0, maxLineSize = 0, minPageSize = 0;

	private double maxStartFloatAdvance = 0, maxEndFloatAdvance = 0;

	private int columnCount = 1;

	/**
	 * {@link #minLineSize}が段数倍を含むか(2026-07-28)。
	 * {@link net.zamasoft.foliojet.layout.sizing.IntrinsicSizes#columnInflated()}
	 * を参照。
	 */
	private boolean columnInflated = false;

	/**
	 * 現在の行幅。
	 */
	private double lineAxis = 0;

	private double atomicLineSize = 0;

	private double letterSpacing = 0;

	private double textIndent;

	private boolean blockHead;

	/**
	 * 通常のフローのブロックボックスの枠部分の行方向の幅、ページ方向の幅。
	 */
	private double lineFrame = 0, pageFrame = 0;

	private LineBreak toLineFeed = null;

	private final List<IBox> inlineStack = new ArrayList<IBox>();

	/**
	 * フローに入る直前の{@link #minLineSize}/{@link #maxLineSize}と、そのフローが
	 * 外側へ差し出す行方向の寸法(祖先の枠込み)を積む(2026-08-04)。
	 *
	 * <p>
	 * <b>固定幅のフローは、外から見た寸法をその幅で確定させる</b>——中の内容が
	 * はみ出していても外へは漏らさない。従来はこれを{@code endFlow}で
	 * {@code maxLineSize = minLineSize = flowBox.getWidth()}と<b>代入</b>して
	 * 実現しており、<b>先に測った兄弟の寸法まで消していた</b>。子が
	 * 「広い箱→狭い箱」の順に並ぶと最後の狭い箱の幅が全体の幅になり、
	 * 表のセル・絶対配置・フレックス項目が内容より狭く作られていた
	 * (material-web のタブ見出しが重なって発覚)。
	 */
	private final List<double[]> flowSizeStack = new ArrayList<double[]>();

	IntrinsicMeasurer(TwoPassBlockBuilder builder) {
		this.builder = builder;
	}

	IntrinsicSizes sizes() {
		return new IntrinsicSizes(this.minLineSize, this.maxLineSize, this.minPageSize, this.columnInflated);
	}

	void start(AbstractContainerBox containerBox) {
		this.textIndent = containerBox.getTextIndent();
		this.blockHead = true;
		this.letterSpacing = LayoutUtils.computeLength(containerBox.getBlockParams().letterSpacing,
				this.builder.getFlowBox().getLineSize());
	}

	void startFlow(final FlowBlockBox flowBox, final AbstractContainerBox containerBox) {
		assert this.inlineStack.isEmpty();
		BlockParams params = containerBox.getBlockParams();
		FlowPos pos = (FlowPos) flowBox.getPos();
		this.clearFloatAdvance(pos.clear);

		// 段組の中の内容は、外側から見ると段数倍の行方向寸法を要する。
		// **拡大するのは新しく足す分だけ**——{@link #lineFrame}は祖先の枠を
		// 積んだ累積値で、各階層で既に拡大済みである。従来はこれを各
		// startFlowで掛け直しており、入れ子の深さに対して**指数的に**
		// 膨らんでいた(2026-07-26に修正)。
		//
		// 実測: 4段の中に2段を入れた文書で
		// lineFrame 33 → 132 → 532 → 4256 と膨らみ、その途中値から
		// maxLineSizeを採っていた。結果、収縮幅の測定が紙面の31倍を返し、
		// 段が紙面の外へ並んだ(REVIEW-STATISTICS §12)。
		double frameAdd = flowBox.getFrame().getFrameLineExtent(params.flow);
		if (flowBox.getColumnCount() > 0) {
			frameAdd += flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1);
		}
		final double lineSize = this.lineFrame + flowBox.getLineExtent(params.flow) * this.columnCount;
		// 中に入る前の値と、このフロー自身が差し出す寸法を控える(endFlowで使う)
		this.flowSizeStack.add(new double[] { this.minLineSize, this.maxLineSize, lineSize });
		this.lineFrame += frameAdd * this.columnCount;
		this.pageFrame += flowBox.getFrame().getFramePageExtent(params.flow);
		assert !LayoutUtils.isNone(this.lineFrame);
		if (this.lineFrame > this.minLineSize) {
			this.minLineSize = this.lineFrame;
		}
		if (this.pageFrame > this.minPageSize) {
			this.minPageSize = this.pageFrame;
		}
		if (lineSize > this.maxLineSize) {
			this.maxLineSize = lineSize;
		}
		this.textIndent = flowBox.getTextIndent();
		this.blockHead = true;

		if (flowBox.getColumnCount() >= 2) {
			// ここから内側の最小内容寸法は段数倍で積まれる(2026-07-28)
			this.columnInflated = true;
		}
		this.columnCount *= flowBox.getColumnCount();
		// 元コードでは flowStack.add(flowBox) 後の getFlowBox().getLineSize() を参照していたが、
		// push 後の getFlowBox() は flowBox 自身なので等価。
		this.letterSpacing = LayoutUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				flowBox.getLineSize());
	}

	void endFlow(final AbstractBlockBox flowBox) {
		assert this.inlineStack.isEmpty();
		// builder.getFlowBox() は flowStack.remove 後の親ボックス。
		AbstractContainerBox containerBox = this.builder.getFlowBox();
		BlockParams params = containerBox.getBlockParams();
		BlockParams flowParams = flowBox.getBlockParams();
		this.columnCount /= flowBox.getColumnCount();
		// startFlowと対称に、**足した分だけ**を同じ倍率で戻す
		if (flowBox.getColumnCount() > 0) {
			this.lineFrame -= flowBox.getBlockParams().columns.gap * (flowBox.getColumnCount() - 1)
					* this.columnCount;
		}

		final double[] entered = this.flowSizeStack.remove(this.flowSizeStack.size() - 1);
		final boolean fixedLineSize;
		switch (params.flow) {
		case WritingMode.TB:
			// 横書き
			this.lineFrame -= flowBox.getFrame().getFrameWidth() * this.columnCount;
			this.pageFrame -= flowBox.getFrame().getFrameHeight();
			fixedLineSize = flowParams.size.getWidthType() == LengthType.ABSOLUTE;
			break;
		case WritingMode.LR:
		case WritingMode.RL:
			// 縦書き
			this.lineFrame -= flowBox.getFrame().getFrameHeight() * this.columnCount;
			this.pageFrame -= flowBox.getFrame().getFrameWidth();
			fixedLineSize = flowParams.size.getHeightType() == LengthType.ABSOLUTE;
			break;
		default:
			throw new IllegalStateException();
		}
		if (fixedLineSize) {
			// **固定幅フロー**。中の内容は外へ漏らさず、このフロー自身が
			// 差し出す寸法だけを残す。**兄弟の寸法は消さない**(2026-08-04)
			this.minLineSize = Math.max(entered[0], entered[2]);
			this.maxLineSize = Math.max(entered[1], entered[2]);
		}

		assert !LayoutUtils.isNone(this.lineFrame);

		this.textIndent = 0;
		this.blockHead = false;
		this.letterSpacing = LayoutUtils.computeLength(flowBox.getBlockParams().letterSpacing,
				this.builder.getFlowBox().getLineSize());
	}

	void bound(final AbstractReplacedBox replacedBox) {
		switch (replacedBox.getPos().getType()) {
		case FLOW: {
			// 静的・相対配置
			AbstractContainerBox containerBox = this.builder.getFlowBox();
			IFlowBox flowBox = (IFlowBox) replacedBox;
			FlowPos pos = (FlowPos) flowBox.getPos();
			this.clearFloatAdvance(pos.clear);
			LayoutUtils.calculateReplacedSize(this.builder, replacedBox);

			double minLineAxis, maxLineAxis = 0, minPageAxis;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// 縦書き
				minLineAxis = replacedBox.getHeight();
				minPageAxis = replacedBox.getWidth();
				if (replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getHeight();
				}
			} else {
				// 横書き
				minLineAxis = replacedBox.getWidth();
				minPageAxis = replacedBox.getHeight();
				if (replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getWidth();
				}
			}
			minPageAxis += this.pageFrame;
			minLineAxis *= this.columnCount;
			minLineAxis += this.lineFrame;

			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;

			assert !LayoutUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;
		case FLOAT: {
			// 浮動体
			AbstractContainerBox containerBox = this.builder.getFlowBox();
			IFloatBox floatingBox = (IFloatBox) replacedBox;
			this.clearFloatAdvance(floatingBox.getFloatPos().clear);
			LayoutUtils.calculateReplacedSize(this.builder, replacedBox);

			double minLineAxis, minPageAxis, maxLineAxis = 0;
			BlockParams params = containerBox.getBlockParams();
			if (params.flow.isVertical()) {
				// 縦書き
				minLineAxis = replacedBox.getHeight();
				minPageAxis = replacedBox.getWidth();
				if (replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getHeight();
				}
			} else {
				// 横書き
				minLineAxis = replacedBox.getWidth();
				minPageAxis = replacedBox.getHeight();
				if (replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
					maxLineAxis = replacedBox.getReplacedParams().size.getWidth();
				}
			}
			assert !LayoutUtils.isNone(minLineAxis);
			if (minLineAxis > this.minLineSize) {
				this.minLineSize = minLineAxis;
			}
			if (minPageAxis > this.minPageSize) {
				this.minPageSize = minPageAxis;
			}

			switch (floatingBox.getFloatPos().floating) {
			case FloatSide.START: {
				this.maxStartFloatAdvance += minLineAxis;
			}
				break;
			case FloatSide.END: {
				this.maxEndFloatAdvance += minLineAxis;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			double xmaxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
			if (xmaxLineAxis > maxLineAxis) {
				maxLineAxis = xmaxLineAxis;
			}
			maxLineAxis *= this.columnCount;
			maxLineAxis += this.lineFrame;
			if (maxLineAxis > this.maxLineSize) {
				this.maxLineSize = maxLineAxis;
			}
		}
			break;

		case ABSOLUTE:
			// 絶対配置
			replacedBox.calculateFrame(this.builder.getFlowBox().getLineSize());
			break;

		default:
			throw new IllegalStateException();
		}
	}

	void table(final IntrinsicSizes tableSizes) {
		this.columnInflated |= tableSizes.columnInflated();
		this.minLineSize = Math.max(this.minLineSize, tableSizes.minContent() * this.columnCount);
		this.maxLineSize = Math.max(this.maxLineSize, tableSizes.maxContent() * this.columnCount);
	}

	/** Grid全体のcontent-box contributionです(Grid G3d2——tableと同型)。 */
	void grid(final IntrinsicSizes gridSizes) {
		this.columnInflated |= gridSizes.columnInflated();
		this.minLineSize = Math.max(this.minLineSize, gridSizes.minContent() * this.columnCount);
		this.maxLineSize = Math.max(this.maxLineSize, gridSizes.maxContent() * this.columnCount);
	}

	/** Flex全体のcontent-box contributionです(Flex F1f——gridと同型)。 */
	void flex(final IntrinsicSizes flexSizes) {
		this.columnInflated |= flexSizes.columnInflated();
		this.minLineSize = Math.max(this.minLineSize, flexSizes.minContent() * this.columnCount);
		this.maxLineSize = Math.max(this.maxLineSize, flexSizes.maxContent() * this.columnCount);
	}

	void fitFloating(TwoPassBlockBuilder childBuilder) {
		FloatBlockBox floatingBox = (FloatBlockBox) childBuilder.getRootBox();
		this.clearFloatAdvance(floatingBox.getFloatPos().clear);

		BlockParams params = floatingBox.getBlockParams();
		BlockParams flowParams = this.builder.getFlowBox().getBlockParams();
		final WritingMode floatFlow = flowParams.flow;
		double minLineAxis, maxLineAxis;
		// 台帳#1 解消(2026-07-17): 旧実装は縦書きの min だけページ方向の
		// フレーム(FrameWidth)を加算していた(max は行方向)。論理軸
		// アクセサで縦横を統合し、min/max とも行方向フレームに揃える
		if (params.size.getLineType(floatFlow) != LengthType.AUTO) {
			minLineAxis = maxLineAxis = floatingBox.getLineExtent(floatFlow);
		} else {
			final IntrinsicSizes childSizes = childBuilder.getIntrinsicSizes();
			this.columnInflated |= childSizes.columnInflated();
			final double frameLine = floatingBox.getFrame().getFrameLineExtent(floatFlow);
			minLineAxis = childSizes.minContent() + frameLine;
			maxLineAxis = childSizes.maxContent() + frameLine;
		}
		assert !LayoutUtils.isNone(maxLineAxis);
		// System.err.println(this.minLineAxis + "/" + this.maxLineAxis);
		if (minLineAxis > this.minLineSize) {
			this.minLineSize = minLineAxis;
		}

		switch (floatingBox.getFloatPos().floating) {
		case FloatSide.START:
			this.maxStartFloatAdvance += maxLineAxis;
			break;
		case FloatSide.END:
			this.maxEndFloatAdvance += maxLineAxis;
			break;
		default:
			throw new IllegalStateException();
		}
		maxLineAxis = this.maxStartFloatAdvance + this.maxEndFloatAdvance;
		maxLineAxis *= this.columnCount;
		maxLineAxis += this.lineFrame;
		if (maxLineAxis > this.maxLineSize) {
			this.maxLineSize = maxLineAxis;
		}
	}

	/**
	 * 1グリフ分の幅を計上します。CSS幅式の成分の定義は
	 * {@link GlyphMeasureStep}(唯一の定義。85点計画増分5でintrinsic系統を
	 * 接続し、幅会計3系統の統合が完了)。
	 *
	 * <p>
	 * <b>intrinsic計測の方針は行会計と2点違う</b>ので、
	 * {@code baseAndSpacing()+adjustment()}の正規2段ではなく成分別に足す:
	 * </p>
	 *
	 * <ul>
	 * <li>和文詰めA2: 境界gapは<b>max-content(行)にのみ</b>計上する
	 * ——和欧文境界は分割機会でgapは分割時に消えるため、min-content
	 * (atomic unit)には入れない(高々0.125icの過小は安全側の近似として
	 * 記録)。trimはmin/max両方(trim pairは禁則で不可分、T1a)</li>
	 * <li>加算順は従来を保存する: gap→(base−trim)+letter-spacing。
	 * 正規順(base+letter-spacing)と最終ULPが変わりうるため、golden
	 * 全件バイト一致(再生成禁止)の完了条件の下では順序を動かせない</li>
	 * </ul>
	 */
	void glyph(final double baseAdvance, final double autospaceGap, final double punctuationTrim) {
		final GlyphMeasureStep step = new GlyphMeasureStep(baseAdvance, this.letterSpacing, autospaceGap,
				punctuationTrim);
		if (step.autospaceGap() > 0) {
			this.lineAxis += step.autospaceGap();
		}
		double advance = step.baseAdvance() - step.punctuationTrim();
		advance += step.letterSpacing();
		this.atomicLineSize += advance;
		this.lineAxis += advance;
		double minPageAxis = this.getCurrentLineHeight() + this.pageFrame;
		if (minPageAxis > this.minPageSize) {
			this.minPageSize = minPageAxis;
		}
	}

	void control(final TextControl quad, final TwoPass inlineBlockMeasure) {
		// 元コードでは toLineFeed の設定は記録(records.add)より前だったが、
		// 計測状態と records は独立のため順序を入れ替えても等価。
		if (quad instanceof LineBreak) {
			this.toLineFeed = (LineBreak) quad;
		}

		double minAdvance, maxAdvance, pageSize;
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			final BlockParams cParams = this.builder.getFlowBox().getBlockParams();
			if (quad instanceof InlineReplacedQuad) {
				// 画像
				final AbstractReplacedBox box = (AbstractReplacedBox) inlineQuad.getBox();
				maxAdvance = quad.getAdvance();
				minAdvance = 0;
				if (cParams.flow.isVertical()) {
					// 縦書き
					if (!box.getReplacedParams().size.getHeightType().needsReference()
							&& !box.getReplacedParams().maxSize.getHeightType().needsReference()) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getHeight() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getHeight();
						}
					}
					pageSize = box.getWidth();
				} else {
					// 横書き
					if (!box.getReplacedParams().size.getWidthType().needsReference()
							&& !box.getReplacedParams().maxSize.getWidthType().needsReference()) {
						minAdvance = maxAdvance;
					}
					if (box.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE) {
						if(box.getReplacedParams().size.getWidth() > maxAdvance) {
							maxAdvance = box.getReplacedParams().size.getWidth();
						}
					}
					pageSize = box.getHeight();
				}
			} else if (quad instanceof InlineBlockQuad) {
				// インラインブロック
				final AbstractContainerBox box = (AbstractContainerBox) inlineQuad.getBox();
				final double lineFrame = box.getFrame().getFrameLineExtent(cParams.flow);
				final double pageFrame = box.getFrame().getFramePageExtent(cParams.flow);
				// インラインブロック
				final BlockParams params = (BlockParams) box.getParams();
				final TwoPass stfBuilder = inlineBlockMeasure;
				if (stfBuilder == null) {
					// 実測ビルダーを持たない合成箱(ルビ単位)。寸法は
					// 構築時に確定しているので箱の実寸だけを使う
					// (2026-07-25、TwoPassBlockBuilder.controlの
					// isPreMeasured分岐と対)
					minAdvance = maxAdvance = lineFrame;
					pageSize = pageFrame;
				} else {
					final IntrinsicSizes stfSizes = stfBuilder.getIntrinsicSizes();
					this.columnInflated |= stfSizes.columnInflated();
					if (cParams.flow.isVertical() == params.flow.isVertical()) {
						minAdvance = stfSizes.minContent() + lineFrame;
						maxAdvance = stfSizes.maxContent() + lineFrame;
						pageSize = stfSizes.minPage() + pageFrame;
					} else {
						// 縦中横/横中縦
						minAdvance = maxAdvance = stfSizes.minPage() + pageFrame;
						pageSize = stfSizes.minContent() + lineFrame;
					}
				}
				minAdvance = Math.max(minAdvance, box.getLineExtent(params.flow));
				maxAdvance = Math.max(maxAdvance, box.getLineExtent(params.flow));
				pageSize = Math.max(pageSize, box.getPageExtent(params.flow));
			} else {
				if (inlineQuad instanceof InlineStartQuad) {
					this.inlineStack.add(inlineQuad.getBox());
					final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
					this.letterSpacing = LayoutUtils.computeLength(inlineStartQuad.box.getTextParams().letterSpacing,
							this.builder.getFlowBox().getLineSize());
				} else if (inlineQuad instanceof InlineEndQuad) {
					this.inlineStack.remove(this.inlineStack.size() - 1);
					AbstractTextParams params;
					if (this.inlineStack.isEmpty()) {
						params = this.builder.getFlowBox().getBlockParams();
					} else {
						final InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
						params = box.getTextParams();
					}
					this.letterSpacing = LayoutUtils.computeLength(params.letterSpacing,
							this.builder.getFlowBox().getLineSize());
				}
				minAdvance = maxAdvance = quad.getAdvance();
				pageSize = inlineQuad.getBox().getPageExtent(cParams.flow);
			}
		} else if (quad instanceof net.zamasoft.foliojet.layout.text.LeaderQuad leader) {
			// leader() L1: min-content/max-contentともにパターン1周期分
			// (割り付け済みadvanceを読まない——再計測時の漏れ防止)
			minAdvance = maxAdvance = leader.minAdvance;
			pageSize = 0;
		} else {
			minAdvance = maxAdvance = quad.getAdvance();
			pageSize = 0;
		}
		pageSize = Math.max(pageSize, this.getCurrentLineHeight());
		pageSize += this.pageFrame;
		if (pageSize > this.minPageSize) {
			this.minPageSize = pageSize;
		}
		this.atomicLineSize += minAdvance;
		this.lineAxis += maxAdvance;
	}

	void flush() {
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
			if (minLineSize > this.maxLineSize) {
				this.maxLineSize = minLineSize;
			}
		}
		this.atomicLineSize = 0;
		if (this.toLineFeed != null) {
			assert !LayoutUtils.isNone(this.lineAxis);
			assert !LayoutUtils.isNone(this.lineFrame);
			double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
			maxLineSize *= this.columnCount;
			maxLineSize += this.lineFrame;
			if (maxLineSize > this.maxLineSize) {
				this.maxLineSize = maxLineSize;
			}
			this.lineAxis = 0;
			this.toLineFeed = null;
			this.textIndent = 0;
			this.clearFloatAdvance(ClearMode.BOTH);
		}
	}

	void endTextBlock() {
		assert !LayoutUtils.isNone(this.lineAxis);
		assert !LayoutUtils.isNone(this.lineFrame);
		double minLineSize = this.atomicLineSize;
		if (this.blockHead) {
			minLineSize += this.textIndent;
			this.blockHead = false;
		}
		minLineSize *= this.columnCount;
		minLineSize += this.lineFrame;
		if (minLineSize > this.minLineSize) {
			this.minLineSize = minLineSize;
		}
		double maxLineSize = this.textIndent + this.maxStartFloatAdvance + this.maxEndFloatAdvance + this.lineAxis;
		maxLineSize *= this.columnCount;
		maxLineSize += this.lineFrame;
		if (maxLineSize > this.maxLineSize) {
			this.maxLineSize = maxLineSize;
		}
		this.atomicLineSize = 0;
		this.lineAxis = 0;
	}

	private void clearFloatAdvance(ClearMode clear) {
		switch (clear) {
		case ClearMode.BOTH:
			this.maxStartFloatAdvance = 0;
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.START:
			this.maxStartFloatAdvance = 0;
			break;
		case ClearMode.END:
			this.maxEndFloatAdvance = 0;
			break;
		case ClearMode.NONE:
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private double getCurrentLineHeight() {
		if (this.inlineStack.isEmpty()) {
			return this.builder.getFlowBox().getBlockParams().lineHeight;
		}
		InlineBox box = (InlineBox) this.inlineStack.get(this.inlineStack.size() - 1);
		return box.getInlinePos().lineHeight;
	}
}
