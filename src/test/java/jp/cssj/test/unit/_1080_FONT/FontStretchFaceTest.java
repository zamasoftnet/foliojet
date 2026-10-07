package jp.cssj.test.unit._1080_FONT;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Verifies font selection via {@code font-stretch} using line widths (2026-08-29).
 * One family combines Minion Pro (normal width) and Barlow Condensed
 * ({@code font-stretch: condensed} in @font-face). condensed/semi-condensed/75% select the narrow face,
 * while normal/expanded select the normal-width face
 * (expanded has no wider face, so selects the nearest normal width).
 */
public class FontStretchFaceTest extends AbstractTestCase {
	private final Map<String, Double> widths = new HashMap<>();

	public FontStretchFaceTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1080-FONT/font-stretch.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private boolean record(final String id, final IBox box, final double expected) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		System.err.println(id + " width/" + box.getWidth());
		this.widths.put(id, box.getWidth());
		assertEquals(id, expected, box.getWidth(), 1);
		return true;
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		return record("a", box, MINION_WIDTH);
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		return record("b", box, BARLOW_WIDTH);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		return record("c", box, BARLOW_WIDTH);
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		return record("d", box, MINION_WIDTH);
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		final boolean done = record("e", box, BARLOW_WIDTH);
		if (done) {
			// The narrow face is actually narrower (check the relationship as well as fixed values).
			assertTrue("condensed < normal", this.widths.get("b") < this.widths.get("a") - 10);
		}
		return done;
	}

	/** "Width Sample" in MinionPro-Regular at 36 pt. */
	private static final double MINION_WIDTH = 201.67;
	/** "Width Sample" in BarlowCondensed-Bold at 36 pt. */
	private static final double BARLOW_WIDTH = 179.57;
}
