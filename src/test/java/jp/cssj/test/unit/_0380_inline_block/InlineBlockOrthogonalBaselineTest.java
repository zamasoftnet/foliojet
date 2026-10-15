package jp.cssj.test.unit._0380_inline_block;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * An inline-block whose content is in the other writing direction (2026-10-16, fit sweep seed 20063206). A box in the
 * other direction gives no baseline, so the inline-block takes the last line box in its own direction, or puts its
 * bottom margin edge on the baseline when there is none, as browsers do. The empty {@code ref} box, which has no line
 * box at all, shows where that edge goes.
 *
 * <ul>
 * <li>A vertical box of one line ({@code v1}) or three ({@code v3}), one inside a wrapper ({@code vw}), and one with
 * padding below ({@code vp}) put their bottom where {@code ref} does. The horizontal distance from the vertical box's
 * last line to its left edge was taken as the depth below the baseline, so they sat lower the fewer lines they had.</li>
 * <li>A line before the vertical box ({@code pv}) or before an empty block ({@code pe}) is the baseline: the box starts
 * where a one-line box ({@code x1}) does.</li>
 * <li>On a vertical line, a box without a baseline is centered on the line: a horizontal box of one line ({@code h1})
 * or three ({@code h3}) centers where {@code vref} does, and a line before it ({@code ph}) is the baseline, as for
 * {@code y1}.</li>
 * </ul>
 */
public class InlineBlockOrthogonalBaselineTest extends AbstractTestCase {
	public InlineBlockOrthogonalBaselineTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0380-inline-block/orthogonal-baseline.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// horizontal
		for (final String id : new String[] { "v1", "v3", "vw", "vp" }) {
			assertEquals(id, bottom("ref"), bottom(id), 0.1);
		}
		assertEquals("pv", top("x1"), top("pv"), 0.1);
		assertEquals("pe", top("x1"), top("pe"), 0.1);
		// vertical-rl
		final double center = (left("vref") + right("vref")) / 2;
		for (final String id : new String[] { "h1", "h3" }) {
			assertEquals(id, center, (left(id) + right(id)) / 2, 0.1);
		}
		assertEquals("ph", right("y1"), right("ph"), 0.1);
	}

	private double[] get(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id);
	}

	private double left(final String id) {
		return this.get(id)[0];
	}

	private double top(final String id) {
		return this.get(id)[1];
	}

	private double right(final String id) {
		return this.get(id)[0] + this.get(id)[2];
	}

	private double bottom(final String id) {
		return this.get(id)[1] + this.get(id)[3];
	}

	private boolean record(final String id, final IBox box, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.at.put(id, new double[] { x, y, box.getWidth(), box.getHeight() });
			return true;
		}
		return false;
	}

	public boolean check_ref(IBox box, int pageNumber, double x, double y) {
		return this.record("ref", box, x, y);
	}

	public boolean check_x1(IBox box, int pageNumber, double x, double y) {
		return this.record("x1", box, x, y);
	}

	public boolean check_v1(IBox box, int pageNumber, double x, double y) {
		return this.record("v1", box, x, y);
	}

	public boolean check_v3(IBox box, int pageNumber, double x, double y) {
		return this.record("v3", box, x, y);
	}

	public boolean check_vw(IBox box, int pageNumber, double x, double y) {
		return this.record("vw", box, x, y);
	}

	public boolean check_vp(IBox box, int pageNumber, double x, double y) {
		return this.record("vp", box, x, y);
	}

	public boolean check_pv(IBox box, int pageNumber, double x, double y) {
		return this.record("pv", box, x, y);
	}

	public boolean check_pe(IBox box, int pageNumber, double x, double y) {
		return this.record("pe", box, x, y);
	}

	public boolean check_vref(IBox box, int pageNumber, double x, double y) {
		return this.record("vref", box, x, y);
	}

	public boolean check_y1(IBox box, int pageNumber, double x, double y) {
		return this.record("y1", box, x, y);
	}

	public boolean check_h1(IBox box, int pageNumber, double x, double y) {
		return this.record("h1", box, x, y);
	}

	public boolean check_h3(IBox box, int pageNumber, double x, double y) {
		return this.record("h3", box, x, y);
	}

	public boolean check_ph(IBox box, int pageNumber, double x, double y) {
		return this.record("ph", box, x, y);
	}
}
