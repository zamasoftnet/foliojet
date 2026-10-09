package jp.cssj.test.unit._0290_list_style_position;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The indent of a list is padding-inline-start: 40px and of dd margin-inline-start: 40px; lists have 1em above and
 * below and none in a list (HTML Standard's rendering section, Chrome; 2026-10-09;
 * files/unittest/0290-list-style-position/list-indent.html). With the indent as a margin, an author's
 * {@code ul { margin: 0 }} took it away and put the markers out of the page, and {@code ul { padding-left: 0 }} left it.
 * The expected positions are Chrome 151's (in the comment of the HTML file), in pt.
 */
public class ListIndentTest extends AbstractTestCase {
	private static final double EPSILON = 0.1;

	/** id -> {x, y} of the first drawn box of each element (the margin box's corner) */
	private final Map<String, double[]> boxes = new HashMap<>();

	public ListIndentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0290-list-style-position/list-indent.html"), "text/html", null);
	}

	@Override
	protected void visitAnyBox(final IBox box, final int pageNumber, final double x, final double y) {
		final StructureElement element = box.getParams().element;
		if (element != null && element.id() != null) {
			this.boxes.putIfAbsent(element.id(), new double[] { x, y });
		}
	}

	private double x(final String id) {
		final double[] b = this.boxes.get(id);
		assertNotNull(id + " was not drawn", b);
		return b[0];
	}

	private double y(final String id) {
		final double[] b = this.boxes.get(id);
		assertNotNull(id + " was not drawn", b);
		return b[1];
	}

	@Override
	public void testDocument() throws Exception {
		super.testDocument();
		// The list items 40px in, also with the list's margin 0; none with padding-left: 0
		for (final String id : new String[] { "t1", "t2", "t5", "t6", "t6b" }) {
			assertEquals(id, 30, this.x(id), EPSILON);
		}
		assertEquals("t3", 0, this.x("t3"), EPSILON);
		// A nested list: 40px more, no margin above it, and 80px for a ul in an ol
		assertEquals("t4b", 60, this.x("t4b"), EPSILON);
		assertEquals("t4b right below t4's first line", this.y("t4") + 15, this.y("t4b"), EPSILON);
		assertEquals("t7", 60, this.x("t7"), EPSILON);
		// 1em (16px) above a list, none with margin: 0
		assertEquals("t1 below b1", this.y("b1") + 0.75 + 12, this.y("t1"), EPSILON);
		assertEquals("t2 at b2", this.y("b2") + 0.75, this.y("t2"), EPSILON);
		// Vertical writing: the indent is at the top
		assertEquals("t8 below l8", this.y("l8") + 30, this.y("t8"), EPSILON);
	}
}
