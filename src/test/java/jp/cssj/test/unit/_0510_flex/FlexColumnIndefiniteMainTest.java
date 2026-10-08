package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Column flex containers with an indefinite main size, laid out in normal flow (F0+, 2026-10-08;
 * copperpdf4/docs/design/column-flex-indefinite-main-design.md). The free space is zero, so each item's main size
 * is its hypothetical main size, and items do not collapse margins. Expected distances are Chrome's (measured on the
 * same document, 12pt/20pt text).
 *
 * <ul>
 * <li>a: {@code flex: 0 1 96pt} gives 96pt (normal flow used the content, 20pt).</li>
 * <li>b: {@code margin: 10pt 0} items 20pt tall are 40pt apart (collapsed: 30pt).</li>
 * <li>c: {@code flex-basis: content} ignores {@code height: 60pt}: 20pt.</li>
 * <li>d: {@code flex: 0 1 10pt} with two lines grows to 40pt (automatic minimum).</li>
 * <li>e: the same with {@code overflow: hidden} stays at 10pt.</li>
 * <li>f: {@code flex: 0 1 80pt; max-height: 30pt} gives 30pt.</li>
 * <li>h: basis auto uses {@code height: 30pt}.</li>
 * <li>i: a 50pt float stays inside its item, which grows to 50pt.</li>
 * <li>j: {@code flex: 1} is a 0% basis, the content size against an indefinite main size: with
 * {@code overflow: hidden} the item keeps its two lines, 40pt (a length 0 basis made it 0 tall).</li>
 * <li>g: vertical-rl, main axis right to left: 50pt and 30pt items, then a 20pt one.</li>
 * </ul>
 */
public class FlexColumnIndefiniteMainTest extends AbstractTestCase {
	public FlexColumnIndefiniteMainTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-indefinite-main.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals(96, y("a2") - y("a1"), 0.1);
		assertEquals(40, y("b2") - y("b1"), 0.1);
		assertEquals(20, y("c2") - y("c1"), 0.1);
		assertEquals(40, y("d2") - y("d1"), 0.1);
		assertEquals(10, y("e2") - y("e1"), 0.1);
		assertEquals(30, y("f2") - y("f1"), 0.1);
		assertEquals(30, y("h2") - y("h1"), 0.1);
		assertEquals(50, y("i2") - y("i1"), 0.1);
		assertEquals(40, y("j2") - y("j1"), 0.1);
		assertEquals(30, x("g1") - x("g2"), 0.1);
		assertEquals(20, x("g2") - x("g3"), 0.1);
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

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		return this.record("a1", box, x, y);
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		return this.record("a2", box, x, y);
	}

	public boolean check_b1(IBox box, int pageNumber, double x, double y) {
		return this.record("b1", box, x, y);
	}

	public boolean check_b2(IBox box, int pageNumber, double x, double y) {
		return this.record("b2", box, x, y);
	}

	public boolean check_c1(IBox box, int pageNumber, double x, double y) {
		return this.record("c1", box, x, y);
	}

	public boolean check_c2(IBox box, int pageNumber, double x, double y) {
		return this.record("c2", box, x, y);
	}

	public boolean check_d1(IBox box, int pageNumber, double x, double y) {
		return this.record("d1", box, x, y);
	}

	public boolean check_d2(IBox box, int pageNumber, double x, double y) {
		return this.record("d2", box, x, y);
	}

	public boolean check_e1(IBox box, int pageNumber, double x, double y) {
		return this.record("e1", box, x, y);
	}

	public boolean check_e2(IBox box, int pageNumber, double x, double y) {
		return this.record("e2", box, x, y);
	}

	public boolean check_f1(IBox box, int pageNumber, double x, double y) {
		return this.record("f1", box, x, y);
	}

	public boolean check_f2(IBox box, int pageNumber, double x, double y) {
		return this.record("f2", box, x, y);
	}

	public boolean check_h1(IBox box, int pageNumber, double x, double y) {
		return this.record("h1", box, x, y);
	}

	public boolean check_h2(IBox box, int pageNumber, double x, double y) {
		return this.record("h2", box, x, y);
	}

	public boolean check_i1(IBox box, int pageNumber, double x, double y) {
		return this.record("i1", box, x, y);
	}

	public boolean check_i2(IBox box, int pageNumber, double x, double y) {
		return this.record("i2", box, x, y);
	}

	public boolean check_j1(IBox box, int pageNumber, double x, double y) {
		return this.record("j1", box, x, y);
	}

	public boolean check_j2(IBox box, int pageNumber, double x, double y) {
		return this.record("j2", box, x, y);
	}

	public boolean check_g1(IBox box, int pageNumber, double x, double y) {
		return this.record("g1", box, x, y);
	}

	public boolean check_g2(IBox box, int pageNumber, double x, double y) {
		return this.record("g2", box, x, y);
	}

	public boolean check_g3(IBox box, int pageNumber, double x, double y) {
		return this.record("g3", box, x, y);
	}
}
