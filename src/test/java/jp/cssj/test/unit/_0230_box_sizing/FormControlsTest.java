package jp.cssj.test.unit._0230_box_sizing;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Buttons and selects size their border box, as Chrome's UA does (2026-10-09;
 * files/unittest/0230-box-sizing/form-controls.html). A width: 100% select or submit button overflowed its box by its
 * padding and border. The expected sizes are Chrome 151's (in the comment of the HTML file); the select keeps its
 * height (one row plus its 4pt frame). The row is 10pt since controls take Chrome's 13.333px font (2026-10-10; Chrome
 * 14.25pt high, 52.5pt for 4 rows, whose rows are taller than 1em there).
 */
public class FormControlsTest extends AbstractTestCase {
	private static final double EPSILON = 0.1;

	/** id -> {width, height, y} of the first drawn box of each element */
	private final Map<String, double[]> sizes = new HashMap<>();

	public FormControlsTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/0230-box-sizing/form-controls.html"),
				"text/html", null);
	}

	@Override
	protected void visitAnyBox(final IBox box, final int pageNumber, final double x, final double y) {
		final StructureElement element = box.getParams().element;
		if (element != null && element.id() != null) {
			this.sizes.putIfAbsent(element.id(), new double[] { box.getWidth(), box.getHeight(), y });
		}
	}

	private double[] size(final String id) {
		final double[] s = this.sizes.get(id);
		assertNotNull(id + " was not drawn", s);
		return s;
	}

	@Override
	public void testDocument() throws Exception {
		super.testDocument();
		// width: 100% of a 300px box
		for (final String id : new String[] { "s1", "s2", "s3", "s4", "s5" }) {
			assertEquals(id, 225, this.size(id)[0], EPSILON);
		}
		assertEquals("s6 width", 75, this.size("s6")[0], EPSILON);
		assertEquals("s6 height", 30, this.size("s6")[1], EPSILON);
		assertEquals("s7", 112.5, this.size("s7")[0], EPSILON);
		// The select's height: one 10pt row and the frame
		for (final String id : new String[] { "s1", "s7", "s8", "s12", "s13" }) {
			assertEquals(id + " height", 14, this.size(id)[1], EPSILON);
		}
		// The options after the first are not shown but count for the width
		assertTrue("s12 wider than s13", this.size("s12")[0] > this.size("s13")[0] + 50);
		// The selected option shows in the row of the first one, 1em high
		assertEquals("o2 y", this.size("o1")[2], this.size("o2")[2], EPSILON);
		assertEquals("o2 height", 10, this.size("o2")[1], EPSILON);
		// A multiple select without size: 4 rows of 1.2em and the frame
		assertEquals("s15 height", 4 * 12 + 4, this.size("s15")[1], EPSILON);
	}
}
