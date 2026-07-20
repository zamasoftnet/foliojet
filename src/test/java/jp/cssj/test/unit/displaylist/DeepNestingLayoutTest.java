package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 深いネスト文書のボックス木走査に対する回帰テストです(ARCHITECTURE.md
 * 不変条件6、2026-07-20。実文書=e-gov.go.jp法令ページで確認された
 * {@code StackOverflowError}の再発防止)。
 *
 * <p>
 * 修正前は{@code AbstractContainerBox.finishLayout}が「各階層で局所処理→
 * 子へ委譲」というポリモーフィックな相互再帰(ボックス種別ごとに
 * オーバーライドを跨ぐ)で実装されており、1000段超のネストで
 * {@code StackOverflowError}を起こしていた(docs/history/
 * 2026-07-18-html5-tags-and-bugfixes.mdの「別立てフォローアップ」参照。
 * 当時試みた回帰テストは、同時に未修正だったこのバグにも依存してしまい
 * 単独のテストとして成立せず見送られた)。{@link net.zamasoft.foliojet.layout.box.FinishLayoutStep}
 * による明示的ワークリストへの反復化で解消したことをこのテストで固定する。
 * </p>
 *
 * <p>
 * 2026-07-20時点の追記: 当初この修正直後は{@code AbstractContainerBox.draw}
 * 系(draw/drawFlows/drawFloatings/drawAbsolutes)がまだポリモーフィックな
 * 相互再帰のままで、深さ1500で**別の**{@code StackOverflowError}に到達する
 * ことを確認していた(finishLayout自体は無制限に解消済み)。その後、
 * frames({@link net.zamasoft.foliojet.layout.box.FramesStep})・
 * draw({@link net.zamasoft.foliojet.layout.box.DrawStep})・
 * textShape({@link net.zamasoft.foliojet.layout.box.TextShapeStep})・
 * getText({@link net.zamasoft.foliojet.layout.box.GetTextStep})の
 * 4系統も同じ反復化パターンで解消したため、このテストの深さを
 * 5000まで引き上げても成功することを確認済み。**未解消**なのは
 * {@code restyle}系(継続機構=BlockBuilderの状態機械と深く絡み合うため
 * 別途じっくり設計する必要がある、2026-07-20ユーザー判断)のみ
 * (RELIABILITY-PLAN.md台帳参照)。この境界テストはページ分割を
 * 誘発しない構成(下記generateDeeplyNestedDivs参照)のため、restyle系は
 * 経路に入らず引き続き検証対象外。
 * </p>
 */
public class DeepNestingLayoutTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * finishLayout・frames・draw・textShape・getTextの反復化で解消した深さ
	 * (未修正時はfinishLayoutが1000段前後、drawが1500段前後で
	 * StackOverflowErrorしていた)を大きく上回る深さで、レイアウトから
	 * PDF出力までが完了することを確認する。
	 */
	public void testDeeplyNestedDivsLayoutWithoutStackOverflow() throws Exception {
		final File doc = generateDeeplyNestedDivs("deep-nesting-5000", 5000);
		final File pdf = new File("local/unittest/display-list/deep-nesting-5000.pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		assertTrue("PDFが出力されていません", pdf.length() > 0);
	}

	/**
	 * 指定段数だけ{@code <div>}を入れ子にした文書を生成する(golden比較
	 * 対象ではないため files/unittest へは置かず、local/unittest へ都度
	 * 生成する)。
	 *
	 * @param name  生成ファイル名(拡張子なし)
	 * @param depth ネスト段数
	 */
	private static File generateDeeplyNestedDivs(String name, int depth) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			// borderやpaddingを付けない: 各段が高さを持つと1ページに収まらず
			// 継続機構(OpenShape.depth、別の既知の再帰課題)を誘発してしまい、
			// finishLayoutの反復化を単独で検証できなくなる
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			w.write("nested content");
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
