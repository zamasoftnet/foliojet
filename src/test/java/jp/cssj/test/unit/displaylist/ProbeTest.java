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
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 手元のエンジンで任意の文書を組み、PDF と頁ごとの表示リストを出す道具です(2026-10-05)。調べものの度に
 * 使い捨ての試験を書いていたのをやめる。{@code -Dfoliojet.probe} が無ければ何もしない(通常の試験では空振り)。
 *
 * <pre>
 * wsl -e bash dev/tools/wsl/probe.sh 名前=/mnt/f/…/a.html [名前=…] [-- 入出力プロパティ=値 …]
 * </pre>
 *
 * <p>
 * {@code foliojet.probe} は「名前=パス」を {@code |} で区切ったもの、{@code foliojet.probeProps} は
 * 「プロパティ=値」を {@code |} で区切ったもの。入力の種類は拡張子で決める(.epub・.html・.htm は
 * それぞれ EPUB・HTML、ほかは XHTML)。出力は {@code local/probes/名前/}(out.pdf と page-NNNN.txt)。
 * 頁寸法の処理命令を効かせるため {@code input.property-pi=true} と {@code input.include=**} を既定で付ける。
 * </p>
 */
public class ProbeTest extends TestCase {
	public ProbeTest(String name) {
		super(name);
	}

	public void testProbe() throws Exception {
		final String spec = System.getProperty("foliojet.probe");
		if (spec == null || spec.isBlank()) {
			return;
		}
		final String props = System.getProperty("foliojet.probeProps", "");
		for (final String item : spec.split("\\|")) {
			final int eq = item.indexOf('=');
			final String name = item.substring(0, eq).trim();
			final File input = new File(item.substring(eq + 1).trim());
			final File dir = new File("local/probes/" + name);
			dir.mkdirs();
			final File[] old = dir.listFiles();
			if (old != null) {
				for (final File f : old) {
					f.delete();
				}
			}
			final String lower = input.getName().toLowerCase();
			final String mime = lower.endsWith(".epub") ? "application/epub+zip"
					: lower.endsWith(".html") || lower.endsWith(".htm") ? "text/html" : "application/xhtml+xml";
			final long started = System.nanoTime();
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
						null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					for (final String p : props.split("\\|")) {
						final int peq = p.indexOf('=');
						if (peq > 0) {
							session.property(p.substring(0, peq).trim(), p.substring(peq + 1).trim());
						}
					}
					CTISessionHelper.transcodeFile(session, input, mime, null);
				} finally {
					session.close();
				}
			}
			final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
			System.out.println("[probe] " + name + " pages=" + (pages == null ? 0 : pages.length) + " "
					+ (System.nanoTime() - started) / 1_000_000 + "ms -> " + dir.getPath());
		}
	}
}
