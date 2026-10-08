package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Column flex containers with an indefinite main size (stage 2, 2026-10-08;
 * copperpdf4/docs/design/column-flex-indefinite-main-design.md §4): {@code column-reverse} and an absolute
 * {@code max-height} are retained and placed as a whole, the items whose main size depends on their content measured
 * at their cross size before the real bind. Expected positions are Chrome's (measured on the same document, 12pt/20pt
 * text), except where noted.
 *
 * <p>
 * Since 2026-10-09 a column with {@code align-items} other than stretch is no longer retained (every page relaid all
 * of its remaining content, so a long centered body grew with pages times content). It stays streamed: an item with a
 * width is aligned by {@code align-self}/{@code align-items}, while an item whose width is auto fills the column as
 * before stage 2. Chrome shrinks such an item to its content and aligns it (x1, z1, z3); the measured path of
 * {@code width: fit-content} that would do so did not break across pages when it held an app shell's whole body
 * (openprops, quarto-book), so those are recorded differences.
 * </p>
 *
 * <ul>
 * <li>r: reverse with 40pt bases: r3, r2, r1 from the top, 40pt apart.</li>
 * <li>s: reverse with content bases in a 16pt wide container: s3 (one line), s2 (two lines), s1.</li>
 * <li>m: {@code max-height: 60pt} shrinks three 40pt bases to 20pt each.</li>
 * <li>a: {@code align-items: center} in a 200pt container: a 40pt item at 80pt, an 80pt item at 60pt.</li>
 * <li>h: the same with a 60pt height (a definite main size): centered across the container, not across the widest
 * item.</li>
 * <li>t: {@code height: 100%} behaves as auto in the indefinite column: a 150pt wide aspect-ratio 1.875 item
 * takes 80pt, the next one comes after it and a 6pt gap.</li>
 * <li>w: a float holding an {@code align-items: flex-start} column of 100pt and 60pt items is 100pt wide (the row sum
 * made it 160pt).</li>
 * <li>x: {@code max-width: 60pt} bounds an auto-width item of a centered column (at the start, see above) and
 * {@code max-width: 50pt} a stretched one (at the start).</li>
 * <li>y: auto cross margins come before align-items: {@code margin: 0 auto} centers a 40pt item at 80pt,
 * {@code margin-left: auto} puts a 30pt item at 170pt, and an item with auto margins in a stretch container keeps
 * its fit-content width, centered.</li>
 * <li>z: an item with {@code margin: 0 auto} and an auto width in a stretching streamed column fills it (codex review
 * 2026-10-08, B-7; not supported, see above); in a centered column, {@code align-self: stretch} fills the 200pt and so
 * does an auto-width item.</li>
 * <li>v: vertical-rl reverse: the first item at the left, 30pt apart.</li>
 * </ul>
 */
public class FlexColumnIndefiniteRetainedTest extends AbstractTestCase {
	public FlexColumnIndefiniteRetainedTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-indefinite-retained.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals(40, y("r2") - y("r3"), 0.1);
		assertEquals(40, y("r1") - y("r2"), 0.1);
		assertEquals(20, y("s2") - y("s3"), 0.1);
		assertEquals(40, y("s1") - y("s2"), 0.1);
		assertEquals(20, y("m2") - y("m1"), 0.1);
		assertEquals(20, y("m3") - y("m2"), 0.1);
		assertEquals(80, x("a1"), 0.1);
		assertEquals(60, x("a2"), 0.1);
		assertEquals(80, x("h1"), 0.1);
		assertEquals(60, x("h2"), 0.1);
		assertEquals(86, y("t2") - y("t1"), 0.1);
		assertEquals(100, this.at.get("w0")[3], 0.1);
		// Auto-width items of a streamed column stretch, as before stage 2 (2026-10-09): Chrome shrinks x1 to its content and
		// centers it at 70pt; taking the fit-content width would need the item measured first (see the class comment)
		assertEquals(0, x("x1"), 0.1);
		assertEquals(60, this.at.get("x1")[3], 0.1);
		assertEquals(60, y("x2") - y("x1"), 0.1);
		assertEquals(0, x("x2"), 0.1);
		assertEquals(50, this.at.get("x2")[3], 0.1);
		assertEquals(80, x("y1"), 0.1);
		assertEquals(170, x("y2"), 0.1);
		assertEquals(100, x("y3") + this.at.get("y3")[3] / 2, 0.1);
		assertTrue("y3 stretched", this.at.get("y3")[3] < 50);
		// z1 and z3 are recorded differences (2026-10-09): Chrome shrinks them to their content (21pt) and centers them
		assertEquals(0, x("z1"), 0.1);
		assertEquals(200, this.at.get("z1")[3], 0.1);
		assertEquals(0, x("z2"), 0.1);
		assertEquals(200, this.at.get("z2")[3], 0.1);
		assertEquals(0, x("z3"), 0.1);
		assertEquals(200, this.at.get("z3")[3], 0.1);
		assertEquals(30, x("v2") - x("v1"), 0.1);
		assertEquals(30, x("v3") - x("v2"), 0.1);
	}

	private double x(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[0];
	}

	private double y(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[1];
	}

	private boolean record(final String id, final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			// The border box: an item aligned in a streamed column is placed by its margins (2026-10-09)
			final net.zamasoft.foliojet.layout.part.AbsoluteInsets margin = box instanceof net.zamasoft.foliojet.layout.box.AbstractBlockBox block
					? block.getFrame().margin
					: new net.zamasoft.foliojet.layout.part.AbsoluteInsets(0, 0, 0, 0);
			this.at.putIfAbsent(id,
					new double[] { x + margin.left, y, pageNumber, box.getWidth() - margin.left - margin.right });
			return true;
		}
		return false;
	}

	public boolean check_r1(IBox box, int pageNumber, double x, double y) {
		return this.record("r1", box, pageNumber, x, y);
	}

	public boolean check_r2(IBox box, int pageNumber, double x, double y) {
		return this.record("r2", box, pageNumber, x, y);
	}

	public boolean check_r3(IBox box, int pageNumber, double x, double y) {
		return this.record("r3", box, pageNumber, x, y);
	}

	public boolean check_s1(IBox box, int pageNumber, double x, double y) {
		return this.record("s1", box, pageNumber, x, y);
	}

	public boolean check_s2(IBox box, int pageNumber, double x, double y) {
		return this.record("s2", box, pageNumber, x, y);
	}

	public boolean check_s3(IBox box, int pageNumber, double x, double y) {
		return this.record("s3", box, pageNumber, x, y);
	}

	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		return this.record("m1", box, pageNumber, x, y);
	}

	public boolean check_m2(IBox box, int pageNumber, double x, double y) {
		return this.record("m2", box, pageNumber, x, y);
	}

	public boolean check_m3(IBox box, int pageNumber, double x, double y) {
		return this.record("m3", box, pageNumber, x, y);
	}

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		return this.record("a1", box, pageNumber, x, y);
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		return this.record("a2", box, pageNumber, x, y);
	}

	public boolean check_h1(IBox box, int pageNumber, double x, double y) {
		return this.record("h1", box, pageNumber, x, y);
	}

	public boolean check_h2(IBox box, int pageNumber, double x, double y) {
		return this.record("h2", box, pageNumber, x, y);
	}

	public boolean check_t1(IBox box, int pageNumber, double x, double y) {
		return this.record("t1", box, pageNumber, x, y);
	}

	public boolean check_t2(IBox box, int pageNumber, double x, double y) {
		return this.record("t2", box, pageNumber, x, y);
	}

	public boolean check_w0(IBox box, int pageNumber, double x, double y) {
		return this.record("w0", box, pageNumber, x, y);
	}

	public boolean check_x1(IBox box, int pageNumber, double x, double y) {
		return this.record("x1", box, pageNumber, x, y);
	}

	public boolean check_x2(IBox box, int pageNumber, double x, double y) {
		return this.record("x2", box, pageNumber, x, y);
	}

	public boolean check_y1(IBox box, int pageNumber, double x, double y) {
		return this.record("y1", box, pageNumber, x, y);
	}

	public boolean check_y2(IBox box, int pageNumber, double x, double y) {
		return this.record("y2", box, pageNumber, x, y);
	}

	public boolean check_y3(IBox box, int pageNumber, double x, double y) {
		return this.record("y3", box, pageNumber, x, y);
	}

	public boolean check_z1(IBox box, int pageNumber, double x, double y) {
		return this.record("z1", box, pageNumber, x, y);
	}

	public boolean check_z2(IBox box, int pageNumber, double x, double y) {
		return this.record("z2", box, pageNumber, x, y);
	}

	public boolean check_z3(IBox box, int pageNumber, double x, double y) {
		return this.record("z3", box, pageNumber, x, y);
	}

	public boolean check_v1(IBox box, int pageNumber, double x, double y) {
		return this.record("v1", box, pageNumber, x, y);
	}

	public boolean check_v2(IBox box, int pageNumber, double x, double y) {
		return this.record("v2", box, pageNumber, x, y);
	}

	public boolean check_v3(IBox box, int pageNumber, double x, double y) {
		return this.record("v3", box, pageNumber, x, y);
	}
}
