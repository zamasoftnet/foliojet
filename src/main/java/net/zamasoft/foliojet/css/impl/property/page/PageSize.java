package net.zamasoft.foliojet.css.impl.property.page;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.PageSizeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code @page { size }}です(名前付きページN3/N4、2026-07-31——
 * consult-codex-2026-07-31-named-pages.txt Q3)。サブセット:
 * {@code auto | <length>{1,2} | <page-size> [portrait|landscape] |
 * portrait | landscape}。規格名はISO A3-A5/B4-B5・JIS B4/B5・
 * letter/legal/ledger。相対長(em等)はサブセット外(宣言無効)。
 * {@code size:auto}の寸法はoutput.page-width/height(UA既定)。
 *
 * @author MIYABE Tatsuhiko
 */
public class PageSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageSize();

	private static final double MM = 72.0 / 25.4;

	public static PageSizeValue get(CSSStyle style) {
		return (PageSizeValue) style.get(INFO);
	}

	protected PageSize() {
		super("size");
	}

	public Value getDefault(CSSStyle style) {
		return PageSizeValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	/** 規格名の寸法(pt、縦置き)。未知はnull。 */
	private static double[] namedSize(final String name) {
		switch (name.toLowerCase()) {
		case "a3":
			return new double[] { 297 * MM, 420 * MM };
		case "a4":
			return new double[] { 210 * MM, 297 * MM };
		case "a5":
			return new double[] { 148 * MM, 210 * MM };
		case "b4":
			return new double[] { 250 * MM, 353 * MM };
		case "b5":
			return new double[] { 176 * MM, 250 * MM };
		case "jis-b4":
			return new double[] { 257 * MM, 364 * MM };
		case "jis-b5":
			return new double[] { 182 * MM, 257 * MM };
		case "letter":
			return new double[] { 8.5 * 72, 11 * 72 };
		case "legal":
			return new double[] { 8.5 * 72, 14 * 72 };
		case "ledger":
			return new double[] { 11 * 72, 17 * 72 };
		default:
			return null;
		}
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("auto")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return PageSizeValue.AUTO;
		}
		double[] named = null;
		byte orientation = PageSizeValue.ORIENTATION_NONE;
		double w = -1, h = -1;
		int lengths = 0;
		while (tokens.hasNext()) {
			if (tokens.eat("landscape")) {
				if (orientation != PageSizeValue.ORIENTATION_NONE) {
					throw new PropertyException();
				}
				orientation = PageSizeValue.ORIENTATION_LANDSCAPE;
				continue;
			}
			if (tokens.eat("portrait")) {
				if (orientation != PageSizeValue.ORIENTATION_NONE) {
					throw new PropertyException();
				}
				orientation = PageSizeValue.ORIENTATION_PORTRAIT;
				continue;
			}
			final String ident = tokens.ident();
			if (ident != null) {
				named = namedSize(ident);
				if (named == null || lengths > 0) {
					throw new PropertyException();
				}
				continue;
			}
			final CssToken lu = tokens.next();
			final Value length = ValueUtils.toLength(ua, lu);
			if (!(length instanceof AbsoluteLengthValue absolute) || named != null || lengths >= 2) {
				// 相対長・過剰値はサブセット外
				throw new PropertyException();
			}
			if (lengths == 0) {
				w = h = absolute.getLength(); // 一長さは正方形
			} else {
				h = absolute.getLength();
			}
			++lengths;
		}
		if (named != null) {
			return new PageSizeValue(named[0], named[1], orientation);
		}
		if (lengths > 0) {
			if (orientation != PageSizeValue.ORIENTATION_NONE) {
				// lengthsとorientationの併用は仕様外
				throw new PropertyException();
			}
			return new PageSizeValue(w, h, PageSizeValue.ORIENTATION_NONE);
		}
		if (orientation != PageSizeValue.ORIENTATION_NONE) {
			return new PageSizeValue(-1, -1, orientation);
		}
		throw new PropertyException();
	}
}
