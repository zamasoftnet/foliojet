package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Font-index location (2026-08-29). Container setups mounting fonts read-only cannot write
 * the index beside the configuration file, so {@code jp.cssj.font.index.dir} allows
 * placing it elsewhere.
 */
public class FontIndexLocationTest extends TestCase {
	private static final String PROPERTY = "jp.cssj.font.index.dir";

	private String saved;

	@Override
	protected void setUp() {
		this.saved = System.getProperty(PROPERTY);
		System.clearProperty(PROPERTY);
	}

	@Override
	protected void tearDown() {
		if (this.saved == null) {
			System.clearProperty(PROPERTY);
		} else {
			System.setProperty(PROPERTY, this.saved);
		}
	}

	/** The default remains beside the configuration file. */
	public void testDefaultsToTheConfigurationDirectory() throws IOException {
		final File profiles = new File("build/tmp/font-index-default").getAbsoluteFile();
		assertEquals(new File(profiles, "fonts/fonts-print.xml.db"),
				DirectSession.fontIndexFile(profiles, "fonts/fonts-print.xml"));
	}

	/**
	 * Place it in the specified directory. Each configuration gets a separate index,
	 * preventing mix-ups when indexes for multiple profiles share a directory.
	 */
	public void testHonoursTheConfiguredDirectory() throws IOException {
		final File dir = Files.createTempDirectory("font-index").toFile();
		try {
			System.setProperty(PROPERTY, dir.getAbsolutePath());
			final File print = DirectSession.fontIndexFile(new File("/opt/copper/conf/profiles"),
					"fonts/fonts-print.xml");
			final File plain = DirectSession.fontIndexFile(new File("/opt/copper/conf/profiles"),
					"fonts/fonts.xml");
			assertEquals(dir.getAbsoluteFile(), print.getParentFile());
			assertEquals("fonts-fonts-print.xml.db", print.getName());
			assertFalse("configurations must not share one index file: " + print,
					print.getName().equals(plain.getName()));
		} finally {
			dir.delete();
		}
	}

	/** Create the directory if absent: the first startup with an empty volume must not fail. */
	public void testCreatesTheDirectory() throws IOException {
		final File parent = Files.createTempDirectory("font-index-parent").toFile();
		final File dir = new File(parent, "created/here");
		try {
			System.setProperty(PROPERTY, dir.getAbsolutePath());
			final File index = DirectSession.fontIndexFile(new File("/opt/copper/conf/profiles"),
					"fonts/fonts-print.xml");
			assertTrue("the index directory must be created", dir.isDirectory());
			assertEquals(dir.getAbsoluteFile(), index.getParentFile());
		} finally {
			dir.delete();
			new File(parent, "created").delete();
			parent.delete();
		}
	}
}
