package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;

/**
 * {@code vertical-align}を計算します。キーワードの基礎的な意味はCSS 2.1
 * §10.8.1を参照しますが、FolioJet全体のCSS実装範囲をCSS 2.1に限定するものでは
 * ありません。
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
			if (parentBox.getTextParams().flow.isVertical()) {
				// **縦組み**(2026-09-02): 行は中央線揃えなので、箱の中央を親の中央線に
				// 置く(横組みの x-height の項は無い——それを使うと半 x-height 右へ
				// 寄っていた)。文字の箱は左右対称で 0、inline-table 等の非対称な箱は
				// 中央が来るぶんだけずれる
				v = -((ascent + descent) / 2.0 - descent);
				break;
			}
			// ボックスの中央線を親ボックスの基底線から親のx-heightの半分だけ上に揃える。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxXHeight() / 2.0 - ((ascent + descent) / 2.0 - descent);
			break;
		}

		case CSSVerticalAlignPolicy.SUPER: {
			// 上添え字
			if (parentBox.getTextParams().flow.isVertical()) {
				// **縦組み**(2026-09-02): 字は中央線に置かれ、字面は左右に
				// サイズの半分ずつしか無い。横組みの式(箱の中央を親のフォントの
				// 上辺=右端へ)をそのまま使うと、上付きの箱の中央が字面の右端に
				// 来て**丸ごと列の外へ張り出す**(利用者の報告: 縦書きで脚注の
				// 番号が右にずれる)。Chrome(Blink)は上付きを親のフォントサイズの
				// 1/3だけ寄せるので、それに合わせる。横組みの式は触らない
				v = parentBox.getTextParams().fontStyle.getSize() / 3.0;
				break;
			}
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
			if (parentBox.getTextParams().flow.isVertical()) {
				// 縦組み: 上付きと同じ理由で、Chromeの下付き(親のフォントサイズの
				// 1/5)に合わせる(2026-09-02)
				v = -parentBox.getTextParams().fontStyle.getSize() / 5.0;
				break;
			}
			// ベースラインを親ボックスのフォント下辺に揃える(SPEC なし)。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = -flm.getMaxDescent();
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_TOP: {
			if (parentBox.getTextParams().flow.isVertical()) {
				// 縦組み(2026-09-02): 親のフォントの「上辺」は字面の右辺=サイズの半分。
				// 横組みの ascent(約 0.88 倍)を使うと右へはみ出していた
				v = parentBox.getTextParams().fontStyle.getSize() / 2.0 - ascent;
				break;
			}
			// ボックスのフォントの上辺を親ボックスのフォントの上辺に揃える。
			final FontListMetrics flm = parentBox.getTextParams().getFontListMetrics();
			v = flm.getMaxAscent() - ascent;
			break;
		}

		case CSSVerticalAlignPolicy.TEXT_BOTTOM: {
			if (parentBox.getTextParams().flow.isVertical()) {
				// 縦組み: 親のフォントの「下辺」は字面の左辺=サイズの半分
				v = -(parentBox.getTextParams().fontStyle.getSize() / 2.0 - descent);
				break;
			}
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
