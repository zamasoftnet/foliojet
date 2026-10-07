package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G1 track placement test (consult-codex-2026-07-31-grid-g1.txt §4).
 * Places four items (heights 30/50/20/40) in source order in two fixed columns
 * (100pt 100pt, gap 20/10). Relative to #a as the origin, checks column starts (0/120),
 * row starts (0/60=50+rowGap 10), and total Grid height (100=50+10+40).
 */
public class GridFixedLayoutTest extends AbstractTestCase {
	public GridFixedLayoutTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long records = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_RECORDS.get();
		final long binds = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_BINDS.get();
		File file = new File("files/unittest/0500-grid/fixed-2x2.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// G3a (consult-codex-2026-07-31-grid-g3.txt Q3): Every recorded item
		// is bound exactly once (upstream white-space processing absorbs whitespace
		// between elements without opening anonymous items; measured on 2026-07-31).
		final long dRecords = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_RECORDS.get() - records;
		final long dBinds = net.zamasoft.foliojet.layout.builder.impl.GridBuilder.GRID_ITEM_BINDS.get() - binds;
		assertEquals("record数=bind数", dRecords, dBinds);
		assertEquals("4item", 4, dRecords);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** Second column: column start = 100+columnGap 20. Same row, so y matches #a. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** Row 2, column 1: row start = row 1 height max(30,50)+rowGap 10=60. */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 120, x, 0.1);
			assertEquals(this.baseY + 60, y, 0.1);
			return true;
		}
		return false;
	}

	/** Following block: lower by total Grid height = 50+10+40=100 (checks parent cursor synchronization). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 100, y, 0.1);
			return true;
		}
		return false;
	}
}
