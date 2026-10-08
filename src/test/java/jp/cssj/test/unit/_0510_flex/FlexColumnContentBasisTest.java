package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * Column basis:content (Flex F4c, missed condition ① in the F4c recommendation). basis:content takes the content
 * height even with a specified main-axis size (height:40pt). Until 2026-10-08 the whole container fell back to one
 * stacked column for it; now the item's content is measured at its cross size (FlexItemContent.measureMain), so the
 * item is as tall as its one line, as in Chrome. Record count = bind count; nothing falls back.
 */
public class FlexColumnContentBasisTest extends AbstractTestCase {
	public FlexColumnContentBasisTest(String name) {
		super(name);
	}

	private double baseY = Double.NaN;

	private double lineHeight = Double.NaN;

	protected void transcode() throws Exception {
		final long contentBefore = FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get();
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/column-content-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("basis:contentは測ってfallbackしない", contentBefore,
				FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get());
		assertEquals("record数=bind数", FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore,
				FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseY = y;
			// One line of 12pt text at the default line height (1.2)
			this.lineHeight = 12 * 1.2;
			return true;
		}
		return false;
	}

	/** q sits just below p's one line of content, not below p's specified 40 pt height. */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + this.lineHeight, y, 0.1);
			return true;
		}
		return false;
	}
}
