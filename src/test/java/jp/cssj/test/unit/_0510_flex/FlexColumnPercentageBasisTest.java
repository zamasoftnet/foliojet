package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The used main size of column flex items whose basis is the content or a percentage of an indefinite size, and of a
 * column whose own basis is definite (codex review 2026-10-08). Expected positions and sizes are Chrome's (measured on
 * the same document, 12pt/20pt text).
 *
 * <ul>
 * <li>a: {@code flex-basis: content; height: 100pt; min-height: 0} in a centered column: the item is 20pt tall and the
 * next starts at 20pt (the box stayed 100pt tall under the next item).</li>
 * <li>b, c: {@code flex: 1; height: 100pt}, centered and stretched: the 0% basis of an indefinite size is the content,
 * 20pt (both used the height).</li>
 * <li>d: a column item with {@code flex: 0 0 100pt; min-height: 0} in a streamed column is definite: its two
 * {@code flex: 1} items are 50pt each (they stacked at 20pt).</li>
 * <li>e, f: a and b in a column retained by an absolute {@code max-height} (a to c stream since 2026-10-09).</li>
 * </ul>
 */
public class FlexColumnPercentageBasisTest extends AbstractTestCase {
	public FlexColumnPercentageBasisTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-percentage-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals(20, this.height("a1"), 0.1);
		assertEquals(20, this.y("a2") - this.y("a1"), 0.1);
		assertEquals(20, this.height("b1"), 0.1);
		assertEquals(20, this.y("b2") - this.y("b1"), 0.1);
		assertEquals(20, this.height("c1"), 0.1);
		assertEquals(20, this.y("c2") - this.y("c1"), 0.1);
		assertEquals(50, this.height("d1"), 0.1);
		assertEquals(50, this.y("d2") - this.y("d1"), 0.1);
		assertEquals(100, this.y("d3") - this.y("d1"), 0.1);
		assertEquals(20, this.height("e1"), 0.1);
		assertEquals(20, this.y("e2") - this.y("e1"), 0.1);
		assertEquals(20, this.height("f1"), 0.1);
		assertEquals(20, this.y("f2") - this.y("f1"), 0.1);
	}

	private double y(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[0];
	}

	private double height(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[1];
	}

	private boolean record(final String id, final IBox box, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.at.putIfAbsent(id, new double[] { y, box.getHeight() });
			return true;
		}
		return false;
	}

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		return this.record("a1", box, y);
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		return this.record("a2", box, y);
	}

	public boolean check_b1(IBox box, int pageNumber, double x, double y) {
		return this.record("b1", box, y);
	}

	public boolean check_b2(IBox box, int pageNumber, double x, double y) {
		return this.record("b2", box, y);
	}

	public boolean check_c1(IBox box, int pageNumber, double x, double y) {
		return this.record("c1", box, y);
	}

	public boolean check_c2(IBox box, int pageNumber, double x, double y) {
		return this.record("c2", box, y);
	}

	public boolean check_d1(IBox box, int pageNumber, double x, double y) {
		return this.record("d1", box, y);
	}

	public boolean check_d2(IBox box, int pageNumber, double x, double y) {
		return this.record("d2", box, y);
	}

	public boolean check_d3(IBox box, int pageNumber, double x, double y) {
		return this.record("d3", box, y);
	}

	public boolean check_e1(IBox box, int pageNumber, double x, double y) {
		return this.record("e1", box, y);
	}

	public boolean check_e2(IBox box, int pageNumber, double x, double y) {
		return this.record("e2", box, y);
	}

	public boolean check_f1(IBox box, int pageNumber, double x, double y) {
		return this.record("f1", box, y);
	}

	public boolean check_f2(IBox box, int pageNumber, double x, double y) {
		return this.record("f2", box, y);
	}
}
