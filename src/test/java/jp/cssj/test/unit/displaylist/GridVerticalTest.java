package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
 * 縦組みのグリッドをトラックで組み、頁の境で行ごとに割ることを固定します(2026-10-05、jigensha の報告 3)。
 *
 * <p>
 * それまで縦組みのグリッドはトラック配置の対象外で、item を 1 列に流すだけだった(列も gap も整列も効かない)。
 * 縦組みでは列のトラックが行の向き(縦)に並び、グリッドの行が段の進む向き(vertical-rl は右から左)に重なる。
 * 頁に収まらないグリッドは横組みと同じく行の境で割り、1 頁目から始める。
 * </p>
 */
public class GridVerticalTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern ITEM_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"項目(\\d+)\"");

	public GridVerticalTest(String name) {
		super(name);
	}

	private static String document(final String writingMode, final int items) {
		final StringBuilder body = new StringBuilder();
		for (int i = 1; i <= items; ++i) {
			body.append("<div>項目").append(i).append("</div>");
		}
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page{size:120mm 80mm;margin:6mm}
				html{writing-mode:%s;font:10pt/1.4 serif}
				body{margin:0}
				p{margin:0}
				.g{display:grid;grid-template-columns:1fr 1fr 1fr;gap:2mm}
				</style></head><body>
				<p>前置き</p><div class="g">%s</div><p>後書き</p>
				</body></html>
				""".formatted(writingMode, body);
	}

	public void testTracksInVerticalRl() throws Exception {
		final Map<Integer, double[]> at = items(convert("rl", document("vertical-rl", 6)), 6);
		// 列は行の向き(縦)に並ぶ: 同じ行の 3 つは x が同じで、上から下へ
		assertEquals(at.get(1)[1], at.get(2)[1], 0.01);
		assertEquals(at.get(1)[1], at.get(3)[1], 0.01);
		assertTrue("列は上から下へ: " + Arrays.toString(at.get(1)) + " " + Arrays.toString(at.get(2)),
				at.get(1)[2] < at.get(2)[2] && at.get(2)[2] < at.get(3)[2]);
		// 1fr の 3 列は等しい間隔
		assertEquals(at.get(2)[2] - at.get(1)[2], at.get(3)[2] - at.get(2)[2], 0.01);
		// 次の行は左へ
		assertTrue("vertical-rl の次の行は左", at.get(4)[1] < at.get(1)[1]);
		assertEquals(at.get(1)[2], at.get(4)[2], 0.01);
	}

	public void testTracksInVerticalLr() throws Exception {
		final Map<Integer, double[]> at = items(convert("lr", document("vertical-lr", 6)), 6);
		assertEquals(at.get(1)[1], at.get(2)[1], 0.01);
		assertTrue("列は上から下へ", at.get(1)[2] < at.get(2)[2] && at.get(2)[2] < at.get(3)[2]);
		assertTrue("vertical-lr の次の行は右", at.get(4)[1] > at.get(1)[1]);
	}

	/** 頁に収まらないグリッドは 1 頁目から始まり、行の境で割れる(以前は丸ごと 2 頁目へ送られた)。 */
	public void testSplitsFromFirstPage() throws Exception {
		for (final String mode : new String[] { "vertical-rl", "vertical-lr" }) {
			final String[] pages = convert("split-" + mode, document(mode, 60));
			assertTrue(mode + ": 2 頁以上", pages.length >= 2);
			final Map<Integer, double[]> first = items(new String[] { pages[0] }, 0);
			assertTrue(mode + ": 1 頁目に項目1", first.containsKey(1));
			assertTrue(mode + ": 1 頁目に 30 個以上: " + first.size(), first.size() >= 30);
			// 割れた後の頁でも、同じ行の 3 つは同じ段にそろう
			assertEquals(0, first.size() % 3);
			items(pages, 60);
		}
	}

	/**
	 * 全頁から item の位置を集めます({@code page, x, y})。{@code expected} が正なら 1〜expected がちょうど 1 回ずつ
	 * 現れることも確かめます。
	 */
	private static Map<Integer, double[]> items(final String[] pages, final int expected) {
		final Map<Integer, double[]> at = new HashMap<>();
		for (int p = 0; p < pages.length; ++p) {
			final Matcher m = ITEM_IN_DUMP.matcher(pages[p]);
			while (m.find()) {
				final int n = Integer.parseInt(m.group(3));
				assertNull("項目" + n + "が 2 回", at.put(n, new double[] { p, Double.parseDouble(m.group(1)),
						Double.parseDouble(m.group(2)) }));
			}
		}
		if (expected > 0) {
			for (int n = 1; n <= expected; ++n) {
				assertTrue("項目" + n + "が無い", at.containsKey(n));
			}
			assertEquals(expected, at.size());
		}
		return at;
	}

	/** 変換して、各頁の表示リストを頁順に返します。 */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/grid-vertical/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "grid-vertical-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
