package net.zamasoft.foliojet.impl.css.property.shorthand;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.ListStyleImage;
import net.zamasoft.foliojet.impl.css.property.ListStylePosition;
import net.zamasoft.foliojet.impl.css.property.ListStyleType;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

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