package jp.cssj.test.unit.displaylist;

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
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** The display list of each page of a document converted with one pass, as dumped text (2026-11-26). */
final class DisplayListPages {
	private static final Pattern TEXT = Pattern.compile("y=([-0-9.]+) Text\\[\"([^\"]*)\"");

	private DisplayListPages() {
	}

	static List<String> of(final String fixture, final String name) throws Exception {
		final File dir = new File("local/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		try (AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeFile(session, new File(fixture), "text/html", null);
			} finally {
				session.close();
			}
		}
		final File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<String>();
		for (final File page : files) {
			pages.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}

	/** Where a text is first drawn: {page (from 1), y}, or null. */
	static double[] find(final List<String> pages, final String text) {
		for (int i = 0; i < pages.size(); ++i) {
			final Matcher m = TEXT.matcher(pages.get(i));
			while (m.find()) {
				if (m.group(2).equals(text)) {
					return new double[] { i + 1, Double.parseDouble(m.group(1)) };
				}
			}
		}
		return null;
	}
}
