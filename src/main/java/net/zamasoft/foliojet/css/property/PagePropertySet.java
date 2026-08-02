package net.zamasoft.foliojet.css.property;

import net.zamasoft.foliojet.css.impl.property.background.BackgroundAttachment;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundRepeat;
import net.zamasoft.foliojet.css.impl.property.content.CounterIncrement;
import net.zamasoft.foliojet.css.impl.property.content.CounterReset;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundSize;
import net.zamasoft.foliojet.css.impl.property.shorthand.BackgroundShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderBottomShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderColorShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderLeftShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderRightShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderStyleShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderTopShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.BorderWidthShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.MarginShorthand;
import net.zamasoft.foliojet.css.impl.property.shorthand.PaddingShorthand;
import net.zamasoft.foliojet.css.impl.property.border.BorderWidth;
import net.zamasoft.foliojet.css.impl.property.border.BorderStyle;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.box.Side;
import net.zamasoft.foliojet.css.impl.property.page.PageBleed;
import net.zamasoft.foliojet.css.impl.property.page.PageMarks;
import net.zamasoft.foliojet.css.impl.property.page.PageSize;

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
		// 名前付きページN3/N4(consult-codex-2026-07-31-named-pages.txt)
		put(PageSize.INFO);
		put(PageMarks.INFO);
		put(PageBleed.INFO);
	}

	public static PropertySet getInstance() {
		return INSTANCE;
	}
}
