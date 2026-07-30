package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * MULTICOL native worklist降下(legacy再帰撤去=増分1〜4、2026-07-30)の
 * 本番routing回帰ガードです。各fixtureを本番routingで1回変換し、次を
 * 固定する:
 *
 * <ol>
 * <li><b>非空振り</b>: MULTICOL境界を貫通する文書で
 * {@code MULTICOL_NATIVE_DESCENTS > 0}(native scope降下が実際に
 * 通った)</li>
 * <li><b>フォールバック0</b>: 全文書で
 * {@code WORKLIST_COMPAT_FALLBACKS == 0}(未知コンテナの互換
 * フォールバックへ逃げていない)</li>
 * <li><b>内容保存</b>: インライン文書の期待トークン(T2等)が全ページを
 * 通して<b>ちょうど1回</b>描かれる——消失も複製もない
 * ({@code NestedMulticolDuplicationTest}と同型の検査)</li>
 * <li><b>リークなし</b>: 変換後に尾部封印(tailSeal)がThreadLocalへ
 * 残らない</li>
 * </ol>
 *
 * <p>
 * <b>歴史的経緯</b>: 増分1(routing不変)の時点では「legacy再帰駆動と
 * 強制worklist駆動のdisplay-listバイト等価」の証明だった(foliojet4
 * 67c2414)。増分2でgateが切り替わり、増分4でoverride機構ごと旧driver
 * が物理撤去されたため、driver比較は不可能かつ不要となり、本番routing
 * の回帰ガードへ再定義した(同一実装を2回走らせる比較は決定的な順序
 * 退行を検出できない——display-listの固定はtier1のgolden群が担う)。
 * codex相談: docs/consultations/
 * consult-codex-2026-07-30-increment4-removal-spec.txt §5。
 * </p>
 */
public class MulticolWorklistScopeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 打ち切り時間。実測は1件あたり数秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	/** display-listのテキスト描画行から中身を取り出す。 */
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** 入れ子段組(3段中2段)——MOVE_SENTINEL型。チェーンがMULTICOL境界を貫通する。 */
	private static final String NESTED_MULTICOL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 9pt/1.2 serif}
			</style></head><body>
			<div style="column-count:3">
			T2
			<div style="column-count:2">
			T4
			<p></p>
			T6
			</div>
			</div>
			</body></html>
			""";

	/** 3重の段組——入れ子のMulticolRestyleScope(scope中のscope)の検証。 */
	private static final String TRIPLE_MULTICOL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{font:normal 9pt/1.2 serif}
			</style></head><body>
			<div style="column-count:3">
			T2
			<div style="column-count:2">
			T4
			<div style="column-count:2">
			T5
			<p></p>
			T6
			</div>
			</div>
			</div>
			</body></html>
			""";

	/** 縦書き・二重の段組・{@code <ol>}——SPLIT_FRAGMENT_REPLAY型。 */
	private static final String VERTICAL_NESTED = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="120pt"?>
			<?jp.cssj.property name="output.page-height" value="400pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 7pt/1.2 serif;writing-mode:vertical-lr}
			</style></head><body>
			<div style="column-count:2">
			<div style="column-count:2">
			<span style="display:inline-block;width:42pt"></span>
			<ol>
			T24
			<li>T25</li>
			</ol>
			</div>
			</div>
			</body></html>
			""";

	public void testNestedMulticolNativeDescent() throws Exception {
		assertProductionRouting("nested-multicol", null, NESTED_MULTICOL, true, "T2", "T4", "T6");
	}

	public void testTripleMulticolNativeDescent() throws Exception {
		assertProductionRouting("triple-multicol", null, TRIPLE_MULTICOL, true, "T2", "T4", "T5", "T6");
	}

	public void testVerticalNestedNoFallback() throws Exception {
		assertProductionRouting("vertical-nested", null, VERTICAL_NESTED, false, "T24", "T25");
	}

	public void testColumnsFloatNoFallback() throws Exception {
		assertProductionRouting("columns-float", new File("files/unittest/0400-column-count/columns-float.html"), null,
				false);
	}

	public void testPageFirstNoFallback() throws Exception {
		assertProductionRouting("page-first", new File("files/unittest/0400-column-count/page-first.html"), null,
				false);
	}

	/**
	 * 1文書を本番routingで変換し、カウンタ・トークン保存・リークなしを
	 * 検査します。
	 *
	 * @param name                出力ディレクトリ名
	 * @param source              入力ファイル({@code html}と排他)
	 * @param html                インライン文書({@code source}と排他)
	 * @param expectNativeDescent 開いたチェーンがMULTICOL境界を貫通し、
	 *                            native降下が発火するはずの文書ならtrue
	 * @param expectedTokens      全ページを通してちょうど1回描かれるべき
	 *                            トークン(インライン文書のみ)
	 */
	private static void assertProductionRouting(final String name, final File source, final String html,
			final boolean expectNativeDescent, final String... expectedTokens) throws Exception {
		final File input;
		if (source != null) {
			input = source;
		} else {
			input = new File("local/unittest/multicol-worklist/" + name + "/input.html");
			input.getParentFile().mkdirs();
			try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
				w.write(html);
			}
		}

		final List<String> dumps = transcodeAndDump(name + "/production", input);
		assertTrue(name + ": ページが出ていません", !dumps.isEmpty());

		assertEquals(name + ": worklist駆動が互換フォールバックへ逃げました", 0,
				ContinuationStats.WORKLIST_COMPAT_FALLBACKS.get());
		if (expectNativeDescent) {
			assertTrue(name + ": native降下が空振り(MULTICOL経路を通っていない)",
					ContinuationStats.MULTICOL_NATIVE_DESCENTS.get() > 0);
		}

		if (expectedTokens.length > 0) {
			final Map<String, Integer> counts = new LinkedHashMap<>();
			for (final String dump : dumps) {
				final Matcher m = TEXT.matcher(dump);
				while (m.find()) {
					for (final String word : m.group(1).trim().split("\\s+")) {
						counts.merge(word, 1, Integer::sum);
					}
				}
			}
			for (final String token : expectedTokens) {
				final Integer n = counts.get(token);
				assertNotNull(name + ": トークン" + token + "が消失", n);
				assertEquals(name + ": トークン" + token + "が複製", 1, n.intValue());
			}
		}
	}

	/** 本番routingで変換してページごとのdisplay-list dumpを返します。カウンタは変換直前にresetします。 */
	private static List<String> transcodeAndDump(final String name, final File input) throws Exception {
		final File dir = new File("local/unittest/multicol-worklist/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles((d, n) -> n.endsWith(".txt"));
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		ContinuationStats.reset();
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
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
				if (FlowContainer.hasOpenTailSeal()) {
					throw new AssertionError(name + ": 尾部封印がリークしています");
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-worklist-" + name.replace('/', '-'), 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}

		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		java.util.Arrays.sort(pages);
		final List<String> dumps = new ArrayList<>();
		for (final File page : pages) {
			dumps.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return dumps;
	}
}
