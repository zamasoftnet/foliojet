package net.zamasoft.foliojet.layout.draw;

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

	/**
	 * スレッドごとの出力先(システムプロパティより優先)。
	 *
	 * <p>
	 * システムプロパティはプロセス全体で共有されるため、<b>複数の変換を
	 * 並行させるとダンプ先が互いに上書きされる</b>。長時間の掃過
	 * (数百万文書)を並列化するには、変換スレッドごとに出力先を
	 * 分ける必要がある(2026-07-26)。
	 * </p>
	 */
	private static final ThreadLocal<String> DIR_OVERRIDE = new ThreadLocal<>();

	private DisplayListDumper() {
		// utility
	}

	/**
	 * このスレッドのダンプ出力先を設定します。返り値を閉じると元へ戻ります。
	 *
	 * <pre>
	 * try (var scope = DisplayListDumper.scopedDir(dir)) {
	 * 	// このスレッドの変換だけが dir へ出力する
	 * }
	 * </pre>
	 *
	 * @param dir 出力先(nullでこのスレッドの設定を外す)
	 * @return 閉じると元の設定へ戻すハンドル
	 */
	/**
	 * このスレッドに設定されている出力先を返します({@code null}なら未設定)。
	 * レイアウトを別スレッドで実行する側が引き継ぐために使います。
	 */
	public static String currentDir() {
		return DIR_OVERRIDE.get();
	}

	public static AutoCloseable scopedDir(final String dir) {
		final String saved = DIR_OVERRIDE.get();
		if (dir == null) {
			DIR_OVERRIDE.remove();
		} else {
			DIR_OVERRIDE.set(dir);
		}
		return () -> {
			if (saved == null) {
				DIR_OVERRIDE.remove();
			} else {
				DIR_OVERRIDE.set(saved);
			}
		};
	}

	/**
	 * ダンプが有効な場合、ページの表示リストを書き出します。
	 */
	public static void dumpPage(Drawer drawer, int pageNumber) {
		String dir = DIR_OVERRIDE.get();
		if (dir == null) {
			dir = System.getProperty(DIR_PROPERTY);
		}
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
