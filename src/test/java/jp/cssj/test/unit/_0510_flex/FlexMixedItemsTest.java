package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * Tests row placement of mixed anonymous-text and block items (Flex F1e).
 * F1d fell back for the whole container, but after §9.7 was fully wired up, anonymous items also
 * use content-derived (max-content) sizes for row placement.
 * Verifies the contracts for record count = bind count, no text loss, and cross size = maximum item height.
 */
public class FlexMixedItemsTest extends AbstractTestCase {
	public FlexMixedItemsTest(String name) {
		super(name);
	}

	private double baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/mixed-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final long records = FlexBuilder.FLEX_ITEM_RECORDS.get() - recordsBefore;
		assertEquals("record数=bind数", records, FlexBuilder.FLEX_ITEM_BINDS.get() - bindsBefore);
		assertEquals("record数=3(alpha/p/omega)", 3, records);
	}

	/** All content inside the container is present (no text loss). */
	public boolean check_ff(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseY = y;
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all content must be present: " + text,
					text.contains("alpha") && text.contains("p") && text.contains("omega"));
			return true;
		}
		return false;
	}

	/** The following block is immediately after the container height (= maximum line cross size, 20 pt). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
