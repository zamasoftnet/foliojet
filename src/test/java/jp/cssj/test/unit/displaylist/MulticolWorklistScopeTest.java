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
import java.util.List;

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
 * MULTICOL native worklist化(legacy再帰撤去=増分1、2026-07-30)の
 * 等価性証明です。routingは未変更のため本番ではまだこの経路に入らない
 * ——このテストが{@code FlowContainer.pushWorklistOverride()}で強制的に
 * worklist driverへ流し、次の3点を固定する:
 *
 * <ol>
 * <li><b>バイト等価</b>: legacy駆動とworklist駆動で全ページの
 * display-list dumpが一致する。順序特性(startFlowBlock順・旧段index
 * 昇順・開いた尾は最終段のみ)の証明はこのバイト一致に包含される</li>
 * <li><b>非空振り</b>: 強制worklist駆動で
 * {@code MULTICOL_NATIVE_DESCENTS > 0}(テストが実際にnative降下を
 * 通った)かつ{@code WORKLIST_RECURSIVE_FALLBACKS == 0}(同期再帰へ
 * 逃げていない)</li>
 * <li><b>リークなし</b>: 各変換後に尾部封印(tailSeal)もworklist
 * overrideもThreadLocalへ残らない</li>
 * </ol>
 *
 * <p>
 * 増分2(WorklistTailGateのMULTICOL許可)後は本番経路がここを通る。
 * codex相談: docs/consultations/
 * consult-codex-2026-07-30-multicol-descent-proof.txt §5 増分1。
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

	/** 入れ子段組(3段中2段)——MOVE_SENTINEL型。実測legacy=3。 */
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

	/** 縦書き・二重の段組・{@code <ol>}——SPLIT_FRAGMENT_REPLAY型。実測legacy=2。 */
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

	public void testNestedMulticolEquivalence() throws Exception {
		// この文書は「開いたチェーンがMULTICOL境界を貫通する」形
		// (native降下の非空振り証明を担う)
		assertLegacyWorklistEquivalence("nested-multicol", null, NESTED_MULTICOL, true);
	}

	public void testTripleMulticolEquivalence() throws Exception {
		assertLegacyWorklistEquivalence("triple-multicol", null, TRIPLE_MULTICOL, true);
	}

	public void testVerticalNestedEquivalence() throws Exception {
		// 二重段組だが開いたチェーン自体は内側段組の中のplainなboxを
		// 通る(実測native=0)——MULTICOL境界はitemの通常replayで組まれる
		assertLegacyWorklistEquivalence("vertical-nested", null, VERTICAL_NESTED, false);
	}

	public void testColumnsFloatEquivalence() throws Exception {
		assertLegacyWorklistEquivalence("columns-float",
				new File("files/unittest/0400-column-count/columns-float.html"), null, false);
	}

	public void testPageFirstEquivalence() throws Exception {
		assertLegacyWorklistEquivalence("page-first",
				new File("files/unittest/0400-column-count/page-first.html"), null, false);
	}

	/**
	 * 1文書をlegacy駆動と強制worklist駆動で変換し、dumpバイト一致・
	 * カウンタ・リークなしを検査します。
	 *
	 * @param name               出力ディレクトリ名
	 * @param source             入力ファイル({@code html}と排他)
	 * @param html               インライン文書({@code source}と排他)
	 * @param expectNativeDescent 開いたチェーンがMULTICOL境界を貫通し、
	 *                            native降下が発火するはずの文書ならtrue。
	 *                            falseの文書はlegacy発火の原因がplainな
	 *                            チェーン降下だけ(2026-07-30実測)で、
	 *                            worklist駆動ではframe pushで完結する
	 */
	private static void assertLegacyWorklistEquivalence(final String name, final File source, final String html,
			final boolean expectNativeDescent) throws Exception {
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

		final List<String> legacyDump = transcodeAndDump(name + "/legacy", input, false);
		final long legacyDescents = ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get();
		assertTrue(name + ": legacy駆動でlegacy再帰が観測されていません(fixtureが弱体化)", legacyDescents > 0);
		assertEquals(name + ": legacy駆動でnative降下が発火(routingが変わっている)", 0,
				ContinuationStats.MULTICOL_NATIVE_DESCENTS.get());

		final List<String> worklistDump = transcodeAndDump(name + "/worklist", input, true);
		final long worklistLegacy = ContinuationStats.LEGACY_RECURSIVE_DESCENTS.get();
		final long worklistFallbacks = ContinuationStats.WORKLIST_RECURSIVE_FALLBACKS.get();
		final long nativeDescents = ContinuationStats.MULTICOL_NATIVE_DESCENTS.get();
		System.out.println("[EQ] " + name + " legacyRun.legacy=" + legacyDescents + " worklistRun.native="
				+ nativeDescents + " worklistRun.fallback=" + worklistFallbacks);

		// 等価性(本丸)を先に検査する——カウンタの期待が外れた場合も
		// 「出力は合っているのか」が失敗メッセージから分かるように
		assertEquals(name + ": ページ数が不一致", legacyDump.size(), worklistDump.size());
		for (int i = 0; i < legacyDump.size(); ++i) {
			assertEquals(name + ": ページ" + (i + 1) + "のdisplay-listが不一致", legacyDump.get(i),
					worklistDump.get(i));
		}

		assertEquals(name + ": 強制worklist駆動でlegacy再帰が発火", 0, worklistLegacy);
		assertEquals(name + ": worklist駆動が同期再帰フォールバックへ逃げました", 0, worklistFallbacks);
		if (expectNativeDescent) {
			assertTrue(name + ": native降下が空振り(MULTICOL経路を通っていない)", nativeDescents > 0);
		}
	}

	/** 変換してページごとのdisplay-list dumpを返します。カウンタは変換直前にresetします。 */
	private static List<String> transcodeAndDump(final String name, final File input, final boolean forceWorklist)
			throws Exception {
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
		// NestedMulticolDuplicationTestと同じ64MBスタックのworker(深い
		// 入れ子でも本テストの関心事(driver等価)以外で落とさない)
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				if (forceWorklist) {
					FlowContainer.pushWorklistOverride();
				}
				try {
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
				} finally {
					if (forceWorklist) {
						FlowContainer.popWorklistOverride();
					}
					if (FlowContainer.hasOpenTailSeal()) {
						throw new AssertionError(name + ": 尾部封印がリークしています");
					}
					if (!forceWorklist && FlowContainer.hasWorklistOverride()) {
						throw new AssertionError(name + ": worklist overrideがリークしています");
					}
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
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		java.util.Arrays.sort(pages);
		final List<String> dumps = new ArrayList<>();
		for (final File page : pages) {
			dumps.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return dumps;
	}
}
