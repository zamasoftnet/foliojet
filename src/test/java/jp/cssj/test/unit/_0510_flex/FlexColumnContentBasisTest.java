package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * Boundary test for column basis:content (Flex F4c, missed condition ① in the F4c recommendation).
 * basis:content requires content height even with a specified main-axis size (height:40pt),
 * so it always falls back for the whole container.
 * Record count = bind count; no partial Flex placement.
 */
public class FlexColumnContentBasisTest extends AbstractTestCase {
	public FlexColumnContentBasisTest(String name) {
		super(name);
	}

	private double baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long contentBefore = FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get();
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/column-content-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("basis:contentは常にfallback", contentBefore + 1,
				FlexBuilder.FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.get());
		assertEquals("record数=bind数", FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore,
				FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Fallback = single-column stacking (no partial Flex placement; q sits just below p's specified 40 pt height). */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
