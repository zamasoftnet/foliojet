package jp.cssj.test.unit.container_queries;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * {@code @container}実装・段5のfixture 2(不足側)です
 * (docs/history/2026-08-15-container-queries-design.md §3/§4)。
 *
 * <p>
 * {@code #outer}(300pt) → {@code .mid}(container-type: inline-size、
 * outerへの{@code @container (min-width: 250pt)}で50pt→100ptに切り替わる)
 * → {@code #inner}({@code .mid}への{@code @container (min-width: 80pt)}で
 * "small-"→"big-"に切り替わる)という2段の入れ子。収束には3パス要る
 * (段4段落: パス1事実なし→両方フォールバック、パス2でouterの寸法を読んで
 * midが100ptへ切り替わるが、innerが読むmidの寸法はまだパス1の50ptのまま、
 * パス3でようやくinnerもmidの新しい寸法100ptを読んで切り替わる)。
 * </p>
 *
 * <p>
 * {@code processing.pass-count=2}(STRUCTURE_SCAN + MIDDLE_PASS×1 +
 * LAST_PASS、実レイアウトパスは2回)では不足し、innerは"small-X"のまま
 * 最終出力される。設計§4「黙って出さない」どおり、この不一致は
 * {@link net.zamasoft.foliojet.ua.ContainerFacts#isConverged()}が
 * {@code false}になることで検出できる(診断メッセージ自体は
 * {@code DirectSession}の{@code LOG.warning}経由でログへ出るだけなので、
 * ここでは事実の不動点フラグを直接検証する)。
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
