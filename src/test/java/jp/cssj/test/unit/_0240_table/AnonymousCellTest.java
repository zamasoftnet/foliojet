package jp.cssj.test.unit._0240_table;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Children of a display: table that are not table parts share one anonymous cell (CSS 2.1 §17.2.1, 2026-10-09;
 * files/unittest/0240-table/anonymous-cell.html): blocks stack, inlines go on in a line, and text directly in the
 * table joins them. Until then each child got a cell of its own, side by side (doxygen's memproto and memdoc in a
 * display: table memitem), and the text was laid out above the table. The relations are Chrome 151's (in the comment
 * of the HTML file).
 */
public class AnonymousCellTest extends AbstractTestCase {
	private static final double EPSILON = 0.1;

	/** id -> {x, y, width} of the first drawn box of each element */
	private final Map<String, double[]> boxes = new HashMap<>();

	public AnonymousCellTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/0240-table/anonymous-cell.html"),
				"text/html", null);
	}

	@Override
	protected void visitAnyBox(final IBox box, final int pageNumber, final double x, final double y) {
		final StructureElement element = box.getParams().element;
		if (element != null && element.id() != null) {
			this.boxes.putIfAbsent(element.id(), new double[] { x, y, box.getWidth() });
		}
	}

	private double[] box(final String id) {
		final double[] b = this.boxes.get(id);
		assertNotNull(id + " was not drawn", b);
		return b;
	}

	@Override
	public void testDocument() throws Exception {
		super.testDocument();
		// Blocks stack in one full-width cell
		assertEquals("d1 x", this.box("p1")[0], this.box("d1")[0], EPSILON);
		assertTrue("d1 below p1", this.box("d1")[1] > this.box("p1")[1] + 10);
		assertEquals("d1 width", this.box("p1")[2], this.box("d1")[2], EPSILON);
		// The text goes between X1 and X2, in the same cell: two lines below X1
		assertEquals("x2 x", this.box("x1")[0], this.box("x2")[0], EPSILON);
		assertTrue("the text between x1 and x2", this.box("x2")[1] > this.box("x1")[1] + 25);
		// Inlines go on in a line
		assertEquals("s2 y", this.box("s1")[1], this.box("s2")[1], EPSILON);
		assertTrue("s2 after s1", this.box("s2")[0] < this.box("s1")[0] + 20);
		// An explicit cell, then one anonymous cell holding both blocks
		assertEquals("e2 x", this.box("e1")[0], this.box("e2")[0], EPSILON);
		assertTrue("e1 beside c1", this.box("e1")[0] > this.box("c1")[0] + 100);
		assertTrue("e2 below e1", this.box("e2")[1] > this.box("e1")[1] + 10);
	}
}
