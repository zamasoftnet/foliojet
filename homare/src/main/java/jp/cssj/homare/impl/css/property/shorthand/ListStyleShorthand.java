package jp.cssj.homare.impl.css.property.shorthand;

import java.net.URI;
import java.net.URISyntaxException;

import jp.cssj.homare.css.property.AbstractShorthandPropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.property.ShorthandPropertyInfo;
import jp.cssj.homare.css.util.GeneratedValueUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.URIValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.ListStyleImage;
import jp.cssj.homare.impl.css.property.ListStylePosition;
import jp.cssj.homare.impl.css.property.ListStyleType;
import jp.cssj.homare.message.MessageCodes;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStyleShorthand.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStyleShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new ListStyleShorthand();

	protected ListStyleShorthand() {
		super("list-style");
	}

	public void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			primitives.set(ListStyleType.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(ListStyleImage.INFO, InheritValue.INHERIT_VALUE);
			primitives.set(ListStylePosition.INFO, InheritValue.INHERIT_VALUE);
			return;
		}

		for (; lu != null; lu = lu.getNextLexicalUnit()) {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_URI: {
				final URIValue imageUri;
				try {
					imageUri = ValueUtils.toURI(ua, uri, lu);
					primitives.set(ListStyleImage.INFO, imageUri);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, lu.getStringValue());
				}
			}
				break;

			case LexicalUnit.SAC_IDENT: {
				final Value styleType = GeneratedValueUtils.toListStyleType(lu.getStringValue());
				if (styleType != null) {
					primitives.set(ListStyleType.INFO, styleType);
					break;
				}

				final Value position = GeneratedValueUtils.toListStylePosition(lu.getStringValue());
				if (position == null) {
					throw new PropertyException();
				}
				primitives.set(ListStylePosition.INFO, position);
			}
				break;

			default:
				throw new PropertyException();
			}
		}
	}

}