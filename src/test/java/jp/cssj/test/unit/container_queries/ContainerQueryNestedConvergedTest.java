package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * {@code @container}実装・段5のfixture 2(十分側)です。
 * {@link ContainerQueryNestedInsufficientPassesTest}と同じ入れ子文書を
 * {@code processing.pass-count=3}(実レイアウト3パス)で変換し、収束して
 * innerが正しく"big-X"へ切り替わり、{@code ContainerFacts.isConverged()}が
 * {@code true}になることを固定する。
 */
public class ContainerQueryNestedConvergedTest extends AbstractTestCase {
	public ContainerQueryNestedConvergedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/nested-two-level.html");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertTrue("pass-count=3(実レイアウト3パス)ではinnerの寸法事実が"
				+ "不動点に達しているはず",
				this.ua.getUAContext().getContainerFacts().isConverged());
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
