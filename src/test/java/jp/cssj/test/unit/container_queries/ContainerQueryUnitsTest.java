package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * {@code @container}実装・段6の{@code cqw}/{@code cqi}単位テストです
 * (docs/history/2026-08-15-container-queries-design.md §5)。
 *
 * <p>
 * {@code #outer}(container-type: inline-size、300pt)の子{@code #box}は
 * {@code width: 50cqi}——outerの実測inline-size(300pt)の50%=150ptに
 * 解決されるはず(`processing.pass-count=2`、outerの実測が1回目の
 * MIDDLE_PASSで確定し、最終パスで`#box`が読む——fixture 1と同じ
 * タイミング)。クエリコンテナの祖先を持たない{@code #free}は、
 * 仕様どおり{@code cqw}/{@code cqi}が0として解決されるはず。
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
