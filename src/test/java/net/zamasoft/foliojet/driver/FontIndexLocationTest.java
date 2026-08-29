package net.zamasoft.foliojet.driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * フォント索引の置き場所(2026-08-29)。フォントを読み取り専用でマウントする
 * コンテナ構成では設定ファイルの隣へ索引を書けないため、
 * {@code jp.cssj.font.index.dir}で別の場所へ逃がせるようにした。
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

	/** 既定は今までどおり設定ファイルの隣。 */
	public void testDefaultsToTheConfigurationDirectory() throws IOException {
		final File profiles = new File("build/tmp/font-index-default").getAbsoluteFile();
		assertEquals(new File(profiles, "fonts/fonts-print.xml.db"),
				DirectSession.fontIndexFile(profiles, "fonts/fonts-print.xml"));
	}

	/**
	 * 指定したディレクトリへ置く。設定ごとに別の索引になること——同じ
	 * ディレクトリに複数のプロファイルの索引が同居しても取り違えない。
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

	/** 無い場合は作る——volumeを空で与えた1回目の起動で落ちては困る。 */
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
