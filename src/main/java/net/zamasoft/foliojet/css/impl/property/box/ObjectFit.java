package net.zamasoft.foliojet.css.impl.property.box;

import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.ObjectFitValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href="https://drafts.csswg.org/css-images-3/#the-object-fit">object-fit
 * 特性</a>です。置換要素の内容のボックスへの収め方を決めます。
 *
 * @author MIYABE Tatsuhiko
 */
public class ObjectFit extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ObjectFit();

	public static ObjectFitMode get(CSSStyle style) {
		return ((ObjectFitValue) style.get(INFO)).getObjectFit();
	}

	protected ObjectFit() {
		super("object-fit");
	}

	public Value getDefault(CSSStyle style) {
		return ObjectFitValue.FILL_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			switch (ident) {
			case "fill":
				return ObjectFitValue.FILL_VALUE;
			case "contain":
				return ObjectFitValue.CONTAIN_VALUE;
			case "cover":
				return ObjectFitValue.COVER_VALUE;
			case "none":
				return ObjectFitValue.NONE_VALUE;
			case "scale-down":
				return ObjectFitValue.SCALE_DOWN_VALUE;
			}
		}
		throw new PropertyException();
	}
}
