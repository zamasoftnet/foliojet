package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * GCPM {@code content()} arguments in {@code string-set} (2026-10-23, reported by tech): {@code content()} and
 * {@code content(text)} leave out the element's own {@code ::before} and {@code ::after}, which
 * {@code content(before)} and {@code content(after)} take; {@code content(first-letter)} is the first letter as
 * {@code ::first-letter} takes it, generated content included. Until then {@code content()} held both pseudo-elements
 * and the arguments were invalid.
 */
public class StringSetPseudoTest extends AbstractTestCase {
	public StringSetPseudoTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/string-set-pseudo.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private boolean assertBlockText(IBox box, String expected) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		StringBuilder text = new StringBuilder();
		box.getText(text);
		assertEquals(expected, text.toString());
		return true;
	}

	public boolean check_fa(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "Alpha");
	}

	public boolean check_fb(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "Bravo");
	}

	public boolean check_fc(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "3. | !");
	}

	// ::first-letter takes the ::before "4. " too.
	public boolean check_fe(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "4.");
	}

	// The ::before laid out in upper case still comes off.
	public boolean check_fg(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "Golf");
	}

	// A floated ::before is not in the element's text: nothing comes off.
	public boolean check_fh(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "Alpha");
	}

	// A list item's marker "1. " comes off with its ::before (codex review).
	public boolean check_fi(IBox box, int pageNumber, double x, double y) {
		return this.assertBlockText(box, "India");
	}
}
