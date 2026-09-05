package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;

import junit.framework.TestCase;

/** runningの存在が表示リストとタグ構造に影響しないことを実変換で確認します。 */
public final class RunningTokenInvarianceTest extends TestCase {
	public void testTokenInParagraph() throws Exception {
		this.invariant("<p>Alpha beta gamma TOKEN delta epsilon zeta eta theta iota kappa lambda.</p>");
	}

	public void testTokenAtBlockStart() throws Exception {
		this.invariant("<section>TOKEN<p>Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda.</p></section>");
	}

	public void testTokenAfterPreservedWhitespaceAndAutomaticPageBreaks() throws Exception {
		this.invariant("<p style='white-space:pre'>A" + "\n".repeat(40) + "   TOKENB</p>");
	}

	public void testTokenInTableAndVerticalJustifiedText() throws Exception {
		this.invariant("<table><tr><td><p style='writing-mode:vertical-rl;text-align:justify;text-indent:10pt'>"
				+ "Alpha beta gamma TOKEN delta epsilon zeta eta theta.</p></td></tr></table>");
	}

	public void testFootnoteRunningIsExplicitlyIgnored() throws Exception {
		final String css = ".note{float:footnote}"
				+ ".note::footnote-call{content:'CALL'} .note::footnote-marker{content:'MARK'}";
		final String body = "<p>BODY<span class='note'>NOTE</span>TAIL</p>";
		final var without = RunningCaptureTest.convert(css, body, false, true, true);
		final var with = RunningCaptureTest.convert(css + ".note::footnote-call{position:running(call)}"
				+ ".note::footnote-marker{position:running(marker)}", body, false, true, true);
		assertEquals(without.pages(), with.pages());
		assertEquals(0L, with.context().getRunningRegistry().assignedCount());
		assertTrue(with.messages().toString(), with.messages().stream().anyMatch(
				message -> message.contains("running() is not applicable")));
	}

	public void testVisitorWrapperForwardsAssignment() {
		final var placement = new net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement(
				7, null, java.util.List.of(), null, false);
		final var received = new java.util.ArrayList<net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement>();
		final var delegate = new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(null) {
			@Override
			public void visitAssignment(final net.zamasoft.foliojet.css.style.running.RunningRegistry.Placement value) {
				received.add(value);
			}
		};
		new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(delegate).visitAssignment(placement);
		assertEquals(java.util.List.of(placement), received);
	}

	private void invariant(final String body) throws Exception {
		for (final boolean tagged : new boolean[] { false, true }) {
			final String css = "p{width:85pt;line-height:1.4}.running{position:running(h);"
					+ "font-size:80pt;line-height:4;vertical-align:super;direction:rtl;unicode-bidi:bidi-override}";
			final var with = RunningCaptureTest.convert(css, body.replace("TOKEN",
					"<span class='running'>REMOVED<span>INNER</span></span>"), tagged, true, false);
			final var without = RunningCaptureTest.convert(css, body.replace("TOKEN", ""), tagged, true, false);
			assertFalse(with.pages().isEmpty());
			assertEquals("行高・改行・baseline・文字位置を含む表示リスト", without.pages(), with.pages());
			assertEquals(1L, with.context().getRunningRegistry().assignedCount());
			if (tagged) {
				final int expected = structureCount(without.pdf());
				final int actual = structureCount(with.pdf());
				System.err.println("[running R1b] structure elements: with=" + actual + ", without=" + expected);
				assertTrue(expected > 0);
				assertEquals(expected, actual);
			}
		}
	}

	private static int structureCount(final byte[] pdf) throws Exception {
		try (final var document = Loader.loadPDF(pdf)) {
			final var root = document.getDocumentCatalog().getStructureTreeRoot();
			assertNotNull(root);
			final Deque<PDStructureNode> nodes = new ArrayDeque<PDStructureNode>();
			nodes.push(root);
			int count = 0;
			while (!nodes.isEmpty()) {
				for (final Object kid : nodes.pop().getKids()) {
					if (kid instanceof PDStructureNode node) {
						++count;
						nodes.push(node);
					}
				}
			}
			return count;
		}
	}
}
