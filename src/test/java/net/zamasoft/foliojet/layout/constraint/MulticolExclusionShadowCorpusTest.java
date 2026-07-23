package net.zamasoft.foliojet.layout.constraint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 排除域のConstraintSpace入力化P0 Step3(2026-07-23、
 * {@code BlockBuilder.computeMulticolBand}/{@code
 * shadowCompareMulticolBand})が、{@code files/unittest/}コーパス全体
 * (multicol+float文書を含む)に対して既存ループと{@link ExclusionSpace}
 * queryの結果が一致し続けることを固定する回帰テストです。
 *
 * <p>
 * {@code files/unittest/0400-column-count/columns-float.html}
 * ({@code ColumnsFloatTest}が使う既存フィクスチャ)を含む、multicolかつ
 * floatを持つ文書がこのコーパスに実在することを確認したうえで、
 * {@link ExclusionShadowStats#MULTICOL_MISMATCHES}が0のままであることを
 * 断言する。
 * </p>
 */
public class MulticolExclusionShadowCorpusTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public MulticolExclusionShadowCorpusTest(String name) {
		super(name);
	}

	public void testShadowParityAcrossCorpus() throws Exception {
		final Path root = Path.of("files/unittest");
		final List<Path> docs = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(p -> {
				final String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".html") || n.endsWith(".htm");
			}).forEach(docs::add);
		}

		ExclusionShadowStats.reset();
		final File scratch = File.createTempFile("multicol-exclusion-shadow", ".pdf");
		scratch.deleteOnExit();
		int ok = 0, failed = 0;
		for (final Path doc : docs) {
			try {
				this.transcode(doc.toFile(), scratch);
				++ok;
			} catch (final Exception e) {
				++failed;
			}
		}
		scratch.delete();

		System.out.println("documents: total=" + docs.size() + " ok=" + ok + " failed=" + failed);
		System.out.println("MULTICOL_SESSIONS=" + ExclusionShadowStats.MULTICOL_SESSIONS.get());
		System.out.println("MULTICOL_MATCHES=" + ExclusionShadowStats.MULTICOL_MATCHES.get());
		System.out.println("MULTICOL_MISMATCHES=" + ExclusionShadowStats.MULTICOL_MISMATCHES.get());

		// このコーパスにはmulticol+floatの組み合わせを実際に踏む文書
		// (columns-float.html等)が含まれるはず——0件ならテスト自体が
		// 何も検証していないことになるため、それも失敗として検出する。
		assertTrue("expected at least one multicol exclusion shadow session in the corpus",
				ExclusionShadowStats.MULTICOL_SESSIONS.get() > 0);
		assertEquals(0, ExclusionShadowStats.MULTICOL_MISMATCHES.get());
	}

	private void transcode(final File source, final File out) throws Exception {
		try (OutputStream os = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(os)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
