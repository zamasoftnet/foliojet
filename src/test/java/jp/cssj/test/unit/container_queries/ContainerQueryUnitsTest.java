package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests {@code cqw}/{@code cqi} units in {@code @container} implementation stage 6
 * (development record §5).
 *
 * <p>
 * {@code #box}, a child of {@code #outer} (container-type: inline-size, 300 pt), has
 * {@code width: 50cqi}, which should resolve to 50% of outer's measured inline-size (300 pt) = 150 pt.
 * With `processing.pass-count=2`, outer's measurement is finalized in the first MIDDLE_PASS,
 * and `#box` reads it in the final pass (the same timing as fixture 1).
 * For {@code #free}, which has no query-container ancestor,
 * {@code cqw}/{@code cqi} should resolve to 0 as specified.
 * </p>
 */
public class ContainerQueryUnitsTest extends AbstractTestCase {
	public ContainerQueryUnitsTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/cq-units.html");
		this.session.property("processing.pass-count", "2");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_box(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(150.0, box.getWidth(), 0.5);
			return true;
		}
		return false;
	}

	public boolean check_free(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(0.0, box.getWidth(), 0.5);
			return true;
		}
		return false;
	}
}
