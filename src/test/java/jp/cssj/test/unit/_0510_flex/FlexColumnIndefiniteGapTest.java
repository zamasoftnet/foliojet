package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The main-axis gap of a column flex container with an indefinite main size laid out in normal flow (F0+,
 * 2026-10-08; the streamed path ignored {@code gap}, while retained columns honored it). Expected distances are
 * Chrome's (measured on the same document, 12pt/20pt text).
 *
 * <ul>
 * <li>k: {@code gap: 10pt} separates 20pt items by 30pt.</li>
 * <li>l: {@code row-gap: 10pt} goes before a table item too (l2 is a block in its cell).</li>
 * <li>o: vertical-rl, the gap runs along the horizontal main axis.</li>
 * </ul>
 */
public class FlexColumnIndefiniteGapTest extends AbstractTestCase {
	public FlexColumnIndefiniteGapTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-indefinite-gap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals(30, y("k2") - y("k1"), 0.1);
		assertEquals(30, y("k3") - y("k2"), 0.1);
		assertEquals(30, y("l2") - y("l1"), 0.1);
		assertEquals(30, x("o1") - x("o2"), 0.1);
	}

	private double x(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[0];
	}

	private double y(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[1];
	}

	private boolean record(final String id, final IBox box, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.at.put(id, new double[] { x, y });
			return true;
		}
		return false;
	}

	public boolean check_k1(IBox box, int pageNumber, double x, double y) {
		return this.record("k1", box, x, y);
	}

	public boolean check_k2(IBox box, int pageNumber, double x, double y) {
		return this.record("k2", box, x, y);
	}

	public boolean check_k3(IBox box, int pageNumber, double x, double y) {
		return this.record("k3", box, x, y);
	}

	public boolean check_l1(IBox box, int pageNumber, double x, double y) {
		return this.record("l1", box, x, y);
	}

	public boolean check_l2(IBox box, int pageNumber, double x, double y) {
		return this.record("l2", box, x, y);
	}

	public boolean check_o1(IBox box, int pageNumber, double x, double y) {
		return this.record("o1", box, x, y);
	}

	public boolean check_o2(IBox box, int pageNumber, double x, double y) {
		return this.record("o2", box, x, y);
	}
}
