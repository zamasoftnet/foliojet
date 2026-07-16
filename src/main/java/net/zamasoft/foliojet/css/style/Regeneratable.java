package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.StyleApplier;
import net.zamasoft.foliojet.css.StyleContext;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.impl.css.property.box.CSSPosition;
import net.zamasoft.foliojet.impl.css.property.box.Display;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJPageContent;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJRegeneratable;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 再生成ボックスです。
 *
 * @author MIYABE Tatsuhiko
 */
class Regeneratable extends PageContent {
	public Regeneratable(StyleContext styleContext) {
		super(styleContext, null, null);
	}

	public void restyle(StyleBuilder builder) {
		StyleApplier applier = new StyleApplier(builder.getUserAgent(), this.styleContext);
		CSSElement page = builder.getPageElement();
		this.styleContext.startElement(page);
		int i = 0;
		for (Item item : this.items) {
			switch (item) {
			case Start(CSSStyle recorded, net.zamasoft.foliojet.ua.CounterScope[] counters) -> {
				CSSElement ce = recorded.getCSSElement();
				if (!ce.isPseudoElement()) {
					CSSStyle parentStyle;
					if (i == 0) {
						parentStyle = recorded.getParentStyle();
					} else {
						parentStyle = builder.getCurrentStyle();
					}
					CSSStyle style = CSSStyle.getCSSStyle(recorded.getUserAgent(), parentStyle, ce);
					applier.startStyle(style);
					if (i == 0) {
						style.set(Display.INFO, DisplayValue.BLOCK_VALUE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSJPageContent.INFO_NAME, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSJRegeneratable.INFO, KeywordValue.NONE, CSSStyle.MODE_IMPORTANT);
						style.set(CSSPosition.INFO, PositionValue._CSSJ_CURRENT_PAGE_VALUE, CSSStyle.MODE_IMPORTANT);
					}
					builder.startStyle(style);
				}
			}
			case Chars(int charOffset, char[] ch) -> builder.characters(charOffset, ch, 0, ch.length);
			case End(CSSStyle recorded, net.zamasoft.foliojet.ua.CounterScope[] endCounters) -> {
				if (!recorded.getCSSElement().isPseudoElement()) {
					builder.endStyle();
					applier.endStyle();
				}
			}
			}
			++i;
		}
		this.styleContext.endElement();
	}
}
