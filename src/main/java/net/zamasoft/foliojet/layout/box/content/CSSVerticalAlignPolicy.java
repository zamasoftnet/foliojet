package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * CSS 2.1で定義されるvertical-alignを計算します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSVerticalAlignPolicy.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class CSSVerticalAlignPolicy implements VerticalAlignPolicy {
	public static final short BASELINE = 0;

	public static final short MIDDLE = BASELINE + 1;

	public static final short SUB = MIDDLE + 1;

	public static final short SUPER = SUB + 1;

	public static final short TEXT_TOP = SUPER + 1;

	public static final short TEXT_BOTTOM = TEXT_TOP + 1;

	public static final short TOP = TEXT_BOTTOM + 1;

	public static final short BOTTOM = TOP + 1;

	public static final VerticalAlignPolicy BASELINE_POLICY = new CSSVerticalAlignPolicy(BASELINE);

	public static final VerticalAlignPolicy MIDDLE_POLICY = new CSSVerticalAlignPolicy(MIDDLE);

	public static final VerticalAlignPolicy SUB_POLICY = new CSSVerticalAlignPolicy(SUB);

	public static final VerticalAlignPolicy SUPER_POLICY = new CSSVerticalAlignPolicy(SUPER);

	public static final VerticalAlignPolicy TEXT_TOP_POLICY = new CSSVerticalAlignPolicy(TEXT_TOP);

	public static final VerticalAlignPolicy TEXT_BOTTOM_POLICY = new CSSVerticalAlignPolicy(TEXT_BOTTOM);

	public static final VerticalAlignPolicy TOP_POLICY = new CSSVerticalAlignPolicy(TOP);

	public static final VerticalAlignPolicy BOTTOM_POLICY = new CSSVerticalAlignPolicy(BOTTOM);

	private final short verticalAlignType;

	protected CSSVerticalAlignPolicy(short verticalAlign) {
		this.verticalAlignType = verticalAlign;
	}

	public double getVerticalAlign(AbstractTextBox parentBox, AbstractLineBox lineBox, double ascent, double descent,
			double lineHeight, double baseline) {
		final double v;
		switch (this.verticalAlignType) {
		case CSSVerticalAlignPolicy.BASELINE:
			// ベースライン
			v = 0;
			break;

		case CSSVerticalAlignPolicy.MIDDLE: {
			// ボックスの中央線を親ボックスの基底線から親のx-heightの半分だけ上に揃える。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxXHeight() / 2.0 - ((ascent + descent) / 2.0 - descent);
			break;
		}

		case CSSVerticalAlignPolicy.SUPER: {
			// 上添え字
			// フォントの中央を親ボックスのフォントの上辺に揃える(SPEC なし)。
			// -フォントの下辺を親ボックスの中央に揃える(SPEC なし)。
			// -ベースラインを親ボックスのフォントの上辺に揃える(SPEC なし)。
			// -ベースラインを親ボックスのフォントの中央に揃える(SPEC なし)。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = descent + flm.getMaxAscent() - (ascent + descent) / 2.0;
			break;
		}

		case CSSVerticalAlignPolicy.SUB: {
			// 下添え字
			// ベースラインを親ボックスのフォント下辺に揃える(SPEC なし)。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = -flm.getMaxDescent();
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_TOP: {
			// ボックスのフォントの上辺を親ボックスのフォントの上辺に揃える。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxAscent() - ascent;
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_BOTTOM: {
			// ボックスのフォントの下辺を親要素のフォントの下辺に揃える。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = -flm.getMaxDescent() + descent;
			break;
		}

		case CSSVerticalAlignPolicy.TOP: {
			// ボックスのフォントの上辺を行の上辺に合わせる
			// v = (lineBox.getAscent() - ascent) - baseline - (lineBox.getPageSize() -
			// (ascent + descent)) / 2;
			v = lineBox.getAscent() - ascent;
			break;
		}

		case CSSVerticalAlignPolicy.BOTTOM: {
			// ボックスのフォントの下辺を行の下辺に合わせる
			// v = -(lineBox.getDescent() - descent) - baseline + (lineBox.getPageSize() -
			// (ascent + descent)) / 2;
			v = -lineBox.getDescent() + descent;
			break;
		}
		default:
			throw new IllegalStateException();
		}
		return v;
	}

	public short getVerticalAlignType() {
		return this.verticalAlignType;
	}

	public String toString() {
		switch (this.verticalAlignType) {
		case BASELINE:
			return "baseline";

		case MIDDLE:
			return "middle";

		case SUB:
			return "sub";

		case SUPER:
			return "super";

		case TEXT_TOP:
			return "text-top";

		case TEXT_BOTTOM:
			return "text-bottom";

		case TOP:
			return "top";

		case BOTTOM:
			return "bottom";

		default:
			throw new IllegalStateException();
		}
	}
}
