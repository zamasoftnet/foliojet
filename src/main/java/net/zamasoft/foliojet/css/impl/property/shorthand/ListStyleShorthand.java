package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.net.URISyntaxException;

import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.URIValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleImage;
import net.zamasoft.foliojet.css.impl.property.content.ListStylePosition;
import net.zamasoft.foliojet.css.impl.property.content.ListStyleType;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class ListStyleShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new ListStyleShorthand();

	protected ListStyleShorthand() {
		super("list-style");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives) throws PropertyException {
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(ListStyleType.INFO, global);
			primitives.set(ListStyleImage.INFO, global);
			primitives.set(ListStylePosition.INFO, global);
			return;
		}

		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (ValueUtils.isImage(lu)) {
				try {
					// url()とimage-set()(2026-08-29)
					final URIValue imageUri = ValueUtils.toImage(ua, uri, lu);
					if (imageUri == null) {
						throw new PropertyException();
					}
					primitives.set(ListStyleImage.INFO, imageUri);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, ValueUtils.uriText(lu));
				}
			} else if (lu instanceof CssToken.Ident ident) {
				final Value styleType = GeneratedValueUtils.toListStyleType(ident.name());
				if (styleType != null) {
					primitives.set(ListStyleType.INFO, styleType);
					continue;
				}

				final Value position = GeneratedValueUtils.toListStylePosition(ident.name());
				if (position == null) {
					// 組み込みでも位置キーワードでもない識別子は
					// 著者定義カウンタスタイルの名前とみなす
					primitives.set(ListStyleType.INFO, CounterStyles.styleValue(ua, ident.name()));
					continue;
				}
				primitives.set(ListStylePosition.INFO, position);
			} else {
				throw new PropertyException();
			}
		}
	}

}