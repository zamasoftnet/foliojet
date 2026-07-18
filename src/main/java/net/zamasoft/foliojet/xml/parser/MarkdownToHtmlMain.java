package net.zamasoft.foliojet.xml.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * ディレクトリ内のMarkdownファイル(*.md)を一括でHTMLへ変換するコマンドライン
 * ツールです。
 * <p>
 * 各種ドキュメントビルド(copperpdf4のユーザーマニュアル等)がMarkdownを
 * 章のソースとして採用しつつ、既存のXSLT結合(XSLT 1.0の{@code document()}は
 * XMLしか読めない)をそのまま使い続けられるよう、ビルド時にHTMLへ事前変換する
 * ために用いる。変換そのものは{@link MarkdownParser#toHtml(String)}に委譲する
 * (実行時のMarkdown入力と同一の変換経路)。
 * </p>
 *
 * <p>
 * 使い方: {@code java -cp ... net.zamasoft.foliojet.xml.parser.MarkdownToHtmlMain <入力ディレクトリ> <出力ディレクトリ>}
 * 入力ディレクトリを再帰的に走査し、{@code *.md} を同名の{@code *.html}として
 * 出力ディレクトリへ相対パスを保ったまま書き出す。
 * </p>
 */
public final class MarkdownToHtmlMain {
	private MarkdownToHtmlMain() {
		// ユーティリティクラス
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("usage: MarkdownToHtmlMain <input-dir> <output-dir>");
			System.exit(1);
			return;
		}
		final Path inputDir = Paths.get(args[0]);
		final Path outputDir = Paths.get(args[1]);
		int count = 0;
		try (Stream<Path> files = Files.walk(inputDir)) {
			for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".md"))::iterator) {
				final String markdown = Files.readString(path, StandardCharsets.UTF_8);
				final String html = MarkdownParser.toHtml(markdown);
				final Path relative = inputDir.relativize(path);
				final String outName = relative.toString().replaceFirst("\\.md$", ".html");
				final Path outPath = outputDir.resolve(outName);
				Files.createDirectories(outPath.getParent());
				Files.writeString(outPath, html, StandardCharsets.UTF_8);
				System.out.println("converted " + path + " -> " + outPath);
				++count;
			}
		}
		System.out.println(count + " file(s) converted");
	}
}
