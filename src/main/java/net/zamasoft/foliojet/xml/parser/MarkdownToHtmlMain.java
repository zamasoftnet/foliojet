package net.zamasoft.foliojet.xml.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * A command-line tool that converts Markdown files (*.md) in a directory to HTML in bulk.
 * <p>
 * Used to preconvert Markdown to HTML at build time so document builds (manuals, etc.)
 * can adopt Markdown chapter sources while retaining existing XSLT joining
 * (XSLT 1.0 {@code document()} can read only XML).
 * Delegates conversion to {@link MarkdownParser#toHtml(String)}
 * (the same conversion path as runtime Markdown input).
 * </p>
 *
 * <p>
 * Usage: {@code java -cp ... net.zamasoft.foliojet.xml.parser.MarkdownToHtmlMain <input directory> <output directory>}
 * Recursively scans the input directory and writes {@code *.md} as same-named {@code *.html}
 * files in the output directory, preserving relative paths.
 * </p>
 */
public final class MarkdownToHtmlMain {
	private MarkdownToHtmlMain() {
		// Utility class
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
