package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.test.unit.TextWrapStyleOptIn;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 行分割戦略(貪欲法/K-P)の性能比較ハーネスです
 * (E-2。CIの常時実行対象ではなく、性能作業時に手動で回す:
 * {@code ./gradlew test --tests "*.LineBreakerPerfHarness"})。
 *
 * <p>
 * 和文主体(全文字がbreakpoint候補=K-P負荷最大)と欧文主体(空白のみ
 * 候補)の2種の合成文書を生成し、ウォームアップ後の中央値で比較する。
 * 目的は回帰判定ではなく「K-P既定化の可否判断」のための実測。
 * K-Pのオプトインは著者スタイルシート
 * ({@code html { text-wrap-style: pretty }})で与える。
 * </p>
 */
public class LineBreakerPerfHarness extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final int WARMUP = 2;

	private static final int RUNS = 5;

	public LineBreakerPerfHarness(String name) {
		super(name);
	}

	public void testComparePerf() throws Exception {
		if (System.getProperty("foliojet.perf") == null) {
			// 常時実行のCIでは回さない(性能作業時のみ:
			// ./gradlew test --tests "*.LineBreakerPerfHarness" -Dfoliojet.perf= )
			System.out.println("LineBreakerPerfHarness: skipped (-Dfoliojet.perf not set)");
			return;
		}
		final File dir = new File("local/unittest/line-breaker-perf");
		dir.mkdirs();

		final File cjk = new File(dir, "perf-cjk.html");
		Files.writeString(cjk.toPath(), generateCjk(400), StandardCharsets.UTF_8);
		final File latin = new File(dir, "perf-latin.html");
		Files.writeString(latin.toPath(), generateLatin(400), StandardCharsets.UTF_8);

		for (final File doc : new File[] { cjk, latin }) {
			final long legacy = median(doc, false);
			final long optimized = median(doc, true);
			System.out.printf("PERF %s: legacy=%dms optimized=%dms ratio=%.2f%n", doc.getName(), legacy, optimized,
					(double) optimized / legacy);
		}
	}

	private long median(final File doc, final boolean pretty) throws Exception {
		final long[] times = new long[RUNS];
		for (int i = 0; i < WARMUP + RUNS; ++i) {
			final long t0 = System.nanoTime();
			this.transcode(doc, pretty);
			final long ms = (System.nanoTime() - t0) / 1_000_000;
			if (i >= WARMUP) {
				times[i - WARMUP] = ms;
			}
		}
		java.util.Arrays.sort(times);
		return times[RUNS / 2];
	}

	private void transcode(final File doc, final boolean pretty) throws Exception {
		final File pdf = new File("local/unittest/line-breaker-perf/out.pdf");
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				if (pretty) {
					session.property("input.default-stylesheet", TextWrapStyleOptIn.PRETTY_STYLESHEET);
				}
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static String generateCjk(final int paragraphs) {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "body{font-size:10pt;line-height:1.6;text-align:justify;}p{margin:0 0 6pt 0;}"
				+ "</style></head><body>");
		final String[] sentences = { //
				"吾輩は猫である。名前はまだ無い。どこで生れたかとんと見当がつかぬ。", //
				"何でも薄暗いじめじめした所でニャーニャー泣いていた事だけは記憶している。", //
				"吾輩はここで始めて人間というものを見た。しかもあとで聞くとそれは書生という人間中で一番獰悪な種族であったそうだ。", //
				"この書生というのは時々我々を捕えて煮て食うという話である。しかしその当時は何という考もなかったから別段恐しいとも思わなかった。", //
		};
		for (int i = 0; i < paragraphs; ++i) {
			sb.append("<p>");
			for (int j = 0; j < 4; ++j) {
				sb.append(sentences[(i + j) % sentences.length]);
			}
			sb.append("</p>");
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	private static String generateLatin(final int paragraphs) {
		final StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "body{font-size:10pt;line-height:1.6;text-align:justify;}p{margin:0 0 6pt 0;}"
				+ "</style></head><body>");
		final String[] sentences = { //
				"The quick brown fox jumps over the lazy dog while the sun sets slowly behind distant mountains. ", //
				"Typography is the art and technique of arranging type to make written language legible, readable and appealing. ", //
				"In the beginning the universe was created; this has made a lot of people very angry and been widely regarded as a bad move. ", //
				"Paragraph composition with total-fit line breaking minimizes the sum of squared badness over all lines of the paragraph. ", //
		};
		for (int i = 0; i < paragraphs; ++i) {
			sb.append("<p>");
			for (int j = 0; j < 4; ++j) {
				sb.append(sentences[(i + j) % sentences.length]);
			}
			sb.append("</p>");
		}
		sb.append("</body></html>");
		return sb.toString();
	}
}
