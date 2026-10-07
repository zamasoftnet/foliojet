package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F1 (call/marker synthesis and document-wide numbering)
 * (consult-codex-2026-07-31-footnote.txt §5). Until F3 was wired up, the body was drawn in place,
 * so this test verifies the contracts for "::footnote-marker at the start of the body (number + separator)"
 * and "numbers advance throughout the document". ::footnote-call appears in the parent's inline flow,
 * so it is not included in the body box's text.
 */
public class FootnoteCallMarkerTest extends AbstractTestCase {
	public FootnoteCallMarkerTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-f1.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private static String text(IBox box) {
		final StringBuilder buff = new StringBuilder();
		box.getText(buff);
		return buff.toString();
	}

	/** float:footnote becomes a block, with a marker (number + separator) at the start of its body. */
	public boolean check_fn1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("marker must prefix the note body: " + text, text.startsWith("1. first note"));
			// F3: Bodies move to the footnote area at the bottom of the page (three notes stacked in document order).
			// F5 made the marker a replaced atom (a label in a fixed field), slightly changing the line height.
			// On 2026-10-04, the number's baseline was aligned with the line's baseline (previously,
			// the number's bottom sat on the baseline, increasing each line's height by its descent). Note lines
			// became shorter, lowering the area stacked from the page bottom (721.61→726.69).
			assertEquals(726.69, y, 1);
			return true;
		}
		return false;
	}

	/** Numbers advance throughout the document. */
	public boolean check_fn2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("second note must be numbered 2: " + text, text.startsWith("2. second note"));
			assertEquals(741.09, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fn3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("third note must be numbered 3: " + text, text.startsWith("3. third note"));
			assertEquals(755.49, y, 1);
			return true;
		}
		return false;
	}

	/** ::footnote-call leaves the number at the call site (the parent's inline flow). */
	public boolean check_p1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("call number must follow the reference text: " + text, text.startsWith("Alpha1"));
			assertTrue("call line must remain in the body flow: " + text, text.contains("beta."));
			assertTrue("the reference paragraph must stay near the page top: y=" + y, y < 100);
			return true;
		}
		return false;
	}

	/** The user's ::footnote-call rule (content: counter(footnote)) overrides it. */
	public boolean check_p3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("custom call content must apply: " + text, text.startsWith("Custom[3]"));
			return true;
		}
		return false;
	}
}
