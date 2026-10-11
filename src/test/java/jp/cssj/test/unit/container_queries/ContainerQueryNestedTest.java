package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Nested query containers in one pass (2026-10-11): {@code #outer} (300 pt) → {@code .mid} (container-type:
 * inline-size, 50 pt switched to 100 pt by {@code @container (min-width: 250pt)} on outer) → {@code #inner} ("small-"
 * switched to "big-" by {@code @container (min-width: 80pt)} on {@code .mid}). The sizes are estimated from the styles
 * as each container starts, so the inner query reads mid's switched width at once. With the sizes measured in the
 * previous pass, this took three passes (one per level); fewer left "small-X".
 */
public class ContainerQueryNestedTest extends AbstractTestCase {
	public ContainerQueryNestedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/nested-two-level.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_inner(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("big-X", text.toString());
			return true;
		}
		return false;
	}
}
