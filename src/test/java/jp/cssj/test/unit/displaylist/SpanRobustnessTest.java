package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 表の{@code colspan}/{@code rowspan}に異常値が与えられても、変換が
 * 停止し例外にならないことを確認します(2026-07-25新設)。
 *
 * <p>
 * 独立レビュー(codex、堅牢性フィクスチャ設計の観点)が静的解析で見つけた
 * P0相当の2件の回帰テストです。負・0・非数値の正規化は以前からありましたが、
 * <b>巨大な正数だけが素通り</b>していました。
 * </p>
 *
 * <ul>
 * <li>{@code colspan="2147483647"} —— {@code IncrementalTableBuilder}が
 * span回数だけ{@code CellContent}を追加するため、約21億回の追加になり
 * 停止前にメモリを使い尽くす</li>
 * <li>{@code rowspan="2147483647"} —— {@code border-collapse: collapse}で
 * {@code CollapsedBorderRules.streamSpacing}の
 * {@code borderRow + rowspan - 1}がintオーバーフローで負値になり、
 * {@code List.get(負値)}で{@code IndexOutOfBoundsException}</li>
 * </ul>
 *
 * <p>
 * 対策は{@code StyleBuilder}でHTML Standardと同じ上限
 * (colspan 1000 / rowspan 65534)へ丸めること。実ブラウザと同じ挙動です。
 * </p>
 *
 * <p>
 * <b>watchdog付き</b>——修正前は「落ちる」のではなく「終わらない」ため、
 * 時間で打ち切らないとテスト自体がハングします。
 * </p>
 */
public class SpanRobustnessTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = 60_000L;

	public SpanRobustnessTest(String name) {
		super(name);
	}

	public void testMaxColspanTerminates() throws Exception {
		convertWithin("max-colspan.html");
	}

	public void testMaxRowspanWithCollapsedBorders() throws Exception {
		convertWithin("max-rowspan.html");
	}

	public void testInvalidSpansAreNormalized() throws Exception {
		convertWithin("invalid-span.html");
	}

	/**
	 * 文書を別スレッドで変換し、{@link #WATCHDOG_MS}以内に例外なく
	 * 終わることを確認します。
	 */
	private static void convertWithin(final String name) throws Exception {
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(name);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "span-robustness-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		if (worker.isAlive()) {
			// 修正前のcolspanケースはここに来る(終わらない)
			fail(name + ": " + WATCHDOG_MS + "ms以内に変換が終わりませんでした");
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わりました", failure[0]);
		}
	}

	private static void convert(final String name) throws Exception {
		final File pdf = new File("local/unittest/span-robustness/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, new File("files/unittest/0490-robustness/" + name), "text/html",
						null);
			} finally {
				session.close();
			}
		}
		assertTrue(name + ": PDFが出力されていません", pdf.length() > 0);
	}
}
