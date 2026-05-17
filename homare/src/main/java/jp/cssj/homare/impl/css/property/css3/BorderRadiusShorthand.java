package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.BorderRadiusValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderRadiusShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderRadiusShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new BorderRadiusShorthand();

	protected BorderRadiusShorthand() {
		super("border-radius");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		final LengthValue tlh, trh, brh, blh;

		tlh = ValueUtils.toLength(ua, lu);
		if (tlh == null) {
			throw new PropertyException();
		}
		if (tlh.getValueType() == Value.TYPE_INHERIT) {
			primitives.set(BorderTopLeftRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderTopRightRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomRightRadius.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(BorderBottomLeftRadius.INFO, InheritValue.INHERIT_VALUE);
			return;
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tl);
			primitives.set(BorderBottomRightRadius.INFO, tl);
			primitives.set(BorderBottomLeftRadius.INFO, tl);
			return;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
			trh = brh = blh = tlh;
			parseVertical(lu, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		trh = ValueUtils.toLength(ua, lu);
		if (trh == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, tl);
			primitives.set(BorderBottomLeftRadius.INFO, tr);
			return;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
			brh = tlh;
			blh = trh;
			parseVertical(lu, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		brh = ValueUtils.toLength(ua, lu);
		if (brh == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			final BorderRadiusValue br = BorderRadiusValue.create(brh, brh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, br);
			primitives.set(BorderBottomLeftRadius.INFO, tr);
			return;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
			blh = trh;
			parseVertical(lu, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		blh = ValueUtils.toLength(ua, lu);
		if (blh == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlh);
			final BorderRadiusValue tr = BorderRadiusValue.create(trh, trh);
			final BorderRadiusValue br = BorderRadiusValue.create(brh, brh);
			final BorderRadiusValue bl = BorderRadiusValue.create(blh, blh);
			primitives.set(BorderTopLeftRadius.INFO, tl);
			primitives.set(BorderTopRightRadius.INFO, tr);
			primitives.set(BorderBottomRightRadius.INFO, br);
			primitives.set(BorderBottomLeftRadius.INFO, bl);
			return;
		}
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_SLASH) {
			parseVertical(lu, ua, primitives, tlh, trh, brh, blh);
			return;
		}
		throw new PropertyException();
	}

	public void parseVertical(LexicalUnit lu, final UserAgent ua, final Primitives primitives, final LengthValue tlh,
			final LengthValue trh, final LengthValue brh, final LengthValue blh) throws PropertyException {
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			throw new PropertyException();
		}
		final LengthValue tlv, trv, brv, blv;
		tlv = ValueUtils.toLength(ua, lu);
		if (tlv == null) {
			throw new PropertyException();
		}
		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			trv = brv = blv = tlv;
		} else {
			trv = ValueUtils.toLength(ua, lu);
			if (trv == null) {
				throw new PropertyException();
			}
			lu = lu.getNextLexicalUnit();
			if (lu == null) {
				brv = tlv;
				blv = trv;
			} else {
				brv = ValueUtils.toLength(ua, lu);
				if (brv == null) {
					throw new PropertyException();
				}
				lu = lu.getNextLexicalUnit();
				if (lu == null) {
					blv = trv;
				} else {
					blv = ValueUtils.toLength(ua, lu);
					if (blv == null) {
						throw new PropertyException();
					}
					lu = lu.getNextLexicalUnit();
					if (lu != null) {
						throw new PropertyException();
					}
				}
			}
		}
		final BorderRadiusValue tl = BorderRadiusValue.create(tlh, tlv);
		final BorderRadiusValue tr = BorderRadiusValue.create(trh, trv);
		final BorderRadiusValue br = BorderRadiusValue.create(brh, brv);
		final BorderRadiusValue bl = BorderRadiusValue.create(blh, blv);
		primitives.set(BorderTopLeftRadius.INFO, tl);
		primitives.set(BorderTopRightRadius.INFO, tr);
		primitives.set(BorderBottomRightRadius.INFO, br);
		primitives.set(BorderBottomLeftRadius.INFO, bl);
	}
}