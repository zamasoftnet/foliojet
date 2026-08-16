package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * {@code @container}実装・段4のfixture 1です
 * (docs/history/2026-08-15-container-queries-design.md §5)。
 *
 * <p>
 * 幅の違う2つの{@code container-type: inline-size}コンテナに同じ子部分木
 * (テキスト"X"を持つ{@code .box}、{@code ::before}が幅で切り替わる)を入れ、
 * 広いコンテナ(400pt)だけが{@code @container (min-width: 200pt)}に一致して
 * "wide-X"になり、狭いコンテナ(40pt)は不一致のまま"narrow-X"であることを
 * {@code processing.pass-count=2}(1回のMIDDLE_PASSで実測inline-sizeが
 * 確定し、LAST_PASSがそれを読む)で固定する。段4の書き込み側
 * ({@code StyleEventMachine}/{@code AbstractVisitor.visitBox})と読み出し側
 * ({@code StyleContext.merge})の両方が正しく配線されていることの、
 * パーサ単体テストでは検出できない結合(end-to-end)確認。
 * </p>
 */
public class ContainerQuerySwitchTest extends AbstractTestCase {
	public ContainerQuerySwitchTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/container-queries/inline-size-switch.html");
		this.session.property("processing.pass-count", "2");
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
