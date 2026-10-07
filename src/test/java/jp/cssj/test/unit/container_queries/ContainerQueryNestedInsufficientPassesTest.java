package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Fixture 2 (insufficient passes) for {@code @container} implementation stage 5
 * (development record §3/§4).
 *
 * <p>
 * Two nested levels: {@code #outer} (300 pt) → {@code .mid} (container-type: inline-size,
 * switching from 50 pt to 100 pt via {@code @container (min-width: 250pt)} on outer)
 * → {@code #inner} (switching from "small-" to "big-" via
 * {@code @container (min-width: 80pt)} on {@code .mid}).
 * Convergence requires three passes (stage 4 paragraph): pass 1 has no facts, so both fall back;
 * pass 2 reads outer's size and switches mid to 100 pt, but inner still reads mid's pass-1 size of 50 pt;
 * only pass 3 lets inner read mid's new 100 pt size and switch too.
 * </p>
 *
 * <p>
 * {@code processing.pass-count=2} (STRUCTURE_SCAN + MIDDLE_PASS×1 + LAST_PASS,
 * two actual layout passes) is insufficient, and inner remains "small-X" in the final output.
 * Per design §4, "do not output silently", this mismatch is detectable because
 * {@link net.zamasoft.foliojet.ua.ContainerFacts#isConverged()} becomes {@code false}.
 * The diagnostic message itself is only logged via {@code DirectSession}'s {@code LOG.warning},
 * so this test directly checks the facts' fixed-point flag.
 * </p>
 */
public class ContainerQueryNestedInsufficientPassesTest extends AbstractTestCase {
	public ContainerQueryNestedInsufficientPassesTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/nested-two-level.html");
		this.session.property("processing.pass-count", "2");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertFalse("pass-count=2 (実レイアウト2パス)ではinnerの寸法事実が"
				+ "不動点に達しないはず(3パス要る)",
				this.ua.getUAContext().getContainerFacts().isConverged());
	}

	public boolean check_inner(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("small-X", text.toString());
			return true;
		}
		return false;
	}
}
