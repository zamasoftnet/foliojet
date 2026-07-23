package net.zamasoft.foliojet.layout.fragment;

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
 * B6a0のplain-flow tail shadowを、foliojet4自身の{@code files/unittest/}
 * コーパス(2026-07-22時点414文書、591文書の視覚コーパスより遥かに広く
 * edge caseフィクスチャが豊富)に対して走らせ、{@link
 * PlainFlowTailTrace.TerminalKind}の内訳を実測する一回限りの診断ツール
 * です(2026-07-22新設)。
 *
 * <p>
 * `docs/history/2026-07-22-b6a0-model-refinement-100pct-consistency.md`
 * の続報で、`BOUND_AS_WRITING_MODE_MISMATCH_BLOCK`/`BOUND_AS_REPLACED`
 * の2経路がどのコーパスでも未実証と判明した——この広いコーパスで自然
 * 発生するか確認する。アサーションは持たない(探索的)。
 * </p>
 */
public class TailShadowUnitCorpusAuditTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testCorpusAudit() throws Exception {
		final Path root = Path.of("files/unittest");
		final List<Path> docs = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(p -> {
				final String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".html") || n.endsWith(".htm");
			}).forEach(docs::add);
		}

		ContinuationStats.reset();
		int ok = 0;
		int failed = 0;
		final File scratch = File.createTempFile("tailshadow-unit-audit", ".pdf");
		scratch.deleteOnExit();
		int documentsWithSessions = 0;
		for (final Path doc : docs) {
			final long sessionsBefore = ContinuationStats.TAIL_SHADOW_SESSIONS.get();
			final long inconsistentBefore = ContinuationStats.TAIL_SHADOW_INCONSISTENT.get();
			final long ineligibleBefore = ContinuationStats.WORKLIST_INELIGIBLE_TERMINALS.get();
			try {
				this.transcode(doc.toFile(), scratch);
				++ok;
			} catch (final Exception e) {
				++failed;
			}
			if (ContinuationStats.TAIL_SHADOW_SESSIONS.get() != sessionsBefore) {
				++documentsWithSessions;
				System.out.println("TAIL_SHADOW session in: " + doc);
			}
			if (ContinuationStats.TAIL_SHADOW_INCONSISTENT.get() != inconsistentBefore) {
				System.out.println("TAIL_SHADOW_INCONSISTENT caused by: " + doc);
			}
			if (ContinuationStats.WORKLIST_INELIGIBLE_TERMINALS.get() != ineligibleBefore) {
				System.out.println("WORKLIST_INELIGIBLE_TERMINALS caused by: " + doc);
			}
		}
		scratch.delete();

		System.out.println("documents: total=" + docs.size() + " ok=" + ok + " failed=" + failed);
		System.out.println("documentsWithTailShadowSessions=" + documentsWithSessions);
		System.out.println("TAIL_SHADOW_SESSIONS=" + ContinuationStats.TAIL_SHADOW_SESSIONS.get());
		System.out.println("TAIL_SHADOW_CONSISTENT=" + ContinuationStats.TAIL_SHADOW_CONSISTENT.get());
		System.out.println("TAIL_SHADOW_INCONSISTENT=" + ContinuationStats.TAIL_SHADOW_INCONSISTENT.get());
		System.out.println("TAIL_SHADOW_ALL_PLAIN_FLOW_INCONSISTENT="
				+ ContinuationStats.TAIL_SHADOW_ALL_PLAIN_FLOW_INCONSISTENT.get());
		for (final PlainFlowTailTrace.TerminalKind kind : PlainFlowTailTrace.TerminalKind.values()) {
			System.out
					.println("TAIL_SHADOW_TERMINAL[" + kind + "]=" + ContinuationStats.tailShadowTerminalKindCount(kind));
		}
		System.out.println("RESTYLE_CHAIN_FIRINGS=" + ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		System.out.println("OPEN_CHAIN_TRAILING_ITEMS=" + ContinuationStats.OPEN_CHAIN_TRAILING_ITEMS.get());
		System.out.println("WORKLIST_ELIGIBLE_TERMINALS=" + ContinuationStats.WORKLIST_ELIGIBLE_TERMINALS.get());
		System.out.println("WORKLIST_INELIGIBLE_TERMINALS=" + ContinuationStats.WORKLIST_INELIGIBLE_TERMINALS.get());
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
