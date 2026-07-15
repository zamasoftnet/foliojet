package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.impl.css.property.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.BorderBottomColor;
import net.zamasoft.foliojet.impl.css.property.BorderBottomStyle;
import net.zamasoft.foliojet.impl.css.property.BorderBottomWidth;
import net.zamasoft.foliojet.impl.css.property.BorderLeftColor;
import net.zamasoft.foliojet.impl.css.property.BorderLeftStyle;
import net.zamasoft.foliojet.impl.css.property.BorderLeftWidth;
import net.zamasoft.foliojet.impl.css.property.BorderRightColor;
import net.zamasoft.foliojet.impl.css.property.BorderRightStyle;
import net.zamasoft.foliojet.impl.css.property.BorderRightWidth;
import net.zamasoft.foliojet.impl.css.property.BorderTopColor;
import net.zamasoft.foliojet.impl.css.property.BorderTopStyle;
import net.zamasoft.foliojet.impl.css.property.BorderTopWidth;
import net.zamasoft.foliojet.impl.css.property.CounterIncrement;
import net.zamasoft.foliojet.impl.css.property.CounterReset;
import net.zamasoft.foliojet.impl.css.property.MarginBottom;
import net.zamasoft.foliojet.impl.css.property.MarginLeft;
import net.zamasoft.foliojet.impl.css.property.MarginRight;
import net.zamasoft.foliojet.impl.css.property.MarginTop;
import net.zamasoft.foliojet.impl.css.property.PaddingBottom;
import net.zamasoft.foliojet.impl.css.property.PaddingLeft;
import net.zamasoft.foliojet.impl.css.property.PaddingRight;
import net.zamasoft.foliojet.impl.css.property.PaddingTop;
import net.zamasoft.foliojet.impl.css.property.css3.BackgroundSize;
import net.zamasoft.foliojet.impl.css.property.shorthand.BackgroundShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderBottomShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderColorShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderLeftShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderRightShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderStyleShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderTopShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.BorderWidthShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.impl.css.property.shorthand.PaddingShorthand;

/**
 * @author MIYABE Tatsuhiko
 */
public final class PagePropertySet extends PropertySet {
	private static final PropertySet INSTANCE = new PagePropertySet();

	private PagePropertySet() {
		put(MarginShorthand.INFO, MarginTop.INFO, MarginLeft.INFO, MarginRight.INFO, MarginBottom.INFO);
		put(BorderShorthand.INFO, BorderTopShorthand.INFO, BorderLeftShorthand.INFO, BorderRightShorthand.INFO,
				BorderBottomShorthand.INFO);
		put(BorderColorShorthand.INFO, BorderTopColor.INFO, BorderLeftColor.INFO, BorderRightColor.INFO,
				BorderBottomColor.INFO);
		put(BorderStyleShorthand.INFO, BorderTopStyle.INFO, BorderLeftStyle.INFO, BorderRightStyle.INFO,
				BorderBottomStyle.INFO);
		put(BorderWidthShorthand.INFO, BorderTopWidth.INFO, BorderLeftWidth.INFO, BorderRightWidth.INFO,
				BorderBottomWidth.INFO);
		put(PaddingShorthand.INFO, PaddingTop.INFO, PaddingLeft.INFO, PaddingRight.INFO, PaddingBottom.INFO);
		put(BackgroundShorthand.INFO, BackgroundColor.INFO, BackgroundImage.INFO, BackgroundRepeat.INFO,
				BackgroundAttachment.INFO, BackgroundPosition.INFO_X, BackgroundPosition.INFO_Y);
		put(CounterIncrement.INFO, CounterReset.INFO);
		put(BackgroundSize.INFO_WIDTH, BackgroundSize.INFO_HEIGHT);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
