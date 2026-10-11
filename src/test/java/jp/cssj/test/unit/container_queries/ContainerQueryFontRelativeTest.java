package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * em and rem in {@code @container} conditions (2026-10-11): the container's and the root's font size, as in Chrome.
 * The container (225 pt, font-size 15 pt, root 7.5 pt) holds {@code (min-width: 25rem)} (187.5 pt) and not
 * {@code (min-width: 17em)} (255 pt). Before, neither parsed and both stayed "no-"; at the initial font size (12 pt)
 * it would be the other way round.
 */
public class ContainerQueryFontRelativeTest extends AbstractTestCase {
	public ContainerQueryFontRelativeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/font-relative.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_rem(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("rem-X", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_em(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("no-X", text.toString());
			return true;
		}
		return false;
	}
}
