package jp.cssj.test.unit._0030_text_align;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * A line too long for its box is start-aligned whatever text-align says, overflowing at the end edge (CSS Text 3 §7.1,
 * 2026-10-09; files/unittest/0030-text-align/overflow-start.html). Copper centered such a line, so an icon wider than
 * its centered button stuck out of both sides. The expected positions are Chrome 151's (in the comment of the HTML
 * file): 150px spans in 100px boxes.
 */
public class OverflowStartTest extends AbstractTestCase {
	private static final double EPSILON = 0.1;

	/** id -> x of the first drawn box of each element */
	private final Map<String, Double> xs = new HashMap<>();

	public OverflowStartTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/0030-text-align/overflow-start.html"),
				"text/html", null);
	}

	@Override
	protected void visitAnyBox(final IBox box, final int pageNumber, final double x, final double y) {
		final StructureElement element = box.getParams().element;
		if (element != null && element.id() != null) {
			this.xs.putIfAbsent(element.id(), x);
		}
	}

	private double x(final String id) {
		final Double x = this.xs.get(id);
		assertNotNull(id + " was not drawn", x);
		return x;
	}

	@Override
	public void testDocument() throws Exception {
		super.testDocument();
		// The start of the content box: a 45pt span centered in the 75pt box starts 15pt in, and right-aligned 30pt in
		final double start = this.x("a3") - 15;
		assertEquals("a4 (right, fits)", start + 30, this.x("a4"), EPSILON);
		for (final String id : new String[] { "a1", "a2", "a7", "a9" }) {
			assertEquals(id + " (overflows: at the start)", start, this.x(id), EPSILON);
		}
		assertEquals("a8 (text-indent: 10px)", start + 7.5, this.x("a8"), EPSILON);
		// Right to left: the start is the right edge, the 112.5pt span overflows at the left
		for (final String id : new String[] { "a5", "a6" }) {
			assertEquals(id + " (rtl)", start + 75 - 112.5, this.x(id), EPSILON);
		}
	}
}
