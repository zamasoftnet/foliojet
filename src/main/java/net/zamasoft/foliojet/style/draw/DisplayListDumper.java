package net.zamasoft.foliojet.style.draw;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ページの表示リストをテキストとしてダンプします(回帰検証用)。
 * システムプロパティ {@value #DIR_PROPERTY} に出力先ディレクトリを設定すると有効になります。
 */
public final class DisplayListDumper {
	private static final Logger LOG = Logger.getLogger(DisplayListDumper.class.getName());

	public static final String DIR_PROPERTY = "net.zamasoft.foliojet.debug.display-list.dir";

	private DisplayListDumper() {
		// utility
	}

	/**
	 * ダンプが有効な場合、ページの表示リストを書き出します。
	 */
	public static void dumpPage(Drawer drawer, int pageNumber) {
		String dir = System.getProperty(DIR_PROPERTY);
		if (dir == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		drawer.dump(sb, "");
		try {
			File d = new File(dir);
			d.mkdirs();
			Files.writeString(new File(d, String.format(Locale.ROOT, "page-%04d.txt", pageNumber)).toPath(),
					sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "表示リストをダンプできませんでした", e);
		}
	}
}
