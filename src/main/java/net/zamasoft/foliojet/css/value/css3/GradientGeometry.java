package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;

/**
 * グラデーションの位置・寸法(解析時に持ち回る{@link QuantityValue})を、
 * 塗る箱が決まった時点でptへ落とす小道具です(2026-08-29)。
 *
 * <p>
 * em等のフォント相対長は{@code background-image}の計算値では解決されない
 * (値をそのまま保持する)ので、ここへ来ても絶対長にできない。その場合は
 * 0として扱う(記録済みの近似)。
 * </p>
 */
final class GradientGeometry {
	private GradientGeometry() {
		// unused
	}

	/** 量を基準長{@code ref}(pt)に対して解決します。解決できなければ0。 */
	static double resolve(final QuantityValue value, final double ref) {
		if (value == null) {
			return 0;
		}
		if (value instanceof PercentageValue p) {
			return p.getRatio() * ref;
		}
		if (value instanceof AbsoluteLengthValue a) {
			return a.getLength();
		}
		if (value instanceof CalcLengthValue c) {
			return c.getAbsolute() + c.getRatio() * ref;
		}
		return 0;
	}

	/** ダンプ用の短い表記。 */
	static String describe(final QuantityValue value) {
		if (value == null) {
			return "auto";
		}
		if (value instanceof PercentageValue p) {
			return String.format(java.util.Locale.ROOT, "%.0f%%", p.getPercentage());
		}
		if (value instanceof AbsoluteLengthValue a) {
			return String.format(java.util.Locale.ROOT, "%.2fpt", a.getLength());
		}
		if (value instanceof CalcLengthValue c) {
			return String.format(java.util.Locale.ROOT, "calc(%.2fpt+%.0f%%)", c.getAbsolute(), c.getRatio() * 100);
		}
		return value.toString();
	}
}
