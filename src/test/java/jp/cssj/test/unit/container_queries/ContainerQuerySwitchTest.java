package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Fixture 1 for {@code @container} implementation stage 4
 * (development record §5).
 *
 * <p>
 * Places identical child subtrees (a {@code .box} containing text "X", with {@code ::before}
 * switching by width) in two {@code container-type: inline-size} containers of different widths.
 * Only the wide container (400 pt) matches {@code @container (min-width: 200pt)} and produces
 * "wide-X"; the narrow container (40 pt) remains unmatched and produces "narrow-X".
 * In one pass (2026-10-11): the containers' sizes are estimated as they start. This end-to-end integration check
 * verifies that the writer ({@code StyleEventMachine}) and the reader ({@code StyleContext.merge}) are wired up,
 * which parser unit tests cannot detect.
 * </p>
 */
public class ContainerQuerySwitchTest extends AbstractTestCase {
	public ContainerQuerySwitchTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/inline-size-switch.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_wide(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("wide-X", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_narrow(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("narrow-X", text.toString());
			return true;
		}
		return false;
	}
}
