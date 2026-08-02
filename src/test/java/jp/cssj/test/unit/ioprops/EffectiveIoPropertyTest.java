package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 入出力プロパティが<b>本当に効いているか</b>を差分で検査します
 * (2026-08-02新設)。
 *
 * <p>
 * {@code PdfIoPropertyTest}の一部は「設定しても変換が壊れない」ことしか
 * 見ておらず、<b>プロパティが丸ごと無視されていても通ってしまう</b>
 * ——実際にそういう欠陥(HTTPのUser-Agentが送られない)が長く残っていた。
 * ここでは<b>同じ文書を2通りの設定で変換し、出力が異なること</b>を見る。
 * 値がどこにどう出るかを知らなくても、配線の有無は捕まえられる。
 * </p>
 *
 * <p>
 * 差分が出ない場合は「そのプロパティがこの文書に効かない」可能性もある
 * ので、<b>効くはずの文書を選ぶ</b>ことがこの検査の設計の要になる。
 * </p>
 */
public class EffectiveIoPropertyTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File TEXT = new File("files/unittest/ioprops/two-pages.html");

	private static final File WITH_IMAGE = new File("files/unittest/ioprops/link-and-image.html");

	/** 1件の差分検査。 */
	private record Case(String name, File document, Map<String, String> a, Map<String, String> b) {
	}

	private final List<String> licenseBlocked = new ArrayList<>();

	private static List<Case> cases() {
		final List<Case> cases = new ArrayList<>();
		final String watermark = new File("files/unittest/red.png").toURI().toString();

		cases.add(new Case("output.default-font-family", TEXT,
				props("output.default-font-family", "serif"), props("output.default-font-family", "monospace")));
		cases.add(new Case("output.color", TEXT, props("output.color", "rgb"), props("output.color", "cmyk")));
		cases.add(new Case("output.resolution", WITH_IMAGE,
				props("output.resolution", "96"), props("output.resolution", "192")));
		cases.add(new Case("output.text-size", TEXT,
				props("output.text-size", "1"), props("output.text-size", "2")));
		cases.add(new Case("output.page-margins", TEXT,
				props("output.page-margins", "0pt"), props("output.page-margins", "50pt")));
		cases.add(new Case("output.n-up.order", TEXT,
				props("output.n-up", "2", "output.n-up.order", "horizontal"),
				props("output.n-up", "2", "output.n-up.order", "vertical")));
		cases.add(new Case("output.marks.spine-width", TEXT,
				props("output.marks", "crop", "output.marks.spine-width", "0pt"),
				props("output.marks", "crop", "output.marks.spine-width", "20pt")));
		cases.add(new Case("output.print-mode", TEXT,
				props("output.print-mode", "double-side"), props("output.print-mode", "left-side")));
		cases.add(new Case("output.pdf.watermark.mode", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "center"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.mode", "tile")));
		cases.add(new Case("output.pdf.watermark.opacity", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "1"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.opacity", "0.3")));
		cases.add(new Case("output.pdf.watermark.print", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "true"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.print", "false")));
		cases.add(new Case("output.pdf.watermark.view", TEXT,
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "true"),
				props("output.pdf.watermark.uri", watermark, "output.pdf.watermark.view", "false")));
		cases.add(new Case("output.pdf.image.max-width", WITH_IMAGE,
				props("output.pdf.image.max-width", "1000"), props("output.pdf.image.max-width", "8")));
		cases.add(new Case("output.pdf.image.max-height", WITH_IMAGE,
				props("output.pdf.image.max-height", "1000"), props("output.pdf.image.max-height", "8")));
		cases.add(new Case("output.pdf.jpeg-image", WITH_IMAGE,
				props("output.pdf.jpeg-image", "false"), props("output.pdf.jpeg-image", "true")));
		cases.add(new Case("output.broken-image", WITH_IMAGE,
				props("input.exclude", "**/red.png", "input.include", "**", "output.broken-image", "hidden"),
				props("input.exclude", "**/red.png", "input.include", "**", "output.broken-image", "cross")));
		return cases;
	}

	public void testPropertiesChangeTheOutput() throws Exception {
		final List<String> noEffect = new ArrayList<>();
		int skipped = 0;
		for (final Case c : cases()) {
			final String a = this.convert(c.document(), c.a());
			if (!this.licenseBlocked.isEmpty()) {
				++skipped;
				continue;
			}
			final String b = this.convert(c.document(), c.b());
			if (a.equals(b)) {
				noEffect.add(c.name());
			}
		}
		if (!noEffect.isEmpty()) {
			fail("値を変えても出力が変わらない(配線されていない疑い)" + noEffect.size() + "件: "
					+ String.join(" / ", noEffect));
		}
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private String convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		this.licenseBlocked.clear();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
					if (code == net.zamasoft.foliojet.message.MessageCodes.WARN_LICENSE_CONSTRAINT_IO) {
						this.licenseBlocked.add(args != null && args.length > 0 ? args[0] : "?");
					}
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				if (!properties.containsKey("input.include") && !properties.containsKey("input.exclude")) {
					session.property("input.include", "**");
				}
				session.property("output.pdf.compression", "none");
				// 生成時刻とファイルIDを固定しないと、同じ設定でも出力が
				// 変わってしまい差分検査が意味を失う
				session.property("output.pdf.meta.creation-date", "2020-01-02 03:04:05");
				session.property("output.pdf.meta.mod-date", "2020-01-02 03:04:05");
				session.property("output.pdf.file-id", "0123456789abcdef0123456789abcdef");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
		return new String(Files.readAllBytes(out.toPath()), StandardCharsets.ISO_8859_1);
	}
}
