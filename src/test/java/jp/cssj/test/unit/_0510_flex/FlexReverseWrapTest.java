package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * A reverse main axis with {@code flex-wrap: wrap} (2026-10-07, fit sweep seed 11931726). Lines are collected in
 * document order (css-flexbox-1 §9.3) and only the items inside each line are mirrored. The engine used to reverse
 * the whole sequence before breaking it into lines, so the last item came first and the lines could regroup.
 *
 * <ul>
 * <li>a, b, c (40pt each) in 100pt: line 1 = [a, b] with a at the right end, line 2 = [c] at the right end.</li>
 * <li>d (40), e (20), f (30) in 60pt: line 1 = [d, e] (reversing first would give [f, e] / [d]).</li>
 * <li>vertical-rl, 100pt tall: line 1 (right) = [g, h] with g at the bottom, line 2 = [i] to its left.</li>
 * <li>column-reverse, 100pt tall: column 1 = [j, k] with j at the bottom, column 2 = [l] to its right.</li>
 * </ul>
 * Positions are collected by the hooks and compared after the conversion, so the hook order does not matter.
 */
public class FlexReverseWrapTest extends AbstractTestCase {
	public FlexReverseWrapTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/reverse-wrap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// row-reverse
		assertEquals(y("a"), y("b"), 0.1);
		assertEquals(y("a") + 20, y("c"), 0.1);
		assertEquals(x("b") + 40, x("a"), 0.1);
		assertEquals(x("a"), x("c"), 0.1);
		// regrouping
		assertEquals(y("d"), y("e"), 0.1);
		assertEquals(y("d") + 20, y("f"), 0.1);
		assertEquals(x("e") + 20, x("d"), 0.1);
		assertEquals(x("e") + 30, x("f"), 0.1);
		// vertical-rl row-reverse: main axis top to bottom, mirrored; lines right to left
		assertEquals(x("g"), x("h"), 0.1);
		assertEquals(x("g") - 20, x("i"), 0.1);
		assertEquals(y("h") + 40, y("g"), 0.1);
		assertEquals(y("g"), y("i"), 0.1);
		// column-reverse
		assertEquals(x("j"), x("k"), 0.1);
		assertEquals(x("j") + 30, x("l"), 0.1);
		assertEquals(y("k") + 40, y("j"), 0.1);
		assertEquals(y("j"), y("l"), 0.1);
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

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		return this.record("a", box, x, y);
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		return this.record("b", box, x, y);
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		return this.record("c", box, x, y);
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		return this.record("d", box, x, y);
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		return this.record("e", box, x, y);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		return this.record("f", box, x, y);
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		return this.record("g", box, x, y);
	}

	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		return this.record("h", box, x, y);
	}

	public boolean check_i(IBox box, int pageNumber, double x, double y) {
		return this.record("i", box, x, y);
	}

	public boolean check_j(IBox box, int pageNumber, double x, double y) {
		return this.record("j", box, x, y);
	}

	public boolean check_k(IBox box, int pageNumber, double x, double y) {
		return this.record("k", box, x, y);
	}

	public boolean check_l(IBox box, int pageNumber, double x, double y) {
		return this.record("l", box, x, y);
	}
}
