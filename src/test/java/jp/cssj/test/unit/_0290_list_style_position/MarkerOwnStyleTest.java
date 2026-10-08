package jp.cssj.test.unit._0290_list_style_position;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The marker is a child style of li, so li's non-inherited properties stay off the marker box (2026-10-08, sweep fit
 * seed 12475813). With li's own style, {@code li { height: 96pt }} made the marker, and with it the first line, 96 pt
 * tall: the second line dropped to the bottom of the li (Chrome keeps the lines 12 pt apart).
 *
 * <p>
 * The bullet images (disc, circle, square) do not make the first line taller than the line height (2026-10-08): the
 * line inside the marker box gets the strut, and the image is 0.7em tall. The first line used to be 14.4 pt.
 * </p>
 */
public class MarkerOwnStyleTest extends AbstractTestCase {
	public MarkerOwnStyleTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0290-list-style-position/marker-own-style.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final double reference = y("a2") - y("a1");
		assertEquals(12, reference, 0.1);
		assertEquals(reference, y("b2") - y("b1"), 0.1);
		assertEquals(12, y("c2") - y("c1"), 0.1);
		assertEquals(12, y("d2") - y("d1"), 0.1);
		assertEquals(12, y("e2") - y("e1"), 0.1);
	}

	private double y(final String id) {
		assertTrue("not drawn: " + id, this.at.containsKey(id));
		return this.at.get(id)[1];
	}

	private boolean record(final String id, final IBox box, final double x, final double y) {
		if (box.getType() == BoxType.INLINE) {
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
}
