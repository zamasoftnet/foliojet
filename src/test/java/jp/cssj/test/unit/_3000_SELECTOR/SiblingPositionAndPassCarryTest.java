package jp.cssj.test.unit._3000_SELECTOR;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Sibling positions kept on the elements and the general sibling combinator's memo (2026-10-11: they walked the chain
 * of preceding siblings, the square of the siblings, and the chain is now cut every 1,024 siblings), and the style
 * sheet carried over to a later pass without its rules added twice. Every paragraph is hidden unless a rule shows it.
 */
public class SiblingPositionAndPassCarryTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([A-Z][0-9]+)\"");

	/** Positions past the cuts of the chain of preceding siblings. */
	public void testPositionsAfterChainCut() throws Exception {
		final StringBuilder body = new StringBuilder("<div>");
		for (int n = 1; n <= 3000; ++n) {
			body.append(n % 2 == 0 ? "<p>P" : "<i>P").append(n).append(n % 2 == 0 ? "</p>" : "</i>");
		}
		body.append("</div>");
		assertEquals("[P2500, P2999]",
				shown(":nth-child(2500), i:nth-of-type(1500) { display: block }", body.toString(), 1).toString());
	}

	/** A sibling matched by an earlier query stays matched for the general sibling combinator, however far back. */
	public void testGeneralSiblingRemembered() throws Exception {
		final StringBuilder body = new StringBuilder("<div><p class=\"a\">A1</p>");
		for (int n = 2; n <= 3000; ++n) {
			body.append(n == 10 || n == 3000 ? "<p class=\"t\">T" : "<p>N").append(n).append("</p>");
		}
		body.append("</div>");
		assertEquals("[T10, T3000]", shown(".a ~ .t { display: block }", body.toString(), 1).toString());
	}

	/**
	 * With two passes a body's style sheet reaches the elements before it, and its rule beats the head's rule of the
	 * same specificity, as in Chrome (each pass appended the head's rules again after the body's).
	 */
	public void testTwoPassBodyStyleOrder() throws Exception {
		final String body = "<p class=\"s\">S1</p><style>.s { display: block }</style><p class=\"s\">S2</p>";
		assertEquals("[S2]", shown(".s { display: none }", body, 1).toString());
		assertEquals("[S1, S2]", shown(".s { display: none }", body, 2).toString());
	}

	/**
	 * With two passes :not(:has()) sees the facts of the earlier pass (the copies of the rules each pass appended had
	 * none). In one pass :has() holds only after the descendant that makes it true.
	 */
	public void testNotHas() throws Exception {
		final String css = ".c:not(:has(.x)) .t { display: block } .c:has(.x) .u { display: block }";
		final String body = "<div class=\"c\"><p class=\"t\">T1</p><p class=\"u\">U1</p><b class=\"x\">x</b>"
				+ "<p class=\"t\">T2</p><p class=\"u\">U2</p></div>";
		assertEquals("[T1, U2]", shown(css, body, 1).toString());
		assertEquals("[U1, U2]", shown(css, body, 2).toString());
	}

	/** The paragraphs drawn, in order. */
	static List<String> shown(final String css, final String body, final int passes) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 900pt; margin: 20pt } body { margin: 0; font: 8pt serif } p, i { margin: 0; display: none }\n"
				+ css + "</style></head><body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("sibling-position").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.property("processing.pass-count", String.valueOf(passes));
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> shown = new ArrayList<>();
		for (int page = 1;; ++page) {
			final File f = new File(dir, String.format("page-%04d.txt", page));
			if (!f.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				shown.add(m.group(1));
			}
		}
		return shown;
	}
}
