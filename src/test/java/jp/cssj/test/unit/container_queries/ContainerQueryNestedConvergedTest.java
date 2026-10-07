package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Fixture 2 (sufficient passes) for {@code @container} implementation stage 5.
 * Converts the same nested document as {@link ContainerQueryNestedInsufficientPassesTest}
 * with {@code processing.pass-count=3} (three actual layout passes), verifying convergence:
 * inner correctly switches to "big-X", and {@code ContainerFacts.isConverged()} becomes {@code true}.
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
