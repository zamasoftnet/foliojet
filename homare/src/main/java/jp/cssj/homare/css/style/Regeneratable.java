package jp.cssj.homare.css.style;

import jp.cssj.homare.css.CSSElement;
import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.StyleApplier;
import jp.cssj.homare.css.StyleContext;
import jp.cssj.homare.css.value.DisplayValue;
import jp.cssj.homare.css.value.NoneValue;
import jp.cssj.homare.css.value.PositionValue;
import jp.cssj.homare.impl.css.property.CSSPosition;
import jp.cssj.homare.impl.css.property.Display;
import jp.cssj.homare.impl.css.property.ext.CSSJPageContent;
import jp.cssj.homare.impl.css.property.ext.CSSJRegeneratable;

/**
 * 再生成ボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Regeneratable.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class Regeneratable extends PageContent {
	public Regeneratable(StyleContext styleContext) {
		super(styleContext, null, null);
	}

	public void restyle(StyleBuilder builder) {
		StyleApplier applier = new StyleApplier(builder.getUserAgent(), this.styleContext);
		CSSElement page = builder.getPageElement();
		this.styleContext.startElement(page);
		int j = 0;
		for (int i = 0; i < this.types.size(); ++i) {
			switch (this.types.get(i)) {
			case START: {
				CSSStyle style = (CSSStyle) this.contents.get(j++);
				CSSElement ce = style.getCSSElement();
				if (!ce.isPseudoElement()) {
					CSSStyle parentStyle;
					if (i == 0) {
						parentStyle = style.getParentStyle();
					} else {
						parentStyle = builder.getCurrentStyle();
					}
					style = CSSStyle.getCSSStyle(style.getUserAgent(), parentStyle, ce);
					applier.startStyle(style);
					if (i == 0) {
						style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSJPageContent.INFO_NAME, NoneValue.NONE_VALUE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSJRegeneratable.INFO, NoneValue.NONE_VALUE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSPosition.INFO, PositionValue._CSSJ_CURRENT_PAGE_VALUE, CSSStyle.MODE_IMPORTANT);
					}
					// System.out.println("start: "+ce.lName);
					builder.startStyle(style);
				}
			}
				break;
			case CHARACTERS: {
				int charOffset = (Integer) this.contents.get(j++);
				char[] chars = (char[]) this.contents.get(j++);
				builder.characters(charOffset, chars, 0, chars.length);
			}
				break;
			case END: {
				CSSStyle style = (CSSStyle) this.contents.get(j++);
				CSSElement ce = style.getCSSElement();
				if (!ce.isPseudoElement()) {
					builder.endStyle();
					applier.endStyle();
					// System.out.println("/end: "+ce.lName);
				}
			}
				break;
			default:
				throw new IllegalStateException();
			}
		}
		this.styleContext.endElement();
	}
}
