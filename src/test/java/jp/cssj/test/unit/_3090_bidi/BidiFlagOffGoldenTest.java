package jp.cssj.test.unit._3090_bidi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 既定ON後も明示したflag OFFが既存RTL fixtureの表示リストを1 byteも変えないことを固定する。 */
public class BidiFlagOffGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	public void testDirGoldenIsUnchangedWhenFlagIsOff() throws Exception {
		final File output = new File("local/unittest/bidi-off/3000-SELECTOR_dir");
		deleteChildren(output);
		output.mkdirs();
		try (AutoCloseable scope = DisplayListDumper.scopedDir(output.getPath())) {
			final File pdf = new File("local/unittest/bidi-off/dir.pdf");
			pdf.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					session.property("layout.bidi.paragraph", "false");
					CTISessionHelper.transcodeFile(session, new File("files/unittest/3000-SELECTOR/dir.html"),
							"text/html", null);
				} finally {
					session.close();
				}
			}
		}

		// 2026-09-04 に段落 bidi が既定 ON になり display-list golden は ON の出力になった。flag OFF(旧行単位 bidi)の
		// 出力は HEAD 時点の旧 golden を legacy 参照として別置し、そちらと比べる
		final File golden = new File("files/unittest/3090-bidi/legacy-golden/3000-SELECTOR_dir");
		final File[] expectedPages = golden.listFiles((dir, name) -> name.endsWith(".txt"));
		final File[] actualPages = output.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(expectedPages);
		assertNotNull(actualPages);
		assertEquals(expectedPages.length, actualPages.length);
		for (final File expected : expectedPages) {
			final File actual = new File(output, expected.getName());
			assertTrue("表示リストがありません: " + actual, actual.isFile());
			assertEquals(expected.getName(), Files.readString(expected.toPath(), StandardCharsets.UTF_8),
					Files.readString(actual.toPath(), StandardCharsets.UTF_8));
		}
	}

	public void testPureLtrDisplayListIsIdenticalWithFlagOnAndOff() throws Exception {
		final File off = new File("local/unittest/bidi-ltr/off");
		final File on = new File("local/unittest/bidi-ltr/on");
		dump(new File("files/unittest/3090-bidi/ltr.html"), false, 1, off,
				new File("local/unittest/bidi-ltr/off.pdf"));
		dump(new File("files/unittest/3090-bidi/ltr.html"), true, 1, on,
				new File("local/unittest/bidi-ltr/on.pdf"));
		assertDisplayListsEqual(off, on);
	}

	public void testPassCountOneAndTwoDisplayListsAreIdentical() throws Exception {
		final File one = new File("local/unittest/bidi-pass-parity/pass-1");
		final File two = new File("local/unittest/bidi-pass-parity/pass-2");
		final File source = new File("files/unittest/3090-bidi/two-pass.html");
		dump(source, true, 1, one, new File("local/unittest/bidi-pass-parity/pass-1.pdf"));
		dump(source, true, 2, two, new File("local/unittest/bidi-pass-parity/pass-2.pdf"));
		assertDisplayListsEqual(one, two);
	}

	public void testStageOneDumpNamesLogicalAndVisualText() throws Exception {
		final File output = new File("local/unittest/bidi-logical-dump/stage1");
		dump(new File("files/unittest/3090-bidi/stage1.html"), true, 1, output,
				new File("local/unittest/bidi-logical-dump/stage1.pdf"));
		final StringBuilder pages = new StringBuilder();
		final File[] dumps = output.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(dumps);
		for (final File dump : dumps) {
			pages.append(Files.readString(dump.toPath(), StandardCharsets.UTF_8));
		}
		assertTrue(pages.toString().contains("text logical=\"אבג ABC\" visual=\"ABC גבא\""));
	}

	private static void dump(final File source, final boolean paragraphBidi, final int passCount,
			final File output, final File pdf)
			throws Exception {
		deleteChildren(output);
		output.mkdirs();
		pdf.getParentFile().mkdirs();
		synchronized (DisplayListDumper.class) {
			final String previous = System.getProperty(DisplayListDumper.DIR_PROPERTY);
			System.setProperty(DisplayListDumper.DIR_PROPERTY, output.getPath());
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					session.property("layout.bidi.paragraph", Boolean.toString(paragraphBidi));
					session.property("processing.pass-count", Integer.toString(passCount));
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				} finally {
					session.close();
				}
			} finally {
				if (previous == null) {
					System.clearProperty(DisplayListDumper.DIR_PROPERTY);
				} else {
					System.setProperty(DisplayListDumper.DIR_PROPERTY, previous);
				}
			}
		}
	}

	private static void assertDisplayListsEqual(final File expectedDir, final File actualDir) throws Exception {
		final File[] expectedPages = expectedDir.listFiles((dir, name) -> name.endsWith(".txt"));
		final File[] actualPages = actualDir.listFiles((dir, name) -> name.endsWith(".txt"));
		assertNotNull(expectedPages);
		assertNotNull(actualPages);
		assertEquals(expectedPages.length, actualPages.length);
		for (final File expected : expectedPages) {
			final File actual = new File(actualDir, expected.getName());
			assertTrue("表示リストがありません: " + actual, actual.isFile());
			assertEquals(expected.getName(), Files.readString(expected.toPath(), StandardCharsets.UTF_8),
					Files.readString(actual.toPath(), StandardCharsets.UTF_8));
		}
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
