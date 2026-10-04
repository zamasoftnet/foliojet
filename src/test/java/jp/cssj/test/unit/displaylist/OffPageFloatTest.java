package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>ページ軸の寸法を明示した浮動体の中身を紙面外へ置かない</b>ことを
 * 固定します(2026-07-28新設)。
 *
 * <p>
 * {@code RandomDocumentFuzzTest}の<b>不変条件6</b>(説明のつかない紙面外への
 * 配置)で、シード100,000〜200,000の帯に残っていた欠陥です
 * (最小形は{@code local/shrink/strict-149858-min.html})。
 * </p>
 *
 * <h2>機序</h2>
 *
 * <p>
 * 浮動体が<b>ページ軸方向の寸法を明示</b>していると——縦書きの
 * {@code width}、横書きの{@code height}——{@code overflow}の既定は
 * {@code visible}なので、指定寸法を超えた中身は箱の外へ描かれます。
 * ところが{@code BreakableBuilder.classifyFloatPlacement()}は
 * <b>箱の幾何</b>({@code IBox.getPageExtent()})だけを見て「収まって
 * いる」と判定し、切断を予約しませんでした。<b>改ページが一度も
 * 起きない</b>まま、中身がページ軸方向にまっすぐ紙の外まで並びます。
 * </p>
 *
 * <p>
 * 同じ幾何前提が切断側にもありました——{@code FloatMeasurement.of()}が
 * {@code getPageExtent()}で実測を採るため、{@code FloatSplitPlan}の
 * 分岐表1(全体が切断線以前)が成立して<b>切断されません</b>。予約側だけを
 * 直すと、改ページはするが浮動体は切れない(=中身は紙の外のまま、
 * 白紙になった次ページは出力側で捨てられる)ので、<b>両方</b>を実測へ
 * 揃える必要があります。
 * </p>
 *
 * <p>
 * 通常フローには同じ補正が既にあります——
 * {@code FlowContainer.computeFlowBottoms()}の
 * {@code Math.max(内寸, getContentSize())}。<b>浮動体だけがこの補正を
 * 欠いていた</b>のが欠陥の正体です。
 * </p>
 *
 * <h2>書字方向に依存しない</h2>
 *
 * <p>
 * 元の文書は縦書き({@code writing-mode:vertical-rl}の{@code width:0pt})
 * でしたが、<b>横書きの鏡像({@code height:0pt})でも1ptの違いもなく
 * 同じ形で再現します</b>——修正前はどちらも1ページに全内容が並びました
 * (縦書き{@code x=-145.28}/紙面幅120pt、横書き{@code y=254.30}/紙面高
 * 120pt)。縦書き固有の欠陥ではないので、両方を固定します。
 * </p>
 *
 * <h2>判定について</h2>
 *
 * <p>
 * <b>ファジングの不変条件6と同じ基準</b>にします——紙面をまるごと1枚分
 * はみ出し、かつ文書中の最大の明示サイズの2倍を超えたときだけ数える
 * ({@code OffPageColumnTest}と同じ理由)。この文書の明示サイズは
 * {@code 0pt}なので猶予は0で、紙面1枚分を超えたはみ出しはすべて落ちます。
 * </p>
 *
 * <p>
 * あわせて<b>ページが2枚以上できる</b>ことも要求します。これが修正の本体
 * ——「収まらないなら改ページする」——の直接の言明で、はみ出し量の判定より
 * 機序に近いところで壊れたことに気づけます。<b>紙面外は「内容を捨てる」
 * ことでも消せる</b>ので、トークンの残存も検査します。
 * </p>
 */
public class OffPageFloatTest extends TestCase {
	/** 打ち切り時間。実測は1件あたり1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	/** 表示リストの描画位置。{@code RandomDocumentFuzzTest}と同じ書式。 */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	/** この文書が持つトークン(連番ではないので明示する)。 */
	private static final String[] TOKENS = { "T4", "T5", "T6", "T8", "T9", "T10", "T14", "T18", "T22", "T25", "T33" };

	public OffPageFloatTest(String name) {
		super(name);
	}

	/**
	 * 縦書き。{@code width}がページ軸なので{@code width:0pt}は
	 * <b>ページ軸の寸法0</b>を意味する。修正前は1ページに全内容が並び、
	 * 最悪は{@code x=-145.28}(紙面幅120pt)で不変条件6の判定は25pt。
	 */
	private static final String VERTICAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-rl}
			</style></head><body>
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			<div style="float:right;width:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testVerticalFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical", VERTICAL, 120, 400);
	}

	/**
	 * 横書きの鏡像。{@code height}がページ軸なので{@code height:0pt}が
	 * 同じ意味になる。修正前は1ページに全内容が並び、最悪は
	 * {@code y=254.30}(紙面高120pt)で判定は14pt。
	 */
	private static final String HORIZONTAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="400pt"?>
			<?jp.cssj.property name="output.page-height" value="120pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:horizontal-tb}
			</style></head><body>
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			<div style="float:right;height:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testHorizontalFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal", HORIZONTAL, 400, 120);
	}

	/**
	 * <b>浮動体がページの先頭にある</b>形(2026-10-02追加、掃過 seed 11065158)。
	 *
	 * <p>
	 * 上の2つは浮動体の前に{@code <ul>}があるので、切断は{@code FLAGS_SPLIT}で
	 * 頼まれる。ページの先頭では{@code FloatSplitPlan.classify}が
	 * {@code FLAGS_FIRST}だけを渡し、{@code FlowCutter.preDecide}が
	 * 「切断線が箱の幾何寸法より先」(ここでは0ptより先)を理由に
	 * 前ページへ残していた——溢れた中身を数えないので、浮動体は切れずに
	 * 1ページに全内容が並ぶ(修正前は縦書きで{@code x=-73.84}/紙面幅120pt、
	 * 横書きで{@code y=180.64}/紙面高120pt)。配置時の判定(2026-07-28)は
	 * 直っていたので、切断の予約までは届いていた。猶予0の紙面外の判定には
	 * 掛からない量なので、落ちるのはページ数の言明である。
	 * </p>
	 */
	private static final String VERTICAL_AT_PAGE_HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:vertical-rl}
			</style></head><body>
			<div style="float:right;width:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testVerticalFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-head", VERTICAL_AT_PAGE_HEAD, 120, 400);
	}

	/** 横書きの鏡像({@link #VERTICAL_AT_PAGE_HEAD}の{@code width}を{@code height}に)。 */
	private static final String HORIZONTAL_AT_PAGE_HEAD = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="400pt"?>
			<?jp.cssj.property name="output.page-height" value="120pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:0pt}
			body{font:normal 11pt/1.2 serif;writing-mode:horizontal-tb}
			</style></head><body>
			<div style="float:right;height:0pt">
			T4
			<p>T5</p>
			T6
			<table>
			T8
			<tr><td>T9</td></tr>
			<td>T10</td>
			</table>
			<table>
			T14
			<tr><td>T18</td></tr>
			<td>T22</td>
			<tr><td>T25</td></tr>
			</table>
			T33
			</div>
			</body></html>
			""";

	public void testHorizontalFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal-head", HORIZONTAL_AT_PAGE_HEAD, 400, 120);
	}

	/**
	 * <b>縁取りのある</b>浮動体(2026-10-02追加)。
	 *
	 * <p>
	 * {@code AbstractContainerBox.paintedPageExtent}は枠線の見える箱を箱いっぱい
	 * で打ち切っていたので、配置時の判定({@code FloatMeasurement.occupiedPageExtent})
	 * が溢れた中身を数えず、<b>ページの先頭でなくても</b>切断が予約されなかった
	 * (修正前は1ページ)。ページ先頭の形は配置と切断の両方の修正を通る。
	 * </p>
	 */
	public void testVerticalBorderedFloatWithSpecifiedPageExtentStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-border", document(120, 400, "vertical-rl", LEADING_LIST,
				"float:right;width:0pt;border:1pt solid black", "", ""), 120, 400);
	}

	public void testHorizontalBorderedFloatWithSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("horizontal-border-head", document(400, 120, "horizontal-tb", "",
				"float:right;height:0pt;border:1pt solid black", "", ""), 400, 120);
	}

	/**
	 * 浮動体の中に<b>ページ軸の寸法を明示した通常ブロック</b>があり、溢れるのは
	 * その中身という形(2026-10-02追加)。切断の判定が直下の子の幾何
	 * ({@code getContentSize()})しか見ないと、溢れを数えずに前ページへ残す。
	 */
	public void testVerticalFloatWithNestedSpecifiedPageExtentAtPageHeadStaysOnPage() throws Exception {
		assertNoUnexplainedOffPage("vertical-nested-head", document(120, 400, "vertical-rl", "",
				"float:right;width:0pt", "<div style=\"width:0pt\">", "</div>"), 120, 400);
	}

	/** 上の2形の浮動体の前にある、ページ軸の場所だけを取る内容。 */
	private static final String LEADING_LIST = """
			<ul style="list-style-position:inside">
			<li></li>
			<li></li>
			<li></li>
			<li></li>
			</ul>
			""";

	/**
	 * {@link #VERTICAL}と同じ中身の浮動体を持つ文書を組み立てます。
	 *
	 * @param pageWidth   紙面の幅(pt)
	 * @param pageHeight  紙面の高さ(pt)
	 * @param writingMode bodyの書字方向
	 * @param before      浮動体の前に置く内容
	 * @param floatStyle  浮動体のstyle
	 * @param innerOpen   浮動体の中身を包む開始タグ
	 * @param innerClose  同じく終了タグ
	 */
	private static String document(final int pageWidth, final int pageHeight, final String writingMode,
			final String before, final String floatStyle, final String innerOpen, final String innerClose) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="%dpt"?>
				<?jp.cssj.property name="output.page-height" value="%dpt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:0pt}
				body{font:normal 11pt/1.2 serif;writing-mode:%s}
				</style></head><body>
				%s<div style="%s">%s
				T4
				<p>T5</p>
				T6
				<table>
				T8
				<tr><td>T9</td></tr>
				<td>T10</td>
				</table>
				<table>
				T14
				<tr><td>T18</td></tr>
				<td>T22</td>
				<tr><td>T25</td></tr>
				</table>
				T33
				%s</div>
				</body></html>
				""".formatted(pageWidth, pageHeight, writingMode, before, floatStyle, innerOpen, innerClose);
	}

	/**
	 * 変換して、(1) ページが2枚以上できること、(2) 説明のつかない紙面外への
	 * 配置がないこと、(3) トークンが全部どこかのページに現れること、を
	 * 検査します。この文書の最大明示サイズは{@code 0pt}なので猶予は0です。
	 *
	 * @param name       作業ディレクトリ名
	 * @param html       文書
	 * @param pageWidth  紙面の幅(pt)
	 * @param pageHeight 紙面の高さ(pt)
	 */
	private static void assertNoUnexplainedOffPage(final String name, final String html, final double pageWidth,
			final double pageHeight) throws Exception {
		final File[] pages = convert(name, html);

		// 修正の本体の直接の言明: 収まらない浮動体は改ページを起こす
		assertTrue(name + ": 浮動体が切断されず1ページに収まってしまっている(ページ数=" + pages.length + ")", pages.length >= 2);

		// 紙面をまるごと1枚分はみ出して初めて数える(不変条件6と同じ基準)。
		// この文書の最大明示サイズは0ptなので猶予は0
		double worst = 0;
		String worstAt = null;
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double over = Math.max(Math.max(-x - pageWidth, x - 2 * pageWidth),
						Math.max(-y - pageHeight, y - 2 * pageHeight));
				if (over > worst) {
					worst = over;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
			}
		}
		assertTrue(name + ": 紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(pageWidth) + "x"
				+ Math.round(pageHeight) + "pt, 最大明示サイズ0pt, " + worstAt + ", 全" + pages.length + "ページ)", worst <= 0);

		// 紙面外は「内容を捨てる」ことでも消せる。それが退行として見えるように
		final List<String> lost = new ArrayList<>();
		for (final String t : TOKENS) {
			if (all.indexOf(t) < 0) {
				lost.add(t);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
	}

	/**
	 * {@code clip-path}で切り抜く浮動体は、溢れた中身があっても<b>分けない</b>
	 * (2026-10-02追加)。
	 *
	 * <p>
	 * 溢れた中身は見えないので、紙の外へ置かれても構わない。分けると断片が
	 * 自身の参照ボックス(ページの終わりまで伸びた箱)で切り抜き直すため、
	 * <b>隠れていた中身が見える</b>(修正前は枠の無い形がページの途中で分かれ、
	 * 切り抜きが96pt→148ptに広がっていた)。溢れの測度は
	 * {@code BlockParams.clipsOverflowPaint()}で{@code overflow:hidden}と同じに扱う。
	 * </p>
	 */
	public void testClipPathFloatIsNotSplitByHiddenOverflow() throws Exception {
		final String[] styles = { "float:right;height:96pt;clip-path:inset(0)",
				"float:right;height:96pt;border:1pt solid black;clip-path:inset(0)" };
		for (int i = 0; i < styles.length; ++i) {
			final StringBuilder html = new StringBuilder("""
					<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
					<?jp.cssj.property name="output.page-width" value="400pt"?>
					<?jp.cssj.property name="output.page-height" value="160pt"?>
					<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
					<style>
					@page{margin:0pt}
					body{margin:0;font:normal 10pt/1.2 serif}
					p{margin:0}
					</style></head><body>
					""");
			html.append(i == 0 ? "<p>X0</p>" : "").append("<div style=\"").append(styles[i]).append("\">");
			for (int j = 1; j <= 25; ++j) {
				html.append("<p>P").append(j).append("</p>");
			}
			html.append("</div></body></html>");
			final String name = "clip-path-" + i;
			final File[] pages = convert(name, html.toString());
			assertEquals(name + ": 切り抜く浮動体が分かれた", 1, pages.length);
			final String dump = java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
			final Matcher m = CLIP_IN_DUMP.matcher(dump);
			int clips = 0;
			while (m.find()) {
				++clips;
				assertTrue(name + ": 切り抜きが箱より大きい " + m.group(), Double.parseDouble(m.group(1)) <= 98.01);
			}
			assertTrue(name + ": 切り抜きが無い", clips > 0);
		}
	}

	/** 表示リストの切り抜き({@code clip=[x y w h]})の高さ。 */
	private static final Pattern CLIP_IN_DUMP = Pattern.compile("clip=\\[[-\\d.]+ [-\\d.]+ [-\\d.]+ ([-\\d.]+)\\]");

	/**
	 * 文書を変換して、ページごとの表示リストを返します。
	 *
	 * @param name 作業ディレクトリ名
	 * @param html 文書
	 * @return ページの表示リスト(ページ順)
	 */
	private static File[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/off-page-float/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "off-page-float-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		java.util.Arrays.sort(pages);
		return pages;
	}
}
