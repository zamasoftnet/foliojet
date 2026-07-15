package net.zamasoft.foliojet.style.util;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.PosType;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.box.params.Offset;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TableParams;
import net.zamasoft.foliojet.style.builder.Builder;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.imposition.Imposition;
import net.zamasoft.foliojet.style.part.AbsoluteInsets;
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
 * @version $Id: StyleUtils.java 1574 2018-10-26 02:44:00Z miyabe $
 */
public final class StyleUtils {
	private StyleUtils() {
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
	 * 行方向が固定されていればtrueを返します。
	 * 
	 * @param containerBox
	 * @param blockBox
	 * @return
	 */
	public static final boolean isFixedLineAxis(AbstractContainerBox containerBox, AbstractContainerBox blockBox) {
		if (blockBox.getPos().getType() == PosType.ABSOLUTE) {
			if (StyleUtils.isVertical(containerBox.getBlockParams().flow)) {
				// 縦書き
				return blockBox.getBlockParams().size.getHeightType() == LengthType.ABSOLUTE;
			} else {
				// 横書き
				return blockBox.getBlockParams().size.getWidthType() == LengthType.ABSOLUTE;
			}
		} else {
			if (StyleUtils.isVertical(containerBox.getBlockParams().flow)) {
				// 縦書き
				return blockBox.getBlockParams().size.getHeightType() != LengthType.AUTO;
			} else {
				// 横書き
				return blockBox.getBlockParams().size.getWidthType() != LengthType.AUTO;
			}
		}
	}

	/**
	 * 行方向が固定されていればtrueを返します。
	 * 
	 * @param containerBox
	 * @param replacedBox
	 * @return
	 */
	public static final boolean isFixedLineAxis(AbstractContainerBox containerBox, AbstractReplacedBox replacedBox) {
		if (replacedBox.getPos().getType() == PosType.ABSOLUTE) {
			if (StyleUtils.isVertical(containerBox.getBlockParams().flow)) {
				// 縦書き
				return replacedBox.getReplacedParams().size.getHeightType() == LengthType.ABSOLUTE;
			} else {
				// 横書き
				return replacedBox.getReplacedParams().size.getWidthType() == LengthType.ABSOLUTE;
			}
		} else {
			if (StyleUtils.isVertical(containerBox.getBlockParams().flow)) {
				// 縦書き
				return replacedBox.getReplacedParams().size.getHeightType() != LengthType.AUTO;
			} else {
				// 横書き
				return replacedBox.getReplacedParams().size.getWidthType() != LengthType.AUTO;
			}
		}
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
		assert !StyleUtils.isNone(x);
		assert !StyleUtils.isNone(y);
		assert !StyleUtils.isNone(width);
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
		case AUTO:
			return StyleUtils.NONE;
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
			if (ref == StyleUtils.NONE) {
				return StyleUtils.NONE;
			}
			return size.getWidth() * ref;
		case ABSOLUTE:
			return size.getWidth();
		case AUTO:
			return StyleUtils.NONE;
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
			if (ref == StyleUtils.NONE) {
				return StyleUtils.NONE;
			}
			return size.getHeight() * ref;
		case ABSOLUTE:
			return size.getHeight();
		case AUTO:
			return StyleUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsTop(Insets insets, double ref) {
		switch (insets.getTopType()) {
		case ABSOLUTE:
			return insets.getTop();
		case RELATIVE:
			return insets.getTop() * ref;
		case AUTO:
			return StyleUtils.NONE;
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
		case AUTO:
			return StyleUtils.NONE;
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
		case AUTO:
			return StyleUtils.NONE;
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
		case AUTO:
			return StyleUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static boolean isTwoPassTable(TableBox box) {
		TableParams params = box.getTableParams();
		if (params.layout == TableParams.LAYOUT_AUTO) {
			// 自動レイアウトは2パス
			return true;
		}
		if (box.getBlockBox().getPos().getType() != PosType.FLOW) {
			// 通常のフローにない場合は2パス
			return true;
		}
		boolean vertical = StyleUtils.isVertical(params.flow);
		if ((vertical ? params.size.getWidthType() : params.size.getHeightType()) != LengthType.AUTO) {
			// 高さが指定された場合は2パス
			return true;
		}
		if ((vertical ? params.size.getHeightType() : params.size.getWidthType()) == LengthType.AUTO) {
			// 自動幅の場合は2パス
			return true;
		}
		return false;
	}

	public static double computeOffsetX(Offset offset, IBox containerBox) {
		switch (offset.getXType()) {
		case ABSOLUTE:
			return offset.getX();
		case RELATIVE:
			// this.offsetX = pos.offset.getX() * container.getInnerWidth();
			// break;
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
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static boolean isVertical(byte progression) {
		switch (progression) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			return false;
		case AbstractTextParams.FLOW_LR:
		case AbstractTextParams.FLOW_RL:
			// 縦書き
			return true;
		default:
			throw new IllegalStateException();
		}
	}

	public static void calclateReplacedSize(Builder builder, AbstractReplacedBox replacedBox) {
		//
		// ■ 幅と高さの計算
		//
		final double refWidth, refHeight, refMaxWidth, refMaxHeight;
		final AbstractContainerBox containerBox = builder.getFlowBox();
		final BlockParams params = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		replacedBox.calculateFrame(lineSize);
		if (StyleUtils.isVertical(params.flow)) {
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
				refMaxWidth = refWidth = StyleUtils.NONE;
				refMaxHeight = refHeight = StyleUtils.NONE;
			} else {
				refWidth = box.getType()== BoxType.PAGE ? StyleUtils.NONE : box.getInnerWidth();
				refMaxWidth = box.getInnerWidth();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxHeight =refHeight = StyleUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (containerBox == builder.getContextBox()) {
						box = builder.getFixedHeightContextBox();
					} else {
						box = builder.getFixedHeightFlowBox();
					}
					if (box == null) {
						refMaxHeight =refHeight = StyleUtils.NONE;
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
				refMaxHeight = refHeight = StyleUtils.NONE;
				refMaxWidth = refWidth = StyleUtils.NONE;
			} else {
				refHeight = box.getType()== BoxType.PAGE ? StyleUtils.NONE : box.getInnerHeight();
				refMaxHeight = box.getInnerHeight();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxWidth = refWidth = StyleUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (containerBox == builder.getContextBox()) {
						box = builder.getFixedWidthContextBox();
					} else {
						box = builder.getFixedWidthFlowBox();
					}
					if (box == null) {
						refMaxWidth = refWidth = StyleUtils.NONE;
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
		if (StyleUtils.isVertical(params.flow)) {
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
		if (StyleUtils.isNone(params.columns.width)) {
			return params.columns.count;
		}
		final double lineSize = StyleUtils.getMaxAdvance(box);
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
