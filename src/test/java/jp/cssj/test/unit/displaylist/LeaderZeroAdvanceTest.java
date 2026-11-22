package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * A {@code leader()} whose pattern has no advance, or next to none, is drawn in bounded time. The pattern is
 * repeated every advance of one copy; with a font size of 0 that period is 0, the number of copies was computed as
 * {@code Long.MAX_VALUE} and drawing never ended. A period that is tiny but not 0 asked for tens of millions of
 * copies on each line.
 *
 * <p>
 * The conversions run on a thread of their own with a time limit, so that a regression fails the test instead of
 * stopping the run.
 * </p>
 */
public class LeaderZeroAdvanceTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** Seconds a conversion gets; they take a fraction of a second. */
	private static final int LIMIT = 30;

	/** A pattern of a font size of 0: no leader, the text around it is printed. */
	public void testZeroFontSize() throws Exception {
		assertKeepAndAfter("<style>p::after{content:leader(\".\");font-size:0}</style><p>KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<style>p{font-size:0}p::after{content:leader(dotted)}div{font-size:10pt}</style>"
				+ "<p>x</p><div>KEEP</div><div>AFTER</div>");
	}

	/** A pattern a few millionths of a point wide: the number of copies of a leader is limited. */
	public void testTinyFontSize() throws Exception {
		final StringBuilder body = new StringBuilder("<style>p::after{content:leader(\".\");font-size:0.0001px}</style>");
		for (int i = 0; i < 20; ++i) {
			body.append("<p>KEEP</p>");
		}
		assertKeepAndAfter(body + "<p>AFTER</p>");
	}

	/** An ordinary leader is still there: the line with it is laid out and the page number follows. */
	public void testOrdinaryLeader() throws Exception {
		final List<String> texts = texts("<style>p::after{content:leader(\".\") \"END\"}</style><p>KEEP</p><p>AFTER</p>");
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("the text after the leader: " + texts, texts.contains("END"));
	}

	private static void assertKeepAndAfter(final String body) throws Exception {
		final List<String> texts = texts(body);
		assertTrue("KEEP is on the page: " + body + " -> " + texts, texts.contains("KEEP"));
		assertTrue("AFTER is on the page: " + body + " -> " + texts, texts.contains("AFTER"));
	}

	/** {@link #convert} on a thread of its own, failing the test when it does not finish in {@link #LIMIT} seconds. */
	private static List<String> texts(final String body) throws Exception {
		final FutureTask<List<String>> task = new FutureTask<>(() -> convert(body));
		final Thread thread = new Thread(task, "leader-zero-advance");
		thread.setDaemon(true);
		thread.start();
		try {
			return task.get(LIMIT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			fail("the conversion did not finish in " + LIMIT + " seconds: " + body);
			return null;
		}
	}

	/** Converts the document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> convert(final String body) throws Exception {
		final File dir = Files.createTempDirectory("leader-zero-advance").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + body, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> texts = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				texts.add(m.group(1));
			}
		}
		return texts;
	}
}
