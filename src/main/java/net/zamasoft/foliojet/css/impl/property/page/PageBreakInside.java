package net.zamasoft.foliojet.css.impl.property.page;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.PageBreakInsideValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class PageBreakInside extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new PageBreakInside();

	public static PageBreakMode get(CSSStyle style) {
		PageBreakInsideValue value = (PageBreakInsideValue) style.get(INFO);
		return value.getPageBreakInside();
	}

	private PageBreakInside() {
		super("page-break-inside");
	}

	public Value getDefault(CSSStyle style) {
		return PageBreakInsideValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident luIdent) {
			String ident = luIdent.lower();
			if (ident.equals("auto")) {
				return PageBreakInsideValue.AUTO_VALUE;
			} else if (ident.equals("avoid") || ident.equals("avoid-page") || ident.equals("avoid-column")) {
				// avoid-page/avoid-columnは、それぞれの分割文脈での回避
				// (css-break-3 §3.2)。この実装の回避はページ・段のどちらの
				// 分割にも効くので、いずれもavoidとして扱う(2026-08-29)。
				// 捨てると段組内の図版が段をまたいで割れていた
				return PageBreakInsideValue.AVOID_VALUE;
			}
		}
		throw new PropertyException();
	}

}