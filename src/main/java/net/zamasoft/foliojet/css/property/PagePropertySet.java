package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.impl.css.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundColor;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundImage;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundPosition;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.impl.css.property.content.CounterIncrement;
import net.zamasoft.foliojet.impl.css.property.content.CounterReset;
import net.zamasoft.foliojet.impl.css.property.background.BackgroundSize;
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
import net.zamasoft.foliojet.impl.css.property.border.BorderWidth;
import net.zamasoft.foliojet.impl.css.property.border.BorderStyle;
import net.zamasoft.foliojet.impl.css.property.box.Padding;
import net.zamasoft.foliojet.impl.css.property.box.Margin;
import net.zamasoft.foliojet.impl.css.property.border.BorderColor;
import net.zamasoft.foliojet.impl.css.property.box.Side;

/**
 * @author MIYABE Tatsuhiko
 */
public final class PagePropertySet extends PropertySet {
	private static final PropertySet INSTANCE = new PagePropertySet();

	private PagePropertySet() {
		put(MarginShorthand.INFO, Margin.TOP, Margin.LEFT, Margin.RIGHT, Margin.BOTTOM);
		put(BorderShorthand.INFO, BorderTopShorthand.INFO, BorderLeftShorthand.INFO, BorderRightShorthand.INFO,
				BorderBottomShorthand.INFO);
		put(BorderColorShorthand.INFO, BorderColor.TOP, BorderColor.LEFT, BorderColor.RIGHT,
				BorderColor.BOTTOM);
		put(BorderStyleShorthand.INFO, BorderStyle.TOP, BorderStyle.LEFT, BorderStyle.RIGHT,
				BorderStyle.BOTTOM);
		put(BorderWidthShorthand.INFO, BorderWidth.TOP, BorderWidth.LEFT, BorderWidth.RIGHT,
				BorderWidth.BOTTOM);
		put(PaddingShorthand.INFO, Padding.TOP, Padding.LEFT, Padding.RIGHT, Padding.BOTTOM);
		// 複合特性は代表(最初の構成要素)のみ名前に紐づける
		put(BackgroundShorthand.INFO, BackgroundColor.INFO, BackgroundImage.INFO, BackgroundRepeat.INFO,
				BackgroundAttachment.INFO, BackgroundPosition.INFO_X);
		put(CounterIncrement.INFO, CounterReset.INFO);
		put(BackgroundSize.INFO_WIDTH);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
