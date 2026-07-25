package net.zamasoft.foliojet.layout.util;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.OutputMarks;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LayoutUtils.java 1574 2018-10-26 02:44:00Z miyabe $
 */
public final class LayoutUtils {
	private LayoutUtils() {
		// unused
	}

	// magic number
	public static final double NONE = Double.MAX_VALUE * 0.958324758437;

	public static final boolean isNone(double v) {
		return v == NONE;
	}

	public static final double THRESHOLD = .5;

	/**
	 * a &lt; bなら負、a &gt; bなら正、a = bならゼロを返します。<br>
	 * 計算誤差による判定間違いを防ぐため、 行の折り返し、浮動ボックス、行の位置指定、改ページ制御のための比較はこれを使用します。
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	public static int compare(double a, double b) {
		// 0.5未満の差は同一と見なします
		// IEでは切り落とし、Firefoxは小数点以下1桁でまるめている模様
		// 注：まるめてから比較する実装では、差が同じでも判定が変わるため、
		// 内容がページをはみ出していると判定された場合でも
		// ボックスの分割の途中で判定が矛盾することがあった
		double diff = a - b;
		if (diff < THRESHOLD && diff > -THRESHOLD) {
			return 0;
		}
		return a < b ? -1 : 1;
	}

	/**
	 * ボックスの行方向寸法が内容に依存する(実測パスが必要)であればtrueを返します。
	 * 2パス化の判定はこの述語ファミリに一元化されます(ARCHITECTURE.md §5.2b)。
	 * 絶対配置は ABSOLUTE 指定のみを固定とみなします(%やインセット由来は内容依存)。
	 *
	 * @param containerBox 包含ブロック(軸の判定に使用)
	 * @param blockBox     対象ボックス
	 * @return 実測が必要であればtrue
	 */
	public static boolean needsIntrinsicSizing(AbstractContainerBox containerBox, AbstractContainerBox blockBox) {
		final LengthType lineType = blockBox.getBlockParams().size
				.getLineType(containerBox.getBlockParams().flow);
		if (blockBox.getPos().getType() == PosType.ABSOLUTE) {
			return lineType != LengthType.ABSOLUTE;
		}
		return lineType == LengthType.AUTO;
	}

	/**
	 * テキストを描画します。
	 * 
	 * @param gc
	 * @param fontSize
	 * @param text
	 * @param x
	 * @param y
	 * @param width
	 */
	public static void drawText(GC gc, FontPolicyList fontPolicy, double fontSize, String text, double x, double y,
			double width) throws GraphicsException {
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		assert !LayoutUtils.isNone(width);
		try (final var gcState = gc.begin()) {
			gc.transform(AffineTransform.getTranslateInstance(x, y));

			PageLayoutGlyphHandler lineHandler = new PageLayoutGlyphHandler(gc);
		lineHandler.setLineAdvance(width);

		TextLayoutHandler tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules(null), lineHandler);
		tlf.setFontFamilies(FontFamilyList.SERIF);
			tlf.setFontPolicy(fontPolicy);
			tlf.setFontSize(fontSize);
			tlf.characters(text);
			tlf.flush();

			lineHandler.close();
		}
	}

	/**
	 * 長さを計算します。
	 * 
	 * @param length
	 * @param ref
	 * @return
	 */
	public static double computeLength(Length length, double ref) {
		switch (length.getType()) {
		case RELATIVE:
			return length.getLength() * ref;
		case ABSOLUTE:
			return length.getLength();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return length.getLength() + length.getRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * AUTOをゼロとしてインセットを計算します。
	 * 
	 * @param ainsets
	 * @param insets
	 * @param refSize
	 */
	public static void computeMarginsAutoToZero(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		case AUTO:
			top = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		case AUTO:
			right = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		case AUTO:
			bottom = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		case AUTO:
			left = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	public static void computePaddings(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	/**
	 * Dimensionの幅を計算します。 AUTOの場合はNaNを返します。
	 * 
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionWidth(Dimension size, double ref) {
		switch (size.getWidthType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() * ref;
		case ABSOLUTE:
			return size.getWidth();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() + size.getWidthRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Dimensionの高さを計算します。 AUTOの場合はNaNを返します。
	 * 
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionHeight(Dimension size, double ref) {
		switch (size.getHeightType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() * ref;
		case ABSOLUTE:
			return size.getHeight();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() + size.getHeightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Dimensionの行方向の寸法を計算します。 AUTOの場合はNONEを返します。
	 *
	 * @param size 寸法
	 * @param flow 軸を決める書字方向
	 * @param ref  相対値の基準
	 * @return 行方向の寸法
	 */
	public static double computeDimensionLine(Dimension size, WritingMode flow, double ref) {
		return flow.isVertical() ? computeDimensionHeight(size, ref) : computeDimensionWidth(size, ref);
	}

	/**
	 * 親の物理原点 x から、論理位置(行方向 childLineStart、ページ方向
	 * childPageStart/childPageEnd)に置かれる子の物理X座標を返します。
	 *
	 * <h2>ページ軸の向きはここと {@link #drawY} だけが知っている</h2>
	 *
	 * <p>
	 * 書字方向は<b>2つの独立した属性</b>で表せます——<b>どの物理次元が
	 * ページ軸か</b>と、<b>その軸が正負どちらへ進むか</b>。前者は
	 * {@link WritingMode#isVertical()}が答え、コード全体に散っていますが、
	 * <b>後者を物理座標へ変換するのは、main全体でこの関数と{@link #drawY}
	 * だけです</b>(2026-07-25に実測確認)。
	 * </p>
	 *
	 * <table border="1">
	 * <caption>ページ軸の次元と向き</caption>
	 * <tr><th>書字方向</th><th>ページ軸</th><th>向き</th><th>X座標に足すもの</th></tr>
	 * <tr><td>TB(横書き)</td><td>Y</td><td>正(上→下)</td><td>行方向のみ</td></tr>
	 * <tr><td>RL(縦書き・右→左)</td><td>X</td><td><b>負</b></td><td>{@code parentPageExtent - childPageEnd}</td></tr>
	 * <tr><td>LR(縦書き・左→右)</td><td>X</td><td>正</td><td>{@code childPageStart}</td></tr>
	 * </table>
	 *
	 * <p>
	 * LRがTBと同じ「始端を足すだけ」の形になるのは偶然ではなく、
	 * <b>向きが正である</b>という同じ性質の現れです。
	 * </p>
	 *
	 * <p>
	 * <b>この関数を迂回して自前で符号計算をしないこと。</b>
	 * {@code x += 親の内寸; x - 子のpageAxis - 子の寸法}という手書きは
	 * RL専用式であり、LRで誤ります(2026-07-25にLRを実装した際、
	 * FlowContainer・Floatings・表・救済分割に計10箇所の手書きが見つかり、
	 * すべてこの関数へ寄せた)。
	 * </p>
	 *
	 * @param flow             書字方向
	 * @param x                親の物理X原点
	 * @param parentPageExtent 親のページ方向寸法
	 * @param childPageStart   子のページ方向始端
	 * @param childPageEnd     子のページ方向終端(始端+子の寸法)
	 * @param childLineStart   子の行方向始端
	 * @return 子の物理X座標
	 */
	public static double drawX(WritingMode flow, double x, double parentPageExtent, double childPageStart,
			double childPageEnd, double childLineStart) {
		return switch (flow) {
		case TB -> x + childLineStart;
		case RL -> x + parentPageExtent - childPageEnd;
		case LR -> x + childPageStart;
		};
	}

	/**
	 * 親の物理原点 y から、論理位置に置かれる子の物理Y座標を返します。
	 * 向きの扱いは{@link #drawX}の説明を参照(縦書きではページ軸がXなので、
	 * Yは常に行方向だけで決まり、RLとLRで違いはありません)。
	 *
	 * @param flow           書字方向
	 * @param y              親の物理Y原点
	 * @param childPageStart 子のページ方向始端
	 * @param childLineStart 子の行方向始端
	 * @return 子の物理Y座標
	 */
	public static double drawY(WritingMode flow, double y, double childPageStart, double childLineStart) {
		return flow.isVertical() ? y + childLineStart : y + childPageStart;
	}

	public static double computeInsetsTop(Insets insets, double ref) {
		switch (insets.getTopType()) {
		case ABSOLUTE:
			return insets.getTop();
		case RELATIVE:
			return insets.getTop() * ref;
		case MIXED:
			return insets.getTop() + insets.getTopRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsLeft(Insets insets, double ref) {
		switch (insets.getLeftType()) {
		case ABSOLUTE:
			return insets.getLeft();
		case RELATIVE:
			return insets.getLeft() * ref;
		case MIXED:
			return insets.getLeft() + insets.getLeftRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsRight(Insets insets, double ref) {
		switch (insets.getRightType()) {
		case ABSOLUTE:
			return insets.getRight();
		case RELATIVE:
			return insets.getRight() * ref;
		case MIXED:
			return insets.getRight() + insets.getRightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsBottom(Insets insets, double ref) {
		switch (insets.getBottomType()) {
		case ABSOLUTE:
			return insets.getBottom();
		case RELATIVE:
			return insets.getBottom() * ref;
		case MIXED:
			return insets.getBottom() + insets.getBottomRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetX(Offset offset, IBox containerBox) {
		switch (offset.getXType()) {
		case ABSOLUTE:
			return offset.getX();
		case RELATIVE:
			// this.offsetX = pos.offset.getX() * container.getInnerWidth();
			// break;
		case MIXED:
			// RELATIVE同様、この経路は未実装(既存のTODO。MIXEDもさしあたり
			// RELATIVEと同じ扱いにして例外だけは避ける)。
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetY(Offset offset, IBox containerBox) {
		switch (offset.getYType()) {
		case ABSOLUTE:
			return offset.getY();
		case RELATIVE:
			// this.offsetY = pos.offset.getY() * container.getInnerWidth();
			// break;
		case MIXED:
			// RELATIVE同様、この経路は未実装(既存のTODO。MIXEDもさしあたり
			// RELATIVEと同じ扱いにして例外だけは避ける)。
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static void calculateReplacedSize(Builder builder, AbstractReplacedBox replacedBox) {
		//
		// ■ 幅と高さの計算
		//
		final double refWidth, refHeight, refMaxWidth, refMaxHeight;
		final AbstractContainerBox containerBox = builder.getFlowBox();
		final BlockParams params = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		replacedBox.calculateFrame(lineSize);
		if (params.flow.isVertical()) {
			// 縦書き
			AbstractContainerBox box;
			if (containerBox == builder.getContextBox()) {
				box = builder.getFixedWidthContextBox();
			} else {
				box = builder.getFixedWidthFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// セル内でページ送りされた場合
					return;
				}
				refMaxWidth = refWidth = LayoutUtils.NONE;
				refMaxHeight = refHeight = LayoutUtils.NONE;
			} else {
				refWidth = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : box.getInnerWidth();
				refMaxWidth = box.getInnerWidth();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxHeight =refHeight = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (containerBox == builder.getContextBox()) {
						box = builder.getFixedHeightContextBox();
					} else {
						box = builder.getFixedHeightFlowBox();
					}
					if (box == null) {
						refMaxHeight =refHeight = LayoutUtils.NONE;
					} else {
						refMaxHeight =refHeight = box.getLineSize();
					}
				} else {
					refMaxHeight =refHeight = lineSize;
				}
			}
		} else {
			// 横書き
			AbstractContainerBox box;
			if (containerBox == builder.getContextBox()) {
				box = builder.getFixedHeightContextBox();
			} else {
				box = builder.getFixedHeightFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// セル内でページ送りされた場合
					return;
				}
				refMaxHeight = refHeight = LayoutUtils.NONE;
				refMaxWidth = refWidth = LayoutUtils.NONE;
			} else {
				refHeight = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : box.getInnerHeight();
				refMaxHeight = box.getInnerHeight();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxWidth = refWidth = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (containerBox == builder.getContextBox()) {
						box = builder.getFixedWidthContextBox();
					} else {
						box = builder.getFixedWidthFlowBox();
					}
					if (box == null) {
						refMaxWidth = refWidth = LayoutUtils.NONE;
					} else {
						refMaxWidth = refWidth = box.getLineSize();
					}
				} else {
					refMaxWidth = refWidth = lineSize;
				}
			}
		}
		replacedBox.calculateSize(refWidth, refHeight, refMaxWidth, refMaxHeight);
	}

	public static double getMaxAdvance(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		final double lineSize;
		if (params.flow.isVertical()) {
			// 縦書き
			lineSize = box.getInnerHeight();
		} else {
			// 横書き
			lineSize = box.getInnerWidth();
		}
		return lineSize;
	}

	public static int getColumnCount(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		if (LayoutUtils.isNone(params.columns.width)) {
			return params.columns.count;
		}
		final double lineSize = LayoutUtils.getMaxAdvance(box);
		if (params.columns.width >= lineSize) {
			return 1;
		}
		return (int) Math.floor((lineSize + params.columns.gap) / (params.columns.width + params.columns.gap));
	}

	public static void setupImposition(final UserAgent ua, final Imposition imposition) {
		imposition.setAutoRotate(UAProps.OUTPUT_AUTO_ROTATE.get(ua));
		imposition.setAlign(UAProps.OUTPUT_FIT_TO_PAPER.get(ua));

		// 左右断ちしろ
		{
			String s = UAProps.OUTPUT_HTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(imposition.getTrimTop(), l, imposition.getTrimBottom(), l);
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_HTRIM.name, s);
			}
		}
		// 上下断ちしろ
		{
			String s = UAProps.OUTPUT_VTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(l, imposition.getTrimRight(), l, imposition.getTrimLeft());
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_VTRIM.name, s);
			}
		}
		{
			double[] trims;
			String s = UAProps.OUTPUT_TRIMS.getString(ua);
			if (s != null) {
				String[] values = s.split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
					trims = null;
				} else {
					trims = new double[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							trims[i] = length.getLength();
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
							trims = null;
							break;
						}
					}
				}
				switch (trims.length) {
				case 1:
					imposition.setTrims(trims[0], trims[0], trims[0], trims[0]);
					break;
				case 2:
					imposition.setTrims(trims[0], trims[1], trims[0], trims[1]);
					break;
				case 3:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[1]);
					break;
				case 4:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[3]);
					break;
				}
			}
		}

		// トンボ
		switch (UAProps.OUTPUT_MARKS.get(ua)) {
		case NONE:
			imposition.setTrims(0, 0, 0, 0);
			imposition.setCuttingMargin(0);
			imposition.setNote(null);
			break;
		case CROP:
			imposition.setCrop(true);
			imposition.setNote("page {0}");
			break;
		case CROSS:
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case BOTH:
			imposition.setCrop(true);
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case HIDDEN:
			imposition.setNote("page {0}");
			break;
		default:
			throw new IllegalStateException();
		}
		imposition.setClip(UAProps.OUTPUT_CLIP.getBoolean(ua));

		// 背表紙
		{
			String s = UAProps.OUTPUT_MARKS_SPINE_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setSpineWidth(l);
					if (imposition.getNote() != null) {
						imposition.setNote(imposition.getNote() + " / spine " + s);
					}
				} else {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_MARKS_SPINE_WIDTH.name, s);
				}
			}
		}

		{
			// 用紙幅
			String s = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperWidth(l);
				} else {
					imposition.fitPaperWidth();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_WIDTH.name, s);
				}
			} else {
				imposition.fitPaperWidth();
			}
		}

		{
			// 用紙高さ
			String s = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperHeight(l);
				} else {
					imposition.fitPaperHeight();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_HEIGHT.name, s);
				}
			} else {
				imposition.fitPaperHeight();
			}
		}
	}

}
