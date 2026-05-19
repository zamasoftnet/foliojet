package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.NoneValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.SrcValue;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Src.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Src extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Src();

	public static URI[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_NONE) {
			return null;
		}
		SrcValue srcValue = (SrcValue) value;
		return srcValue.getURIs();
	}

	protected Src() {
		super("src");
	}

	public Value getDefault(CSSStyle style) {
		return NoneValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		List<URI> list = new ArrayList<URI>();
		do {
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_URI:
				try {
					final URI uriv = URIHelper.resolve(ua.getDocumentContext().getEncoding(), uri, lu.getStringValue());
					list.add(uriv);
				} catch (URISyntaxException e) {
					ua.message(MessageCodes.WARN_BAD_LINK_URI, lu.getStringValue());
				}
				break;
			case LexicalUnit.SAC_FUNCTION:
				if (lu.getFunctionName().equalsIgnoreCase("local")) {
					LexicalUnit param = lu.getParameters();
					while (param != null) {
						String name = param.getStringValue();
						try {
							list.add(URIHelper.create("UTF-8", "local-font:" + name));
						} catch (URISyntaxException e) {
							throw new PropertyException();
						}
						param = param.getNextLexicalUnit();
					}
				}
				break;
			default:
				break;
			}
			lu = lu.getNextLexicalUnit();
		} while (lu != null);
		return new SrcValue((URI[]) list.toArray(new URI[list.size()]));
	}

}