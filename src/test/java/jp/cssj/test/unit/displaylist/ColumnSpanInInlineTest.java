package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
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
 * <b>インラインの中の{@code column-span:all}</b>で変換が落ちないことを
 * 固定します(2026-07-28新設)。
 *
 * <p>
 * WPT(`css/css-multicol`)を不変条件1〜3にかけたときに見つかった
 * クラッシュです({@link WptCorpusTest})。2,409文書中10件が同一原因で
 * {@code IndexOutOfBoundsException: Index -1 out of bounds for length 0}
 * になっていました。20万文書のランダム掃過では<b>一度も出ていません</b>
 * ——生成器が「インラインの中のぶち抜き」を作らないためです。
 * </p>
 *
 * <h2>機序</h2>
 *
 * <p>
 * {@code DocumentBuilder.startBox}のFLOW分岐は、ぶち抜き
 * ({@code column-span:all})のとき<b>{@code startColumnSpan}を先に</b>
 * 呼び、そのあとで{@code closeInlines}していました。
 * {@code startColumnSpan}は段組を抜けるために{@code endFlowBlock}まで
 * 戻すので、その時点で{@code containerBuilder}が差し替わります。
 * すると{@code closeInlines}が出す{@code endInline}は、<b>対応する
 * {@code startInline}を見ていない新しい{@code StyledTextUnitizer}</b>へ
 * 届きます。その{@code InlineParamsStack}は根しか積んでいないため、
 * popが根を外し、{@code current()}が空リストを引いて落ちます。
 * </p>
 *
 * <p>
 * 開いているインラインは<b>ぶち抜き前の文脈で開かれた</b>ものなので、
 * その文脈で閉じなければなりません——{@code closeInlines}を先に、
 * 復元({@code restoreInlines})は{@code endColumnSpan}の後に、と
 * 入れ子を正しました。
 * </p>
 *
 * <p>
 * <b>まだ直っていない場合があります</b>: ぶち抜きがインラインの中の
 * <b>ブロックのさらに中</b>にあると、{@code startColumnSpan}自身が
 * {@code restoreInlines}でインラインを開き直したうえで
 * {@code endFlowBlock}するため、同じ型の不均衡が残ります
 * ({@code multicol-span-all-children-height-010}等2件)。
 * {@code InlineParamsStack.pop}に番人を置くだけでは<b>別のnullへ
 * ずれるだけ</b>で直らないことを確認済みです(`docs/NEXT-SESSION.md`)。
 * </p>
 */
public class ColumnSpanInInlineTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = 60_000L;

	public ColumnSpanInInlineTest(String name) {
		super(name);
	}

	/**
	 * 最小形。{@code <span>}の直下にぶち抜きブロックがある。
	 * WPTの{@code css-multicol/spanner-in-child-after-parallel-flow-003}
	 * 等がこの形。
	 */
	private static final String SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>
			<div style="column-span:all; height:10px; background:green"></div>
			</span>
			</div>
			</body></html>
			""";

	/**
	 * ぶち抜きの前にインラインの内容がある形
	 * ({@code css-multicol/multicol-span-all-019}の骨格)。
	 * インラインが実際に文字を持っていると、{@code endInline}が
	 * グリフパイプラインを通るため経路が変わる。
	 */
	private static final String SPANNER_IN_INLINE_WITH_TEXT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>body{font:normal 10pt/1.2 serif}</style>
			</head><body>
			<div style="columns:2; width:100px; orphans:1; widows:1">
			<div style="height:15px">
			<span>ABC DEF
			<div style="column-span:all; height:20px; background:green"></div>
			</span>
			</div>
			<div style="height:40px"></div>
			</div>
			</body></html>
			""";

	/** ぶち抜きが置換要素の場合({@code addReplacedBox}側の同じ順序)。 */
	private static final String REPLACED_SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>XY
			<img src="@IMG@" style="column-span:all; display:block; width:20pt" />
			</span>
			</div>
			</body></html>
			""";

	public void testSpannerDirectlyInsideInline() throws Exception {
		convertWithin("spanner-in-inline", SPANNER_IN_INLINE);
	}

	public void testSpannerInsideInlineWithText() throws Exception {
		convertWithin("spanner-in-inline-text", SPANNER_IN_INLINE_WITH_TEXT);
	}

	public void testReplacedSpannerInsideInline() throws Exception {
		final File png = new File("files/unittest/red.png");
		assertTrue("テスト画像が見つからない: " + png.getAbsolutePath(), png.isFile());
		convertWithin("replaced-spanner-in-inline",
				REPLACED_SPANNER_IN_INLINE.replace("@IMG@", png.toURI().toString()));
	}

	/**
	 * 文書を別スレッドで変換し、{@link #WATCHDOG_MS}以内に例外なく
	 * 終わることを確認します({@code SpanRobustnessTest}と同じ形)。
	 */
	private static void convertWithin(final String name, final String html) throws Exception {
		final File dir = new File("local/unittest/column-span-in-inline");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(input, new File(dir, name + ".pdf"));
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "column-span-in-inline-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		if (worker.isAlive()) {
			fail(name + ": " + WATCHDOG_MS + "ms以内に変換が終わりませんでした");
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わりました", failure[0]);
		}
	}

	private static void convert(final File input, final File pdf) throws Exception {
		try (OutputStream out = new FileOutputStream(pdf)) {
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
		}
	}
}
