package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;

/**
 * Comprehensive regression test with many items (120) (Flex F2d).
 * Record count = bind count; exact-fit line breaking (40 pt×5=200 pt) holds for all 24 lines.
 * Verifies coordinates of the last item and the following block.
 */
public class FlexWrapManyItemsTest extends AbstractTestCase {
	public FlexWrapManyItemsTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long recordsBefore = FlexBuilder.FLEX_ITEM_RECORDS.get();
		final long bindsBefore = FlexBuilder.FLEX_ITEM_BINDS.get();
		File file = new File("files/unittest/0510-flex/wrap-many-items.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("record数=120", recordsBefore + 120, FlexBuilder.FLEX_ITEM_RECORDS.get());
		assertEquals("bind数=120", bindsBefore + 120, FlexBuilder.FLEX_ITEM_BINDS.get());
	}

	public boolean check_first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Last item (#119) = fifth column of row 24 (x=+160, y=+23×20). */
	public boolean check_last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			assertEquals(this.baseY + 460, y, 0.1);
			return true;
		}
		return false;
	}

	/** Following content sits immediately after 24 rows × 20 pt. */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseY + 480, y, 0.1);
			return true;
		}
		return false;
	}
}
