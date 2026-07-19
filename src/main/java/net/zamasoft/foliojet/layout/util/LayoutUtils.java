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
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
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
	 * 表の列幅・寸法が内容に依存する(実測パス=TwoPassTableBuilderが必要)で
	 * あればtrueを返します。
	 *
	 * @param box 表ボックス
	 * @return 実測が必要であればtrue
	 */
	public static boolean needsIntrinsicSizing(TableBox box) {
		final TableParams params = box.getTableParams();
		if (params.layout == TableParams.LAYOUT_AUTO) {
			// 自動レイアウトは実測が必要
			return true;
		}
		if (box.getBlockBox().getPos().getType() != PosType.FLOW) {
			// 通常のフローにない場合は実測が必要
			return true;
		}
		if (params.size.getPageType(params.flow) != LengthType.AUTO) {
			// ページ方向の寸法が指定された場合は実測が必要
			return true;
		}
		if (params.size.getLineType(params.flow) == LengthType.AUTO) {
			// 自動幅の場合は実測が必要
			return true;
		}
		return false;
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
	 * childPageEnd)に置かれる子の物理X座標を返します。
	 * 縦書きの内部規約(pageAxis は右→左)はこの関数と drawY に集約されます
	 * (将来の LR 鏡映対応はここ1点で行う)。
	 *
	 * @param flow             書字方向
	 * @param x                親の物理X原点
	 * @param parentPageExtent 親のページ方向寸法
	 * @param childPageEnd     子のページ方向終端(始端+子の寸法)
	 * @param childLineStart   子の行方向始端
	 * @return 子の物理X座標
	 */
	public static double drawX(WritingMode flow, double x, double parentPageExtent, double childPageEnd,
			double childLineStart) {
		return flow.isVertical() ? x + parentPageExtent - childPageEnd : x + childLineStart;
	}

	/**
	 * 親の物理原点 y から、論理位置に置かれる子の物理Y座標を返します。
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
