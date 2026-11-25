package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;

/**
 * {@code :checked} matches the checkboxes and radio buttons the document checks (2026-11-25, a test with real web pages,
 * copperpdf4 docs/history/2026-11-25-realweb-test.md). Pure-CSS tabs (Sphinx's inline tabs on pip's documentation,
 * sphinx-design, MkDocs Material) show the content after the checked radio button; it never matched, so no tab
 * printed.
 */
public class CheckedPseudoClassTest extends TestCase {
	public void testCheckedTabShows() throws Exception {
		final File dir = new File("local/checked-pseudo-class-in");
		dir.mkdirs();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
				+ ".tab-content{display:none} .tabs > input:checked + label + .tab-content{display:block}"
				+ ".box{display:none} input:checked ~ .box{display:block} .off{display:block}"
				+ " input:not(:checked) + .off{display:none}"
				+ "</style></head><body><p>BODY</p>"
				+ "<div class='tabs'><input checked='True' type='radio' name='t'><label>L1</label>"
				+ "<div class='tab-content'>FIRST-TAB</div><input type='radio' name='t'><label>L2</label>"
				+ "<div class='tab-content'>HIDDEN-SECOND-TAB</div></div>"
				+ "<div><input type='checkbox' checked><span class='box'>CHECKBOX</span></div>"
				+ "<div><input type='checkbox'><span class='off'>HIDDEN-UNCHECKED</span></div>"
				// a text field with a checked attribute is not checked
				+ "<div><input type='text' checked><span class='box'>HIDDEN-TEXT</span></div>"
				+ "</body></html>", StandardCharsets.UTF_8);
		final Map<String, double[]> texts = EndTagReopenTest.texts(input.getPath(), "checked-pseudo-class");
		assertTrue(texts.keySet().toString(), texts.containsKey("BODY"));
		assertTrue(texts.keySet().toString(), texts.containsKey("FIRST-TAB"));
		assertTrue(texts.keySet().toString(), texts.containsKey("CHECKBOX"));
		for (final String hidden : new String[] { "HIDDEN-SECOND-TAB", "HIDDEN-UNCHECKED", "HIDDEN-TEXT" }) {
			assertFalse(hidden + " is drawn", texts.containsKey(hidden));
		}
	}
}
