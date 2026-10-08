package jp.cssj.test.unit._0380_inline_block;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Cyclic percentages (CSS Sizing 3 §5.2, 2026-10-09; files/unittest/0380-inline-block/cyclic-percentage.html). In a
 * shrink-to-fit box (inline-block, button, float, absolutely positioned box, auto table) a child's percentage width
 * counts as auto for the box's size, then resolves against the box. A button holding a width: 100% input was as wide
 * as the page (plotly.com), and an auto table holding one collapsed to a few points. The input's natural width differs from Chrome's (20ex against
 * 177px), so the expected widths come from the auto-width references r1 (input) and r2 (textarea); the relations are
 * Chrome 151's (the widths are in the comments of the HTML file).
 */
public class CyclicPercentageTest extends AbstractTestCase {
	private static final double EPSILON = 0.5;

	/** id -> {width, inner width} of the first drawn box of each element */
	private final Map<String, double[]> widths = new HashMap<>();

	public CyclicPercentageTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/0380-inline-block/cyclic-percentage.html"),
				"text/html", null);
	}

	@Override
	protected void visitAnyBox(final IBox box, final int pageNumber, final double x, final double y) {
		final StructureElement element = box.getParams().element;
		if (element != null && element.id() != null) {
			this.widths.putIfAbsent(element.id(), new double[] { box.getWidth(), box.getInnerWidth() });
		}
	}

	private double width(final String id) {
		final double[] w = this.widths.get(id);
		assertNotNull(id + " was not drawn", w);
		return w[0];
	}

	private double inner(final String id) {
		final double[] w = this.widths.get(id);
		assertNotNull(id + " was not drawn", w);
		return w[1];
	}

	@Override
	public void testDocument() throws Exception {
		super.testDocument();
		final double input = this.width("r1");
		final double textarea = this.width("r2");
		assertTrue("natural input width " + input, input > 50 && input < 200);
		// The box is as wide as the input's natural width, and the input's content box as wide as the box's
		for (final String n : new String[] { "1", "3", "6", "15" }) {
			assertEquals("c" + n, input, this.inner("c" + n), EPSILON);
			assertEquals("i" + n, this.inner("c" + n), this.inner("i" + n), EPSILON);
		}
		assertEquals("c4", input, this.inner("c4"), EPSILON);
		assertEquals("i4", this.inner("c4") / 2, this.inner("i4"), EPSILON);
		// A block child: as wide as its text
		assertTrue("c5 " + this.inner("c5"), this.inner("c5") < 100);
		assertEquals("i5", this.inner("c5"), this.width("i5"), EPSILON);
		// An auto table: the cell is as wide as the input's natural width (it collapsed to a few points)
		assertEquals("d7", input, this.inner("d7"), EPSILON);
		assertEquals("i7", this.inner("d7"), this.inner("i7"), EPSILON);
		// An image: its natural width (120px)
		assertEquals("c9", 90, this.inner("c9"), EPSILON);
		assertEquals("i9", 90, this.width("i9"), EPSILON);
		assertEquals("c16", 90, this.inner("c16"), EPSILON);
		assertEquals("i16", 90, this.width("i16"), EPSILON);
		// A specified width is not cyclic
		assertEquals("i12", 225, this.inner("i12"), EPSILON);
		assertEquals("c13", textarea, this.inner("c13"), EPSILON);
		assertEquals("i13", this.inner("c13"), this.inner("i13"), EPSILON);
	}
}
