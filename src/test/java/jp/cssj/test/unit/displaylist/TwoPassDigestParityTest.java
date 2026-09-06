package jp.cssj.test.unit.displaylist;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.text.bidi.BidiSlice;
import net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.text.Text;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * TwoPass D7: 固定manifest全件の範囲側digestと例外台帳の採用値を検証する。
 * {@code -Dfoliojet.twopassDigest=regenerate} は候補だけを生成する。
 * manifest/digests/例外台帳の反映は差分レビュー後に人が行う。
 * regenerateでも例外台帳の範囲側照合を省かず、legacySha256は更新しない。
 *
 * <p>WSL で {@code ./gradlew test --tests '*TwoPassDigestParityTest' --rerun-tasks -i}。
 * ダンプは java.io.tmpdir に置き、文書ごとに削除する。頁の byte を正規化せず比較し、
 * SHA-256 も同じ byte から取る。頁数の差は例外で免除しない。</p>
 */
public final class TwoPassDigestParityTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String PROPERTY = "foliojet.twopassDigest";
	private static final Path DATA_DIR = Path.of("files/unittest/twopass-digest");
	private static final Path REPORT_DIR = Path.of("build/reports/twopass-digest");
	private static final String BOOTSTRAP = "# bootstrap: generate on first run";
	private static final String MANIFEST_HEADER = "# docPath\tsourcePath\tpassCount\tmimeType\tinput/output property=value ...";
	private static final String DIGEST_HEADER = "# docPath\tpage\trangeSha256";
	private static final String EXCEPTION_HEADER = "# docPath\tpage\treason\tlegacySha256\trangeSha256";
	private static final Comparator<PageKey> PAGE_ORDER = Comparator.comparing(PageKey::doc).thenComparingInt(PageKey::page);

	private enum Reason { LEGACY_STRUT_REUSE, LEGACY_STATIC_ABSOLUTE_ORDER }

	static record CorpusInput(String source, int passCount, String mimeType, Map<String, String> properties) { }
	private record PageKey(String doc, int page) { }
	private record ExceptionEntry(Reason reason, String legacy, String range) { }
	private record Table(boolean bootstrap, List<String[]> rows) { }

	private static final class Report {
		final List<String> failures = new ArrayList<>();
		final List<String> drift = new ArrayList<>();
		final Map<Reason, Set<String>> accepted = new EnumMap<>(Reason.class);
		int candidates, converted, pages, acceptedPages, missing;
		boolean manifestWritten, digestsWritten;

		void fail(final String message) {
			this.failures.add(message);
			System.err.println("[D7 ERROR] " + message);
		}
	}

	public void testDigestParity() throws Exception {
		final long started = System.nanoTime();
		final Report report = new Report();
		try {
			runParity(report, started);
		} finally {
			final String elapsed = elapsed(started);
			System.err.println("[D7] elapsed=" + elapsed + " documents=" + report.converted + "/" + report.candidates
					+ " pages=" + report.pages + " failures=" + report.failures.size());
		}
		assertTrue("TwoPass digest parity: " + report.failures.size() + " 件。" + REPORT_DIR.resolve("summary.md")
				+ "\n" + String.join("\n", report.failures.stream().limit(20).toList()), report.failures.isEmpty());
	}

	private static void runParity(final Report report, final long started) throws Exception {
		final String mode = System.getProperty(PROPERTY, "check");
		require(Set.of("check", "strict", "regenerate").contains(mode), PROPERTY + " は check / strict / regenerate のみ");
		final boolean regenerate = "regenerate".equals(mode);
		Files.createDirectories(REPORT_DIR);
		if (regenerate) {
			// 今回の生成が途中で止まっても前回の候補を誤って採用させない。
			Files.deleteIfExists(REPORT_DIR.resolve("manifest.candidate.tsv"));
			Files.deleteIfExists(REPORT_DIR.resolve("digests.candidate.tsv"));
		}
		final Map<String, CorpusInput> discovered = corpusDocuments();
		final Table manifestTable = readTable(DATA_DIR.resolve("manifest.tsv"));
		final Map<String, CorpusInput> saved = readManifest(manifestTable);
		// 列挙側に参照が残った既知入力の削除も REMOVED として検出する。
		// 通常実行では失敗。regenerate だけが削除を候補へ反映する。
		discovered.entrySet().removeIf(entry -> saved.containsKey(entry.getKey())
				&& saved.get(entry.getKey()).source().equals(entry.getValue().source())
				&& !Files.isRegularFile(Path.of(entry.getValue().source())));
		if (!manifestTable.bootstrap()) report.drift.addAll(manifestDiff(saved, discovered));
		for (final String line : report.drift) System.err.println("[D7 MANIFEST] " + line);
		checkManifestDrift(report, mode);
		final Map<String, CorpusInput> manifest = regenerate || manifestTable.bootstrap() ? discovered : saved;
		require(!manifest.isEmpty(), "manifest が空");
		if (!regenerate && manifestTable.bootstrap()) report.fail("manifest 未反映。regenerate の候補をレビューして反映する");
		if (regenerate || manifestTable.bootstrap()) {
			writeManifest(manifest);
			report.manifestWritten = true;
		}
		final Table digestTable = readTable(DATA_DIR.resolve("digests.tsv"));
		final Map<PageKey, String> baseline = readDigests(digestTable);
		final boolean writeDigests = regenerate || digestTable.bootstrap();
		if (!regenerate && digestTable.bootstrap()) report.fail("digests 未反映。regenerate の候補をレビューして反映する");
		final Map<PageKey, ExceptionEntry> exceptions = readExceptions(readTable(DATA_DIR.resolve("exceptions.tsv")));
		final Map<PageKey, String> actual = new TreeMap<>(PAGE_ORDER);
		final Set<PageKey> visitedExceptions = new HashSet<>();
		if (!writeDigests) {
			final Set<String> baselineDocs = new TreeSet<>();
			baseline.keySet().forEach(key -> baselineDocs.add(key.doc()));
			if (!baselineDocs.equals(manifest.keySet())) report.fail("manifest と digests の文書集合が不一致");
		}
		{
			for (final var entry : manifest.entrySet()) {
				final String doc = entry.getKey();
				++report.candidates;
				if (!discovered.containsKey(doc) || !Files.isRegularFile(Path.of(entry.getValue().source()))) {
					++report.missing;
					report.fail(doc + " MISSING manifest 文書が列挙にない、または保存条件の入力がない");
					continue;
				}
				try (final Rendered range = render(entry.getValue())) {
					compareDocument(doc, range.pages(), baseline, writeDigests, exceptions,
							visitedExceptions, actual, report);
					++report.converted;
				} catch (final Exception | AssertionError e) {
					// 部分出力を基準にせず、失敗文書も分母に残して全件の診断を続ける。
					report.fail(doc + " CONVERSION " + causeChain(e));
				}
				if (report.candidates % 50 == 0) {
					System.err.println("[D7] processed=" + report.candidates + "/" + manifest.size() + " elapsed=" + elapsed(started));
				}
			}
		}
		for (final PageKey key : exceptions.keySet()) {
			if (!visitedExceptions.contains(key)) {
				report.fail(key + " 例外台帳の文書/頁を観測できない(削除・頁消失・変換失敗)");
			}
		}
		if (report.candidates == 0) report.fail("変換対象が0件");
		if (writeDigests) {
			if (report.converted == manifest.size() && report.converted > 0) {
				// 不一致があっても範囲側の候補は保存する。台帳は自動更新しない。
				writeDigests(actual);
				report.digestsWritten = true;
			} else {
				Files.deleteIfExists(REPORT_DIR.resolve("digests.candidate.tsv"));
				report.fail("全件の変換が完了していないため digests.candidate.tsv は生成しない");
			}
		}
		final String summary = summary(report, exceptions, manifest.size(), mode, started);
		Files.writeString(REPORT_DIR.resolve("summary.md"), summary, StandardCharsets.UTF_8);
	}

	private static void compareDocument(final String doc, final List<Path> pages,
			final Map<PageKey, String> baseline, final boolean writeDigests, final Map<PageKey, ExceptionEntry> exceptions,
			final Set<PageKey> visited, final Map<PageKey, String> actual, final Report report) throws Exception {
		for (int page = 1; page <= pages.size(); ++page) {
			final PageKey key = new PageKey(doc, page);
			final String digest = sha256(Files.readAllBytes(pages.get(page - 1)));
			++report.pages;
			actual.put(key, digest);
			if (!writeDigests && !digest.equals(baseline.get(key))) {
				report.fail(key + " RANGE_DIGEST expected=" + baseline.get(key) + " actual=" + digest);
			}
			final ExceptionEntry exception = exceptions.get(key);
			if (exception != null) {
				visited.add(key);
				// legacySha256は履歴証拠として凍結。採用した範囲側だけを検証する。
				if (!exception.range().equals(digest)) report.fail(key + " 登録済み範囲digestから変化");
				++report.acceptedPages;
				report.accepted.computeIfAbsent(exception.reason(), unused -> new TreeSet<>()).add(doc);
			}
		}
		if (!writeDigests) {
			for (final PageKey key : baseline.keySet()) {
				if (key.doc().equals(doc) && key.page() > pages.size()) report.fail(key + " RANGE_DIGEST 基準の頁が消失");
			}
		}
	}

	private static String causeChain(final Throwable failure) {
		final StringBuilder message = new StringBuilder();
		final Set<Throwable> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (!message.isEmpty()) message.append(" <- caused by ");
			if (!seen.add(cause)) {
				message.append("[cause cycle: ").append(cause.getClass().getName()).append(']');
				break;
			}
			message.append(cause.getClass().getName());
			if (cause.getMessage() != null) message.append(": ").append(tsv(cause.getMessage()));
		}
		return message.toString();
	}

	/** Census と同じ全階層・include/source/skip。golden の pass-count を先に入れる。 */
	static Map<String, CorpusInput> corpusDocuments() throws Exception {
		final Map<String, CorpusInput> documents = new TreeMap<>();
		require(Files.isDirectory(Path.of("files/unittest")), "unittest corpus がない");
		for (final var doc : DisplayListGoldenTest.corpusDocuments()) {
			documents.put(doc.path(), input(Path.of("files/unittest", doc.path()), doc.passCount(), Map.of()));
		}
		addHtmlTree(documents, Path.of("files/unittest"), "");
		// 同じ入力を別の変換条件で固定する。値は環境依存の絶対URIにせず、
		// manifestのinput/output列へ保存し、変換入口でだけ解決する。
		documents.put("3200-line-breaker/parity-float.html@pretty", input(
				Path.of("files/unittest/3200-line-breaker/parity-float.html"), 1,
				Map.of("input.default-stylesheet", "files/unittest/3200-line-breaker/text-wrap-pretty.css")));
		addHtmlTree(documents, Path.of("files/fuzz-repro"), "fuzz-repro/");
		addHtmlTree(documents, Path.of("tmp"), "tmp/");
		final Path visual = Path.of("../copperpdf4/dev/files/visual");
		addImageTestManifest(documents, visual, visual.resolve("MANIFEST.txt"), new HashSet<>());
		return documents;
	}

	private static void addHtmlTree(final Map<String, CorpusInput> documents, final Path root, final String prefix)
			throws IOException {
		if (!Files.isDirectory(root)) return;
		try (final var paths = Files.walk(root)) {
			for (final Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml")) {
					documents.putIfAbsent(prefix + portable(root.relativize(path)), input(path, 1, Map.of()));
				}
			}
		}
	}

	private static void addImageTestManifest(final Map<String, CorpusInput> documents, final Path root,
			final Path manifest, final Set<Path> visited) throws IOException {
		if (!visited.add(manifest.toAbsolutePath().normalize())) return;
		for (final String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
			final String entry = line.trim();
			if (entry.isEmpty() || entry.startsWith("#")) continue;
			if (entry.startsWith("@")) {
				addImageTestManifest(documents, root, manifest.getParent().resolve(entry.substring(1).trim()), visited);
			} else {
				addImageTestEntry(documents, root, entry);
			}
		}
	}

	private static void addImageTestEntry(final Map<String, CorpusInput> documents, final Path root, final String entry) {
		final String[] fields = entry.split("\\s+");
		final Map<String, String> options = new TreeMap<>();
		for (int i = 1; i < fields.length; ++i) {
			final int eq = fields[i].indexOf('=');
			require(eq > 0, "imageTest option に = がない: " + fields[i]);
			options.put(fields[i].substring(0, eq), fields[i].substring(eq + 1));
		}
		if (!"true".equals(options.get("skip"))) {
			documents.put("imageTest/" + fields[0], input(root.resolve(options.getOrDefault("source", fields[0])), 1, options));
		}
	}

	private static CorpusInput input(final Path source, final int passCount, final Map<String, String> options) {
		final Map<String, String> properties = new TreeMap<>();
		properties.put("input.include", "**");
		properties.put("input.property-pi", "true");
		options.forEach((key, value) -> { if (isProperty(key)) properties.put(key, value); });
		final String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
		final String mimeType = name.endsWith(".xml") ? "text/xml"
				: name.endsWith(".xhtml") ? "application/xhtml+xml" : "text/html";
		return new CorpusInput(portable(source.normalize()), passCount, mimeType, Map.copyOf(properties));
	}

	private static boolean isProperty(final String key) {
		return key.startsWith("input.") || key.startsWith("output.");
	}

	private static String portable(final Path path) {
		return path.toString().replace('\\', '/');
	}

	private static List<String> manifestDiff(final Map<String, CorpusInput> saved, final Map<String, CorpusInput> current) {
		final List<String> differences = new ArrayList<>();
		for (final String doc : current.keySet()) {
			if (!saved.containsKey(doc)) differences.add("ADDED " + doc);
			else if (!saved.get(doc).equals(current.get(doc))) differences.add("CONDITIONS " + doc + " saved="
					+ manifestLine(doc, saved.get(doc)) + " current=" + manifestLine(doc, current.get(doc)));
		}
		for (final String doc : saved.keySet()) if (!current.containsKey(doc)) differences.add("REMOVED " + doc);
		return differences;
	}

	private static void checkManifestDrift(final Report report, final String mode) {
		for (final String line : report.drift) {
			if (!"regenerate".equals(mode) && (line.startsWith("REMOVED ")
					|| "strict".equals(mode) && line.startsWith("ADDED "))) {
				report.fail("MANIFEST " + line + " (regenerate の候補をレビューして反映する)");
			}
		}
	}

	private record Rendered(Path directory, List<Path> pages) implements AutoCloseable {
		@Override
		public void close() throws IOException { deleteDump(this.directory); }
	}

	/**
	 * D7 専用の値直列化。golden の describe/dump は使わない。
	 * UTF-8/LF、属性パス順、double/float は IEEE 754 の16進ビット列。
	 * 内部 Drawable は exact class の許可表と全フィールド名の照合で fail closed。
	 * 外部/プラグイン型はクラス名、公開フィールド・bean getter・record 成分を名前順に記録。
	 * 値は同じ規約で再帰し、Object.toString/identity hash・非公開状態の推測は使わない。
	 * 画像/プラグイン描画は RecorderGC の命令も記録し、未知属性・循環・読取失敗はエラー。
	 * 共有バッファの未使用部分・キャッシュ・UA/DOM/レイアウト木は描画属性ではない。
	 * それらの参照は実際に描画が読む値へ射影し、参照の同一性は初出順で表す。
	 */
	private static final class DigestSerializer {
		private static final String DRAW = "net.zamasoft.foliojet.layout.draw.";
		private static final String BOX = "net.zamasoft.foliojet.layout.box.";
		private static final String PDF = "net.zamasoft.pdfg2d.";
		private static final String CSS = "net.zamasoft.foliojet.css.value.";
		private static final Map<String, String> DRAWABLE_FIELDS = Map.ofEntries(
				Map.entry(DRAW + "AbstractDrawable", "clip pageBox opacity transform blendMode filter"),
				Map.entry(DRAW + "BackgroundDrawable", "background width height"),
				Map.entry(DRAW + "BackgroundBorderDrawable", "background border padding width height"),
				Map.entry(DRAW + "AbsoluteRectFrameDrawable", "frame width height textClip"),
				Map.entry(DRAW + "DebugDrawable", "width height color"),
				Map.entry(BOX + "AbstractReplacedBox$ReplacedBoxDrawable", "image objectFit objectPosition"),
				Map.entry(BOX + "AbstractTextBox$TextSequenceDrawable",
						"contents off len params ascent descent logicalLine lineVisualText bidiSlices structRef lineScope"),
				Map.entry(BOX + "AbstractTextBox$LeaderDrawable", "leader params ascent descent"),
				Map.entry(BOX + "AbstractTextBox$TextDecorationDrawable", "params decoration ascent descent width height"),
				Map.entry(BOX + "impl.TableBox$BorderDrawable", "border width height"),
				Map.entry(BOX + "impl.TableBox$CollapsedBordersDrawable", "borders vertical"),
				Map.entry(BOX + "impl.TableCellBox$TableCellBoxDrawable", "collapse spacing"),
				Map.entry(BOX + "content.ColumnsContainer$ColumnRuleDrawable", "x y this$0"),
				Map.entry(BOX + "impl.RubyUnitBox$RubyUnitDrawable", "box"),
				Map.entry(BOX + "impl.WarichuUnitBox$WarichuDrawable", "box"),
				Map.entry(BOX + "impl.PageBox$FootnoteSeparatorDrawable", "rect"),
				Map.entry("net.zamasoft.foliojet.ua.impl.pdf.PDFOutputDrawable", "action digestValues"));
		private static final Map<String, String> GRADIENT_FIELDS = Map.ofEntries(
				Map.entry(CSS + "css3.LinearGradientValue", "angle stops repeating"),
				Map.entry(CSS + "css3.RadialGradientValue", "circle size sizeX sizeY posX posY stops repeating"),
				Map.entry(CSS + "css3.ConicGradientValue", "fromAngle posX posY stops repeating"),
				Map.entry(CSS + "css3.GradientStops", "colors ratio abs auto"),
				Map.entry(PDF + "gc.paint.LinearGradient", "x1 y1 x2 y2 fractions colors transform spread"),
				Map.entry(PDF + "gc.paint.RadialGradient", "cx cy radius fx fy fractions colors transform spread"),
				Map.entry(PDF + "gc.paint.ConicGradient", "cx cy startAngle fractions colors transform spread"));
		private static final Map<String, String> LABEL_FIELDS = Map.of(BOX + "impl.FootnoteLabelImage",
				"footnoteId marker prefix suffix fontStyle fontManager digitAdvance prefixAdvance suffixAdvance ascent descent resolvedNumber");
		private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
			@Override
			protected List<Field> computeValue(final Class<?> type) {
				final List<Field> fields = new ArrayList<>();
				for (Class<?> c = type; c != Object.class && c != null; c = c.getSuperclass()) {
					for (final Field field : c.getDeclaredFields()) {
						if (Modifier.isStatic(field.getModifiers())) continue;
						require(field.trySetAccessible(), "D7: 属性を読めない " + field);
						fields.add(field);
					}
				}
				fields.sort(Comparator.comparing((Field field) -> field.getDeclaringClass().getName()).thenComparing(Field::getName));
				return List.copyOf(fields);
			}
		};

		private final StringBuilder out = new StringBuilder("D7-drawable-v1\n");
		private final Map<Long, Integer> lines = new LinkedHashMap<>();
		private final Map<Long, Integer> paragraphs = new LinkedHashMap<>();
		private final Map<Object, Integer> structures = new IdentityHashMap<>();
		private final Map<Object, Integer> filters = new IdentityHashMap<>();
		private final Set<Object> active = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private final Map<Image, String> imageDigests;
		private net.zamasoft.pdfg2d.gc.font.FontManager fontManager;
		private net.zamasoft.foliojet.ua.UserAgent userAgent;
		private int pluginDepth;

		DigestSerializer(final Map<Image, String> imageDigests) {
			this.imageDigests = imageDigests;
		}

		byte[] page(final Drawer root) throws Exception {
			// Drawer.draw と同じ: 自分の装飾→負 z の子→残り→非負 z の子。
			// 論理行の後続 command を省略しない。深い stacking context も反復で辿る。
			record Step(Drawer drawer, String path, int from, int to, boolean paint) { }
			final var work = new ArrayDeque<Step>();
			work.push(new Step(root, "page", 0, 0, false));
			while (!work.isEmpty()) {
				final Step step = work.pop();
				final Drawer drawer = step.drawer();
				final List<?> commands = (List<?>) get(drawer, "paintCommands");
				final int count = commands == null ? 0 : commands.size();
				if (step.paint()) {
					for (int i = step.from(); i < step.to(); ++i) {
						final Object command = commands.get(i);
						final String path = step.path() + ".paint[" + i + "]";
						value(path + ".x", get(command, "x"));
						value(path + ".y", get(command, "y"));
						value(path + ".artifact", get(command, "artifact"));
						structure(path + ".structure", get(command, "structRef"));
						drawable(path, (Drawable) get(command, "drawable"),
								(double) get(command, "x"), (double) get(command, "y"));
					}
					continue;
				}
				require(drawer.getClass() == Drawer.class, "D7: 未知の Drawer " + drawer.getClass().getName());
				value(step.path() + ".z", get(drawer, "z"));
				value(step.path() + ".artifact", get(drawer, "artifact"));
				value(step.path() + ".filter", get(drawer, "filter"));
				value(step.path() + ".fallbackTransform", get(drawer, "fallbackTransform"));
				value(step.path() + ".adoptedTransform", get(drawer, "adoptedTransform"));
				value(step.path() + ".transformAdopted", get(drawer, "transformAdopted"));
				structure(step.path() + ".structure", get(drawer, "structRef"));
				structure(step.path() + ".currentStructure", get(drawer, "currentStructRef"));
				final int decoration = Math.min((int) get(drawer, "ownDecorationEnd"), count);
				value(step.path() + ".decorationEnd", decoration);
				value(step.path() + ".paintCount", count);
				final List<?> children = (List<?>) get(drawer, "stackingContexts");
				final List<Object> sorted = children == null ? new ArrayList<>() : new ArrayList<>(children);
				sorted.sort(Comparator.comparingInt((Object child) -> (int) get(get(child, "drawer"), "z"))
						.thenComparingInt(child -> (int) get(child, "insertionOrdinal")));
				value(step.path() + ".childCount", sorted.size());
				int split = 0;
				while (split < sorted.size() && (int) get(get(sorted.get(split), "drawer"), "z") < 0) ++split;
				for (int i = sorted.size() - 1; i >= split; --i) {
					work.push(new Step((Drawer) get(sorted.get(i), "drawer"), step.path() + ".child[" + i + "]", 0, 0, false));
				}
				work.push(new Step(drawer, step.path(), decoration, count, true));
				for (int i = split - 1; i >= 0; --i) {
					work.push(new Step((Drawer) get(sorted.get(i), "drawer"), step.path() + ".child[" + i + "]", 0, 0, false));
				}
				work.push(new Step(drawer, step.path(), 0, decoration, true));
			}
			return this.out.toString().getBytes(StandardCharsets.UTF_8);
		}

		private static void checkFields(final Class<?> type, final Map<String, String> schemas) {
			final String schema = schemas.get(type.getName());
			require(schema != null, "D7: 未知の schema " + type.getName());
			final Set<String> names = new TreeSet<>();
			for (final Field f : type.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) names.add(f.getName());
			require(names.equals(schema.isEmpty() ? Set.of() : Set.of(schema.split(" "))),
					"D7: 属性が変化 " + type.getName() + " " + names);
		}

		private static void checkDrawable(final Class<?> type) {
			for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) checkFields(c, DRAWABLE_FIELDS);
		}

		private void drawable(final String path, final Drawable drawable, final double x, final double y) throws Exception {
			final String type = drawable.getClass().getName();
			final boolean plugin = !DRAWABLE_FIELDS.containsKey(type);
			require(!plugin || isPluginType(drawable.getClass()), "D7: 未知の drawable " + type);
			if (drawable instanceof net.zamasoft.foliojet.layout.draw.AbstractDrawable) {
				final PageBox page = (PageBox) get(drawable, "pageBox");
				this.userAgent = page == null ? null : page.getUserAgent();
				this.fontManager = this.userAgent == null ? null : this.userAgent.getFontManager();
			}
			if (plugin) {
				for (Class<?> c = drawable.getClass().getSuperclass(); c != Object.class; c = c.getSuperclass()) {
					if (Drawable.class.isAssignableFrom(c) && !isPluginType(c)) checkFields(c, DRAWABLE_FIELDS);
				}
				pluginProperties(path + ".public", drawable);
				final RecorderGC recorder = new RecorderGC(this.fontManager, true);
				drawable.draw(recorder, x, y);
				value(path + ".commands", recorder.getPage().commands());
			} else {
				checkDrawable(drawable.getClass());
			}
			value(path + ".type", type);
			Class<?> fieldType = drawable.getClass();
			while (plugin && fieldType != Object.class && !DRAWABLE_FIELDS.containsKey(fieldType.getName())) {
				fieldType = fieldType.getSuperclass();
			}
			for (final Field field : FIELDS.get(fieldType)) {
				final String name = field.getName();
				final Object v = field.get(drawable);
				final String p = path + "." + name;
				switch (name) {
				case "pageBox" -> {
					final PageBox page = (PageBox) v;
					value(p + ".width", page == null ? null : page.getWidth());
					value(p + ".height", page == null ? null : page.getHeight());
				}
				case "params" -> textParams(p, (AbstractTextParams) v);
				case "contents" -> {
					final int off = (int) get(drawable, "off"), len = (int) get(drawable, "len");
					value(p, ((List<?>) v).subList(off, off + len));
				}
				case "off", "len" -> { /* contents は描画区間の値列として記録済み */ }
				case "structRef" -> structure(p, v);
				case "lineScope" -> require(v == null, "D7: 描画中の一時 scope を観測した");
				case "action" -> { /* 閉包の処理対象は digestValues に保持する */ }
				case "box" -> rubyOrWarichu(p, (IBox) v);
				case "this$0" -> {
					final var columns = (net.zamasoft.foliojet.layout.box.content.ColumnsContainer) v;
					final var box = (net.zamasoft.foliojet.layout.box.AbstractContainerBox) get(columns, "box");
					final var params = box.getBlockParams();
					value(p + ".count", columns.getColumnCount());
					value(p + ".lineSize", box.getLineSize());
					value(p + ".innerWidth", box.getInnerWidth());
					value(p + ".innerHeight", box.getInnerHeight());
					value(p + ".columns", params.columns);
					value(p + ".flow", params.flow);
					value(p + ".writingModeVariant", params.writingModeVariant);
					value(p + ".direction", params.direction);
				}
				default -> value(p, v);
				}
			}
		}

		private void textParams(final String path, final AbstractTextParams params) throws Exception {
			// レイアウト前の strut/改行条件や DOM を比較せず、描画が読む全スタイルを固定する。
			for (final String name : List.of("flow", "writingModeVariant", "direction", "fontStyle", "color",
					"textStrokeWidth", "textStrokeColor", "strokeBeforeFill", "textShadows", "decorationThickness")) {
				value(path + "." + name, get(params, name));
			}
			final var metrics = params.getFontListMetrics();
			value(path + ".maxAscent", metrics.getMaxAscent());
			value(path + ".maxDescent", metrics.getMaxDescent());
			value(path + ".maxXHeight", metrics.getMaxXHeight());
		}

		private void rubyOrWarichu(final String path, final IBox box) throws Exception {
			value(path + ".width", box.getWidth());
			value(path + ".height", box.getHeight());
			textParams(path + ".params", (AbstractTextParams) box.getParams());
			for (final Field field : FIELDS.get(box.getClass())) {
				// 合成 inline-block の実体・継続用木でなく、この型が持つ確定済み描画データ。
				if (field.getDeclaringClass() == box.getClass()) value(path + "." + field.getName(), field.get(box));
			}
		}

		private void structure(final String path, final Object ref) throws Exception {
			value(path, ref == null ? null : this.structures.computeIfAbsent(ref, unused -> this.structures.size()));
		}

		private void value(final String path, final Object v) throws Exception {
			if (v == null) { line(path, "null"); return; }
			if (v instanceof String s) { line(path, quote(s)); return; }
			if (v instanceof Double d) { line(path, "d:" + HexFormat.of().toHexDigits(Double.doubleToLongBits(d))); return; }
			if (v instanceof Float f) { line(path, "f:" + HexFormat.of().toHexDigits(Float.floatToIntBits(f))); return; }
			if (v instanceof Boolean || v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) {
				line(path, v.getClass().getSimpleName() + ":" + v); return;
			}
			if (v instanceof Character c) { line(path, "char:" + HexFormat.of().toHexDigits(c)); return; }
			if (v instanceof Enum<?> e) { line(path, e.getDeclaringClass().getName() + ":" + e.name()); return; }
			if (v instanceof URI uri) { value(path, uri.toASCIIString()); return; }
			if (v instanceof Locale locale) { value(path, locale.toLanguageTag()); return; }
			require(this.active.add(v), "D7: 描画属性の循環 " + path + " " + v.getClass().getName());
			try {
				if (v instanceof AffineTransform transform) {
					final double[] matrix = new double[6];
					transform.getMatrix(matrix);
					value(path + ".matrix", matrix);
				} else if (v instanceof Shape shape) {
					path(path, shape);
				} else if (v instanceof Text text) {
					text(path, text);
				} else if (v instanceof FontStyle font) {
					font(path, font);
				} else if (v instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue length) {
					// AbsoluteLengthValueImpl の UA を辿らず、px の解像度も反映した描画長を固定。
					value(path + ".pt", length.getLength());
				} else if (v instanceof Image image) {
					image(path, image);
				} else if (v instanceof net.zamasoft.pdfg2d.pdf.StructureRef) {
					structure(path, v);
				} else if (v instanceof LogicalLineEmission logical) {
					value(path + ".line", this.lines.computeIfAbsent(logical.lineId(), unused -> this.lines.size()));
					value(path + ".text", logical.logicalText());
				} else if (v instanceof BidiSlice slice) {
					value(path + ".paragraph", this.paragraphs.computeIfAbsent(slice.paragraphId(), unused -> this.paragraphs.size()));
					value(path + ".start", slice.syntheticStart());
					value(path + ".limit", slice.syntheticLimit());
					value(path + ".paragraphLevel", slice.paragraphLevel());
					value(path + ".level", slice.level());
					value(path + ".ancestorCount", slice.inlineAncestry().size());
					for (int i = 0; i < slice.inlineAncestry().size(); ++i) {
						structure(path + ".ancestor[" + i + "]", slice.inlineAncestry().get(i));
					}
				} else if (v.getClass().isArray()) {
					final int length = Array.getLength(v);
					line(path, "array[" + length + "]");
					for (int i = 0; i < length; ++i) value(path + "[" + i + "]", Array.get(v, i));
				} else if (v instanceof List<?> list) {
					line(path, "list[" + list.size() + "]");
					for (int i = 0; i < list.size(); ++i) value(path + "[" + i + "]", list.get(i));
				} else if (this.pluginDepth > 0 && v instanceof Map<?, ?> map) {
					final Map<String, Object> sorted = new TreeMap<>();
					for (final var entry : map.entrySet()) {
						require(entry.getKey() instanceof String, "D7: plugin の map key は文字列のみ " + path);
						sorted.put((String) entry.getKey(), entry.getValue());
					}
					line(path, "map[" + sorted.size() + "]");
					for (final var entry : sorted.entrySet()) value(path + "[" + quote(entry.getKey()) + "]", entry.getValue());
				} else if (this.pluginDepth > 0 && isPluginType(v.getClass())) {
					pluginProperties(path, v);
				} else {
					final String name = v.getClass().getName();
					if (GRADIENT_FIELDS.containsKey(name)) checkFields(v.getClass(), GRADIENT_FIELDS);
					require(name.startsWith(BOX + "params.")
							|| name.startsWith("net.zamasoft.foliojet.css.value.")
							|| Set.of("net.zamasoft.foliojet.layout.part.AbsoluteRectFrame",
									"net.zamasoft.foliojet.layout.part.AbsoluteInsets",
									"net.zamasoft.foliojet.layout.part.TableCollapsedBorders",
									"net.zamasoft.foliojet.layout.text.LeaderQuad",
									"net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor$RadioBuilder",
									"net.zamasoft.foliojet.ua.impl.pdf.PDFVisitor$SelectBuilder",
									BOX + "impl.RubyUnitBox$RubyAnnotation").contains(name)
							|| name.startsWith(PDF + "gc.paint.") || name.startsWith(PDF + "gc.font.FontFamily")
							|| name.startsWith(PDF + "pdf.form.") || name.startsWith(PDF + "pdf.annot.")
							|| name.equals(PDF + "gc.GroupEffects") || name.equals(PDF + "gc.GroupEffects$DropShadow")
							|| v instanceof RecorderGC.Command,
							"D7: 未知の描画属性 " + path + " " + name);
					line(path, name);
					// FilterScope の除外は値の等しさではなく own の同一性で決まる。
					if (v instanceof net.zamasoft.foliojet.css.value.css3.FilterValue) {
						value(path + ".identity", this.filters.computeIfAbsent(v, unused -> this.filters.size()));
					}
					for (final Field field : FIELDS.get(v.getClass())) value(path + "." + field.getName(), field.get(v));
				}
			} finally {
				this.active.remove(v);
			}
		}

		private void path(final String path, final Shape shape) throws Exception {
			final PathIterator iterator = shape.getPathIterator(null);
			value(path + ".winding", iterator.getWindingRule());
			final double[] coords = new double[6];
			int index = 0;
			while (!iterator.isDone()) {
				final int kind = iterator.currentSegment(coords);
				final int count = switch (kind) {
				case PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 2;
				case PathIterator.SEG_QUADTO -> 4;
				case PathIterator.SEG_CUBICTO -> 6;
				case PathIterator.SEG_CLOSE -> 0;
				default -> throw new IllegalArgumentException("D7: 未知の path segment " + kind);
				};
				value(path + "[" + index + "].kind", kind);
				value(path + "[" + index++ + "].points", Arrays.copyOf(coords, count));
				iterator.next();
			}
			value(path + ".segments", index);
		}

		private void font(final String path, final FontStyle font) throws Exception {
			value(path + ".family", font.getFamily());
			value(path + ".size", font.getSize());
			value(path + ".style", font.getStyle());
			value(path + ".weight", font.getWeight());
			value(path + ".widthClass", font.getWidthClass());
			value(path + ".direction", font.getDirection());
			value(path + ".orientation", font.getTextOrientation());
			value(path + ".lang", font.getLang());
			value(path + ".synthesisWeight", font.getSynthesisWeight());
			value(path + ".synthesisStyle", font.getSynthesisStyle());
			final var features = font.getFeatures();
			value(path + ".features", features.size());
			for (int i = 0; i < features.size(); ++i) {
				value(path + ".feature[" + i + "].tag", features.tagAt(i));
				value(path + ".feature[" + i + "].value", features.valueAt(i));
			}
			final var policy = font.getPolicy();
			value(path + ".policies", policy.getLength());
			for (int i = 0; i < policy.getLength(); ++i) value(path + ".policy[" + i + "]", policy.get(i));
		}

		private void text(final String path, final Text text) throws Exception {
			value(path + ".chars", new String(text.getChars(), 0, text.getCharCount()));
			value(path + ".charOffset", text.getCharOffset());
			value(path + ".style", text.getFontStyle());
			value(path + ".ascent", text.getAscent());
			value(path + ".descent", text.getDescent());
			value(path + ".advance", text.getAdvance());
			value(path + ".letterSpacing", text.getLetterSpacing());
			final var metrics = text.getFontMetrics();
			final var source = metrics.getFontSource();
			value(path + ".font.type", source.getClass().getName());
			value(path + ".font.name", source.getFontName());
			value(path + ".font.aliases", source.getAliases());
			value(path + ".font.direction", source.getDirection());
			value(path + ".font.size", metrics.getFontSize());
			value(path + ".font.ascent", metrics.getAscent());
			value(path + ".font.descent", metrics.getDescent());
			value(path + ".font.xHeight", metrics.getXHeight());
			value(path + ".font.spaceAdvance", metrics.getSpaceAdvance());
			final int count = text.getGlyphCount();
			value(path + ".glyphCount", count);
			double pen = 0;
			for (int i = 0; i < count; ++i) {
				final int gid = text.getGlyphIds()[i];
				final String p = path + ".glyph[" + i + "]";
				final double kerning = i == 0 ? 0.0 : metrics.getKerning(text.getGlyphIds()[i - 1], gid);
				final double extra = text.xAdvances() == null ? 0.0 : text.xAdvances().get(i);
				pen += (i == 0 ? 0.0 : metrics.getAdvance(text.getGlyphIds()[i - 1]) + text.getLetterSpacing() - kerning) + extra;
				value(p + ".inlinePen", pen);
				value(p + ".id", gid);
				value(p + ".clusterLength", text.getClusterLengths()[i]);
				value(p + ".advance", metrics.getAdvance(gid));
				value(p + ".width", metrics.getWidth(gid));
				value(p + ".advanceAdjustment", metrics.getAdvanceAdjustment(gid));
				value(p + ".placementAdjustment", metrics.getPlacementAdjustment(gid));
				value(p + ".kerning", kerning);
				value(p + ".extraAdvance", extra);
			}
		}

		private void image(final String path, final Image image) throws Exception {
			value(path + ".type", image.getClass().getName());
			value(path + ".width", image.getWidth());
			value(path + ".height", image.getHeight());
			value(path + ".intrinsic", image.getIntrinsic());
			value(path + ".alt", image.getAltString());
			if (image instanceof net.zamasoft.foliojet.ua.impl.pdf.PixelBackedImage pixels) {
				final Image decoded = pixels.getPixels();
				if (decoded != null) {
					value(path + ".pixels", decoded);
				} else {
					// JPEG2000 等、PDF が扱えても ImageIO の復号器がない形式。
					// 資源名/URI は識別子にせず、元の符号化バイトを streaming SHA-256 にする。
					require(pixels.getSourceURI() != null && this.userAgent != null,
							"D7: PDF 画像の元資源を取得できない " + image.getClass().getName());
					String hash = this.imageDigests.get(image);
					if (hash == null) {
						final var source = this.userAgent.resolve(pixels.getSourceURI());
						try {
							final var digest = MessageDigest.getInstance("SHA-256");
							try (final var in = new java.security.DigestInputStream(source.getInputStream(), digest)) {
								in.transferTo(OutputStream.nullOutputStream());
							}
							hash = HexFormat.of().formatHex(digest.digest());
							this.imageDigests.put(image, hash);
						} finally {
							this.userAgent.release(source);
						}
					}
					value(path + ".sourceSha256", hash);
				}
			} else if (image.getClass() == net.zamasoft.pdfg2d.pdf.gc.PDFImage.class) {
				// 元資源の付かない PDFImage は、形式+冒頭の固有寸法で識別する。
				// PDF の登録順で変わる name は含めない。通常の読込画像は上の内容 hash を使う。
				value(path + ".format", "PDF-image-XObject");
			} else if (image instanceof net.zamasoft.pdfg2d.gc.image.util.TransformedImage transformed) {
				value(path + ".transform", transformed.getTransform());
				value(path + ".image", transformed.getImage());
			} else if (image instanceof net.zamasoft.foliojet.layout.part.CenteredImage centered) {
				value(path + ".transform", get(centered, "at"));
				value(path + ".image", centered.getImage());
			} else if (image.getClass() == net.zamasoft.foliojet.css.impl.part.UnprintBrokenImage.class) {
				value(path + ".annotation", "Square/nonprinting");
				final var ua = (net.zamasoft.foliojet.ua.UserAgent) get(image, "ua");
				value(path + ".appearance", new net.zamasoft.foliojet.css.impl.part.BrokenImage(ua, image.getAltString()));
			} else if (image instanceof net.zamasoft.pdfg2d.g2d.image.RasterImage raster) {
				if (image instanceof net.zamasoft.foliojet.ua.impl.image.EncodedRasterImage encoded) {
					value(path + ".encodedSha256", sha256(encoded.getEncoded()));
					value(path + ".mediaType", encoded.getMediaType());
					value(path + ".extension", encoded.getExtension());
				}
				String hash = this.imageDigests.get(image);
				if (hash == null) {
					final var bitmap = raster.getImage();
					final var samples = bitmap.getRaster();
					final var digest = MessageDigest.getInstance("SHA-256");
					final var buffer = java.nio.ByteBuffer.allocate(Long.BYTES);
					for (int y = 0; y < bitmap.getHeight(); ++y) {
						for (int x = 0; x < bitmap.getWidth(); ++x) {
							for (int band = 0; band < samples.getNumBands(); ++band) {
								buffer.clear();
								buffer.putLong(Double.doubleToLongBits(samples.getSampleDouble(x, y, band)));
								digest.update(buffer.array());
							}
						}
					}
					hash = HexFormat.of().formatHex(digest.digest());
					this.imageDigests.put(image, hash);
				}
				final var model = raster.getImage().getColorModel();
				value(path + ".sampleSha256", hash);
				value(path + ".components", model.getComponentSize());
				value(path + ".transferType", model.getTransferType());
				value(path + ".alpha", model.hasAlpha());
				value(path + ".premultiplied", model.isAlphaPremultiplied());
				value(path + ".colorSpace", model.getColorSpace().getType());
				if (model.getColorSpace() instanceof java.awt.color.ICC_ColorSpace icc) {
					value(path + ".iccSha256", sha256(icc.getProfile().getData()));
				}
				if (model instanceof java.awt.image.IndexColorModel palette) {
					final int[] colors = new int[palette.getMapSize()];
					palette.getRGBs(colors);
					value(path + ".palette", colors);
				}
			} else if (Set.of(PDF + "svg.SVGImage", PDF + "gc.RecorderGC$RecorderImage",
					BOX + "impl.FootnoteLabelImage", "net.zamasoft.foliojet.objects.mathml.MathMLImage",
					"net.zamasoft.foliojet.objects.barcode.BarcodeImage",
					"net.zamasoft.foliojet.css.impl.part.AltTextImage",
					"net.zamasoft.foliojet.css.impl.part.BrokenImage",
					"net.zamasoft.foliojet.css.impl.part.NullImage",
					"net.zamasoft.foliojet.css.impl.part.CheckBoxImage",
					"net.zamasoft.foliojet.css.impl.part.RadioButtonImage",
					"net.zamasoft.foliojet.css.impl.part.SelectImage",
					"net.zamasoft.foliojet.css.impl.part.CircleImage",
					"net.zamasoft.foliojet.css.impl.part.DiscImage",
					"net.zamasoft.foliojet.css.impl.part.SquareImage").contains(image.getClass().getName())) {
				final var manager = image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage
						? (net.zamasoft.pdfg2d.gc.font.FontManager) get(image, "fontManager") : this.fontManager;
				if (image instanceof net.zamasoft.foliojet.layout.box.impl.FootnoteLabelImage) {
					checkFields(image.getClass(), LABEL_FIELDS);
					// footnoteId/fontManager は描画参照。採番済み文字・字形・位置は commands に射影。
					value(path + ".marker", get(image, "marker"));
					value(path + ".resolvedNumber", get(image, "resolvedNumber"));
				}
				final RecorderGC recorder = new RecorderGC(manager, true);
				image.drawTo(recorder);
				value(path + ".commands", recorder.getPage().commands());
			} else if (image.getClass().getName().equals("net.zamasoft.foliojet.ua.impl.pagedsvg.SourcedImage")) {
				value(path + ".image", get(image, "image"));
				value(path + ".companion", get(image, "companion"));
			} else if (isPluginType(image.getClass())) {
				pluginProperties(path + ".public", image);
				final RecorderGC recorder = new RecorderGC(this.fontManager, true);
				image.drawTo(recorder);
				value(path + ".commands", recorder.getPage().commands());
			} else {
				throw new IllegalArgumentException("D7: 未知の画像 " + image.getClass().getName());
			}
		}

		private static boolean isPluginType(final Class<?> type) {
			final String name = type.getName();
			return !type.isHidden() && !type.isSynthetic()
					&& (name.startsWith("net.zamasoft.foliojet.plugins.")
							|| !(name.startsWith("net.zamasoft.foliojet.") || name.startsWith(PDF)
									|| name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")));
		}

		private void pluginProperties(final String path, final Object object) throws Exception {
			final Class<?> type = object.getClass();
			value(path + ".type", type.getName());
			// フィールド/getter/record 成分を区別し、同名の継承フィールドも隠さない。
			final Map<String, java.lang.reflect.AccessibleObject> properties = new TreeMap<>();
			for (final Field field : type.getFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
					properties.put("field:" + field.getDeclaringClass().getName() + "." + field.getName(), field);
				}
			}
			for (final Method method : type.getMethods()) {
				if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
						|| method.getParameterCount() != 0 || method.getReturnType() == void.class
						|| method.getDeclaringClass() == Object.class) continue;
				final String name = method.getName();
				if ((name.startsWith("get") && name.length() > 3)
						|| (name.startsWith("is") && name.length() > 2
								&& (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class))) {
					properties.put("getter:" + name, method);
				}
			}
			if (type.isRecord()) {
				for (final var component : type.getRecordComponents()) properties.put("record:" + component.getName(), component.getAccessor());
			}
			require(!properties.isEmpty(), "D7: plugin の公開属性がない " + type.getName());
			value(path + ".propertyCount", properties.size());
			++this.pluginDepth;
			try {
				for (final var entry : properties.entrySet()) {
					final String p = path + "[" + quote(entry.getKey()) + "]";
					final var member = entry.getValue();
					require(member.trySetAccessible(), "D7: plugin の公開属性を読めない " + p);
					try {
						value(p, member instanceof Field field ? field.get(object) : ((Method) member).invoke(object));
					} catch (final Exception e) {
						throw new IllegalArgumentException("D7: plugin の公開属性の直列化失敗 " + p, e);
					}
				}
			} finally {
				--this.pluginDepth;
			}
		}

		private void line(final String path, final String value) {
			this.out.append(path).append('=').append(value).append('\n');
		}

		private static String quote(final String text) {
			final StringBuilder quoted = new StringBuilder("\"");
			for (int i = 0; i < text.length(); ++i) {
				final char c = text.charAt(i);
				if (c == '"' || c == '\\') quoted.append('\\').append(c);
				else if (c < 0x20 || Character.isSurrogate(c)) quoted.append("\\u").append(HexFormat.of().toHexDigits(c));
				else quoted.append(c);
			}
			return quoted.append('"').toString();
		}

		private static Object get(final Object object, final String name) {
			for (final Field field : FIELDS.get(object.getClass())) {
				if (!field.getName().equals(name)) continue;
				try { return field.get(object); }
				catch (final IllegalAccessException e) { throw new IllegalArgumentException("D7: 属性を読めない " + field, e); }
			}
			throw new IllegalArgumentException("D7: 属性がない " + object.getClass().getName() + "." + name);
		}
	}

	private static Rendered render(final CorpusInput document) throws Exception {
		final Path dir = Files.createTempDirectory("foliojet-t4a-");
		boolean complete = false;
		// 観測口は static volatile。DirectSession が例外を警告へ変換しても、
		// 直列化の失敗を変換完了後に必ず検出する。
		final PageCapture capture = new PageCapture(dir);
		try (final var observation = DisplayListDumper.observePages(capture::accept)) {
			transcode(document);
			if (capture.failure != null) throw new IllegalArgumentException("D7 直列化失敗: " + document.source(), capture.failure);
			final Map<Integer, Path> pages = new TreeMap<>();
			try (final var paths = Files.list(dir)) {
				for (final Path path : paths.toList()) {
					final String name = path.getFileName().toString();
					require(name.matches("page-[0-9]+\\.txt"), "不明な表示リスト: " + path);
					final int page = Integer.parseInt(name.substring(5, name.length() - 4));
					require(pages.putIfAbsent(page, path) == null, "表示リストの頁重複: " + page);
				}
			}
			require(!pages.isEmpty(), document.source() + ": 表示リストがない");
			int expectedPage = 0;
			for (final int page : pages.keySet()) require(page == ++expectedPage, "表示リストの頁が不連続: " + pages.keySet());
			complete = true;
			return new Rendered(dir, List.copyOf(pages.values()));
		} finally {
			if (!complete) deleteDump(dir);
		}
	}

	/** 固定manifestの変換条件。census・所有不変条件試験も同じ入口を使う。 */
	static Map<String, CorpusInput> fixedManifest() throws IOException {
		final Table table = readTable(DATA_DIR.resolve("manifest.tsv"));
		require(!table.bootstrap(), "固定manifestが未作成");
		return readManifest(table);
	}

	static void transcode(final CorpusInput document) throws Exception {
		try (final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null)) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(OutputStream.nullOutputStream())));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("processing.pass-count", Integer.toString(document.passCount()));
			for (final var property : new TreeMap<>(document.properties()).entrySet()) {
				session.property(property.getKey(), conversionProperty(property.getKey(), property.getValue()));
			}
			CTISessionHelper.transcodeFile(session, Path.of(document.source()).toFile(), document.mimeType(), null);
		}
	}

	/** D7の相対default-stylesheetはリポジトリ基準。output.*も他の値もそのまま渡す。 */
	private static String conversionProperty(final String key, final String value) {
		if (key.equals("input.default-stylesheet") && !URI.create(value).isAbsolute()) {
			return Path.of(value).toUri().toString();
		}
		return value;
	}

	public void testPrettyManifestConversionCondition() throws Exception {
		final CorpusInput pretty = corpusDocuments().get("3200-line-breaker/parity-float.html@pretty");
		assertNotNull(pretty);
		final String stylesheet = pretty.properties().get("input.default-stylesheet");
		assertEquals(URI.create(jp.cssj.test.unit.TextWrapStyleOptIn.PRETTY_STYLESHEET),
				URI.create(conversionProperty("input.default-stylesheet", stylesheet)));
		assertEquals("a=b", conversionProperty("output.pdf.meta.title", "a=b"));
	}

	private static final class PageCapture {
		private final Path directory;
		private volatile Throwable failure;

		PageCapture(final Path directory) { this.directory = directory; }

		void accept(final Drawer drawer, final int page) {
			if (this.failure != null) return;
			try {
				// 画像の内容キャッシュは頁内だけ。頁間の内容変化や画像の保持延長を避ける。
				final byte[] bytes = new DigestSerializer(new IdentityHashMap<>()).page(drawer);
				Files.write(this.directory.resolve(String.format(Locale.ROOT, "page-%04d.txt", page)), bytes,
						java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
			} catch (final Exception | AssertionError e) {
				this.failure = e;
			}
		}
	}

	private static void deleteDump(final Path directory) throws IOException {
		try (final var paths = Files.list(directory)) {
			for (final Path path : paths.toList()) Files.delete(path);
		}
		Files.delete(directory);
	}

	private static Table readTable(final Path path) throws IOException {
		try {
			return parseTable(Files.readAllLines(path, StandardCharsets.UTF_8));
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
		}
	}

	private static Table parseTable(final List<String> lines) {
		final List<String[]> rows = new ArrayList<>();
		boolean bootstrap = false;
		for (final String line : lines) {
			if (line.equals(BOOTSTRAP)) bootstrap = true;
			if (line.isBlank() || line.startsWith("#")) continue;
			rows.add(Arrays.stream(line.split("\t", -1)).map(TwoPassDigestParityTest::unescape).toArray(String[]::new));
		}
		require(!bootstrap || rows.isEmpty(), "bootstrap とデータ行は併記不可");
		return new Table(bootstrap, rows);
	}

	private static Map<String, CorpusInput> readManifest(final Table table) {
		final Map<String, CorpusInput> inputs = new TreeMap<>();
		for (final String[] row : table.rows()) {
			require(row.length >= 6, "manifest は4固定列と input.include/input.property-pi が必要");
			require(!row[0].isBlank() && !row[1].isBlank() && !Path.of(row[1]).isAbsolute()
					&& !row[1].matches("^[A-Za-z]:.*") && !row[1].contains("\\"), "相対 sourcePath が必要: " + row[0]);
			final int passCount = positive(row[2]);
			require(Set.of("text/html", "application/xhtml+xml", "text/xml").contains(row[3]), "不明な MIME: " + row[3]);
			final Map<String, String> properties = new TreeMap<>();
			for (int i = 4; i < row.length; ++i) {
				final int eq = row[i].indexOf('=');
				require(eq > 0 && isProperty(row[i].substring(0, eq)), "不明な変換 property: " + row[i]);
				require(properties.putIfAbsent(row[i].substring(0, eq), row[i].substring(eq + 1)) == null, "property 重複: " + row[i]);
			}
			require(properties.containsKey("input.include") && properties.containsKey("input.property-pi"), "PI/入力許可の条件がない: " + row[0]);
			require(inputs.putIfAbsent(row[0], new CorpusInput(row[1], passCount, row[3], Map.copyOf(properties))) == null,
					"manifest 文書重複: " + row[0]);
		}
		require(table.bootstrap() || !inputs.isEmpty(), "manifest が空(初回は bootstrap マーカーが必要)");
		return inputs;
	}

	private static Map<PageKey, String> readDigests(final Table table) {
		final Map<PageKey, String> digests = new TreeMap<>(PAGE_ORDER);
		for (final String[] row : table.rows()) {
			require(row.length == 3, "digests は3列");
			final PageKey key = pageKey(row);
			require(digests.putIfAbsent(key, digest(row[2])) == null, "digest 重複: " + key);
		}
		String previous = null;
		int page = 0;
		for (final PageKey key : digests.keySet()) {
			if (!key.doc().equals(previous)) page = 0;
			require(key.page() == ++page, "digest の頁が不連続: " + key);
			previous = key.doc();
		}
		require(table.bootstrap() || !digests.isEmpty(), "digests が空(初回は bootstrap マーカーが必要)");
		return digests;
	}

	private static Map<PageKey, ExceptionEntry> readExceptions(final Table table) {
		require(!table.bootstrap(), "例外台帳は自動生成しない");
		final Map<PageKey, ExceptionEntry> exceptions = new TreeMap<>(PAGE_ORDER);
		for (final String[] row : table.rows()) {
			require(row.length == 5, "exceptions は5列");
			final PageKey key = pageKey(row);
			final ExceptionEntry entry = new ExceptionEntry(Reason.valueOf(row[2]), digest(row[3]), digest(row[4]));
			require(!entry.legacy().equals(entry.range()), "一致する digest の例外登録: " + key);
			require(exceptions.putIfAbsent(key, entry) == null, "例外重複: " + key);
		}
		return exceptions;
	}

	private static PageKey pageKey(final String[] row) {
		require(!row[0].isBlank(), "文書名が空");
		return new PageKey(row[0], positive(row[1]));
	}

	private static int positive(final String value) {
		final int number = Integer.parseInt(value);
		require(number > 0, "正の整数が必要: " + value);
		return number;
	}

	private static String digest(final String value) {
		require(value.matches("[0-9a-f]{64}"), "SHA-256 は小文字の16進64桁: " + value);
		return value;
	}

	private static String sha256(final byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void writeManifest(final Map<String, CorpusInput> manifest) throws IOException {
		final StringBuilder text = new StringBuilder("# TwoPass D7 manifest v1; UTF-8, escaped TSV\n" + MANIFEST_HEADER + "\n");
		text.append("# input.property-pi を保持し、元文書の PI を両経路で評価する。候補生成: -Dfoliojet.twopassDigest=regenerate\n");
		text.append("# build/reports/twopass-digest/{manifest,digests}.candidate.tsv をレビューし、例外台帳とともに手動反映する。\n");
		manifest.forEach((doc, input) -> text.append(manifestLine(doc, input)).append('\n'));
		Files.writeString(REPORT_DIR.resolve("manifest.candidate.tsv"), text, StandardCharsets.UTF_8);
	}

	private static String manifestLine(final String doc, final CorpusInput input) {
		final List<String> cells = new ArrayList<>(List.of(doc, input.source(), Integer.toString(input.passCount()), input.mimeType()));
		new TreeMap<>(input.properties()).forEach((key, value) -> cells.add(key + "=" + value));
		return tsv(cells.toArray(String[]::new));
	}

	private static void writeDigests(final Map<PageKey, String> digests) throws IOException {
		final StringBuilder text = new StringBuilder("# TwoPass D7 range digests v2; UTF-8, escaped TSV\n" + DIGEST_HEADER + "\n");
		text.append("# page は1起点・連続。頁数は文書ごとの行数。SHA-256 は D7-drawable-v1 の UTF-8/LF byte から計算。\n");
		text.append("# レビュー後に manifest/digests/例外台帳を同時に反映。legacySha256 は履歴証拠として凍結済み。\n");
		digests.forEach((key, digest) -> text.append(tsv(key.doc(), Integer.toString(key.page()), digest)).append('\n'));
		Files.writeString(REPORT_DIR.resolve("digests.candidate.tsv"), text, StandardCharsets.UTF_8);
	}

	private static String tsv(final String... cells) {
		return String.join("\t", Arrays.stream(cells).map(value -> value.replace("\\", "\\\\").replace("\t", "\\t")
				.replace("\r", "\\r").replace("\n", "\\n")).toList());
	}

	private static String unescape(final String value) {
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < value.length(); ++i) {
			final char ch = value.charAt(i);
			if (ch != '\\') { result.append(ch); continue; }
			require(++i < value.length(), "TSV の末尾に単独のバックスラッシュ");
			result.append(switch (value.charAt(i)) {
			case '\\' -> '\\';
			case 't' -> '\t';
			case 'r' -> '\r';
			case 'n' -> '\n';
			default -> throw new IllegalArgumentException("不明な TSV エスケープ: " + value);
			});
		}
		return result.toString();
	}

	/** 全件を回す前に、台帳の許容が完全一致の1頁に限られることを検査する。 */
	public void testExceptionRules() throws Exception {
		final String legacy = sha256("legacy".getBytes(StandardCharsets.UTF_8));
		final String range = sha256("range".getBytes(StandardCharsets.UTF_8));
		final String changed = sha256("changed".getBytes(StandardCharsets.UTF_8));

		for (final Reason reason : Reason.values()) {
			final String row = tsv("doc.html", "1", reason.name(), legacy, range);
			final Map<PageKey, ExceptionEntry> ledger = readExceptions(parseTable(List.of(row)));
			final ExceptionEntry entry = ledger.get(new PageKey("doc.html", 1));
			assertEquals(legacy, entry.legacy());
			assertEquals(range, entry.range());
			assertFalse(changed.equals(entry.range()));
			assertNull(ledger.get(new PageKey("doc.html", 2)));
			assertNull(ledger.get(new PageKey("other.html", 1)));

			assertInvalid(() -> readExceptions(parseTable(List.of(row, row))));
		}
		assertInvalid(() -> readExceptions(parseTable(List.of(tsv("doc.html", "1", "ABSOLUTE", legacy, range)))));
		assertInvalid(() -> readExceptions(parseTable(List.of(tsv("doc.html", "1", "LEGACY_STRUT_REUSE", range, range)))));
		assertInvalid(() -> readExceptions(parseTable(List.of(tsv("doc.html", "1", "LEGACY_STRUT_REUSE", "-", range)))));
	}

	public void testManifestConditionsAndRoundTrip() {
		final Map<String, CorpusInput> documents = new TreeMap<>();
		addImageTestEntry(documents, Path.of("files/visual"), "logical.html source=source.xhtml password=fixture-password"
				+ " input.default-stylesheet=style.css#fragment output.pdf.meta.title=a=b input.property-pi=false");
		addImageTestEntry(documents, Path.of("files/visual"), "skipped.html skip=true");
		assertEquals(1, documents.size());
		final CorpusInput input = documents.get("imageTest/logical.html");
		assertEquals("files/visual/source.xhtml", input.source());
		assertEquals("application/xhtml+xml", input.mimeType());
		assertEquals("false", input.properties().get("input.property-pi"));
		assertEquals("style.css#fragment", input.properties().get("input.default-stylesheet"));
		assertEquals("a=b", input.properties().get("output.pdf.meta.title"));
		assertFalse(input.properties().containsKey("password"));
		final CorpusInput multi = input(Path.of("files/unittest/has.html"), 2,
				Map.of("output.pdf.meta.title", "日本語\tA\nB\rC\\D=a"));
		assertEquals("true", multi.properties().get("input.property-pi"));
		assertEquals(multi, readManifest(parseTable(List.of(manifestLine("has.html", multi)))).get("has.html"));
		final Map<String, CorpusInput> saved = Map.of("kept", multi, "removed", multi);
		final Map<String, CorpusInput> current = Map.of("kept", input, "added", multi);
		final List<String> differences = manifestDiff(saved, current);
		assertEquals(3, differences.size());
		assertTrue(differences.contains("ADDED added"));
		assertTrue(differences.contains("REMOVED removed"));
		assertEquals(multi, saved.get("kept"));
		assertInvalid(() -> readManifest(parseTable(List.of(manifestLine("has.html", multi), manifestLine("has.html", multi)))));
	}

	public void testManifestRemovalAndStrictAdditions() {
		for (final String mode : List.of("check", "strict", "regenerate")) {
			final Report report = new Report();
			report.drift.addAll(List.of("REMOVED old.html", "ADDED new.html", "CONDITIONS changed.html"));
			checkManifestDrift(report, mode);
			assertEquals(mode.equals("regenerate") ? 0 : mode.equals("strict") ? 2 : 1, report.failures.size());
			assertEquals(0, report.converted);
		}
	}

	public void testDigestSerializationGeometryAndPaint() throws Exception {
		final var red = new net.zamasoft.foliojet.css.value.ColorValue(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(1, 0, 0));
		final var blue = new net.zamasoft.foliojet.css.value.ColorValue(net.zamasoft.pdfg2d.gc.paint.RGBColor.create(0, 0, 1));
		final var background = net.zamasoft.foliojet.layout.box.params.Background.create(red,
				(net.zamasoft.foliojet.layout.box.params.BackgroundImage) null,
				net.zamasoft.foliojet.layout.box.params.Background.BORDER_BOX);
		final var other = net.zamasoft.foliojet.layout.box.params.Background.create(blue,
				(net.zamasoft.foliojet.layout.box.params.BackgroundImage) null,
				net.zamasoft.foliojet.layout.box.params.Background.BORDER_BOX);
		final var first = new net.zamasoft.foliojet.layout.draw.BackgroundDrawable(null, null, 1f,
				new AffineTransform(), background, 100, 20);
		final var recolored = new net.zamasoft.foliojet.layout.draw.BackgroundDrawable(null, null, 1f,
				new AffineTransform(), other, 100, 20);
		assertEquals(first.describe(), recolored.describe());
		final String digest = drawingDigest(first, 10.001);
		assertFalse(digest.equals(drawingDigest(first, 10.002)));
		assertFalse(digest.equals(drawingDigest(recolored, 10.001)));
		final var triangle = new java.awt.geom.Path2D.Double();
		triangle.moveTo(0, 0);
		triangle.lineTo(100, 0);
		triangle.lineTo(100, 20);
		triangle.closePath();
		final var clipped = new net.zamasoft.foliojet.layout.draw.BackgroundDrawable(null, triangle, 1f,
				new AffineTransform(), background, 100, 20);
		final var rectangular = new net.zamasoft.foliojet.layout.draw.BackgroundDrawable(null,
				new java.awt.geom.Rectangle2D.Double(0, 0, 100, 20), 1f, new AffineTransform(), background, 100, 20);
		assertEquals(clipped.describeClip(), rectangular.describeClip());
		assertFalse(drawingDigest(clipped, 0).equals(drawingDigest(rectangular, 0)));
		final var transparent = new net.zamasoft.foliojet.layout.draw.BackgroundDrawable(null, null, .5f,
				new AffineTransform(), background, 100, 20);
		assertFalse(digest.equals(drawingDigest(transparent, 10.001)));
		final Locale saved = Locale.getDefault();
		try {
			Locale.setDefault(Locale.FRANCE);
			assertEquals(digest, drawingDigest(first, 10.001));
		} finally {
			Locale.setDefault(saved);
		}
		try {
			drawingDigest((gc, x, y) -> { }, 0);
			fail("未知の drawable を受理した");
		} catch (final IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("未知の drawable"));
		}
	}

	/** main の成果物を走査するため、試験用/plugin の実装は一覧へ混ざらない。 */
	public void testDigestDrawableSchemaCoverage() throws Exception {
		final Path root = Path.of(Drawable.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		final List<String> names;
		if (Files.isDirectory(root)) {
			try (final var paths = Files.walk(root)) {
				names = paths.filter(Files::isRegularFile).map(root::relativize).map(TwoPassDigestParityTest::portable)
						.filter(name -> name.endsWith(".class")).toList();
			}
		} else {
			try (final var jar = new java.util.jar.JarFile(root.toFile())) {
				names = jar.stream().map(java.util.jar.JarEntry::getName).filter(name -> name.endsWith(".class")).toList();
			}
		}
		final Set<String> found = new TreeSet<>();
		for (final String name : names) {
			// layout/draw だけでは box の内部クラス(脚注/表/文字)が抜けるので layout 全体を含める。
			if (!(name.startsWith("net/zamasoft/foliojet/layout/")
					|| name.startsWith("net/zamasoft/foliojet/ua/impl/pdf/"))) continue;
			final Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'),
					false, Drawable.class.getClassLoader());
			if (!type.isInterface() && Drawable.class.isAssignableFrom(type)) {
				found.add(type.getName());
				assertTrue("D7: 未知の drawable " + type.getName(), DigestSerializer.DRAWABLE_FIELDS.containsKey(type.getName()));
				DigestSerializer.checkDrawable(type);
			}
		}
		assertEquals("schema の削除漏れ/走査漏れ", new TreeSet<>(DigestSerializer.DRAWABLE_FIELDS.keySet()), found);
		for (final String name : DigestSerializer.LABEL_FIELDS.keySet()) {
			DigestSerializer.checkFields(Class.forName(name), DigestSerializer.LABEL_FIELDS);
		}
	}

	public void testConversionCauseChain() {
		final var detail = new IllegalArgumentException("D7: 未知の paint\nexample\tname");
		final var middle = new java.lang.reflect.InvocationTargetException(detail);
		final var outer = new IllegalArgumentException("D7 直列化失敗: files/example.html", middle);
		final String line = causeChain(outer);
		assertTrue(line.contains("D7 直列化失敗: files/example.html <- caused by java.lang.reflect.InvocationTargetException"));
		assertTrue(line.contains("D7: 未知の paint\\nexample\\tname"));
		assertEquals(1L, line.lines().count());
		detail.initCause(outer);
		assertTrue(causeChain(outer).endsWith("[cause cycle: java.lang.IllegalArgumentException]"));
	}

	public void testDigestFootnoteSeparatorAndArtifact() throws Exception {
		final var constructor = Class.forName(DigestSerializer.BOX + "impl.PageBox$FootnoteSeparatorDrawable")
				.getDeclaredConstructor(PageBox.class, java.awt.geom.Rectangle2D.Double.class);
		assertTrue(constructor.trySetAccessible());
		final Drawable horizontal = (Drawable) constructor.newInstance(null, new java.awt.geom.Rectangle2D.Double(0, 20, 30, .5));
		final Drawable vertical = (Drawable) constructor.newInstance(null, new java.awt.geom.Rectangle2D.Double(20, 0, .5, 30));
		assertFalse(drawingDigest(horizontal, 0).equals(drawingDigest(vertical, 0)));
		final Drawer drawer = new Drawer(0);
		drawer.artifactView().visitDrawable(horizontal, 0, 20);
		final String serialized = new String(new DigestSerializer(new IdentityHashMap<>()).page(drawer), StandardCharsets.UTF_8);
		assertTrue(serialized.contains("page.paint[0].artifact=Boolean:true"));
		assertTrue(serialized.contains("PageBox$FootnoteSeparatorDrawable"));
		assertTrue(serialized.contains("page.paint[0].rect.segments="));
	}

	public void testDigestGradientAttributes() throws Exception {
		for (final String name : DigestSerializer.GRADIENT_FIELDS.keySet()) {
			DigestSerializer.checkFields(Class.forName(name), DigestSerializer.GRADIENT_FIELDS);
		}
		final var colors = new net.zamasoft.pdfg2d.gc.paint.Color[] {
				net.zamasoft.pdfg2d.gc.paint.RGBColor.create(1, 0, 0), net.zamasoft.pdfg2d.gc.paint.RGBAColor.create(0, 0, 1, .5f) };
		final double[] fractions = { 0, 1 };
		final var stops = net.zamasoft.foliojet.css.value.css3.GradientStops.ofFractions(fractions, colors);
		final var length = net.zamasoft.foliojet.css.value.AbsoluteLengthValue.create(null, 12);
		final var half = net.zamasoft.foliojet.css.value.PercentageValue.HALF;
		final var explicit = net.zamasoft.foliojet.css.value.css3.RadialGradientValue.Size.EXPLICIT;
		final var linear = new net.zamasoft.foliojet.css.value.css3.LinearGradientValue(1, stops, true);
		final var radial = new net.zamasoft.foliojet.css.value.css3.RadialGradientValue(false, explicit, length, length, half, length, stops, true);
		final var conic = new net.zamasoft.foliojet.css.value.css3.ConicGradientValue(.5, length, half, stops, true);
		final var background = net.zamasoft.foliojet.layout.box.params.Background.create(null,
				new net.zamasoft.foliojet.layout.box.params.Background.Layer[] {
						new net.zamasoft.foliojet.layout.box.params.Background.PaintLayer(linear),
						new net.zamasoft.foliojet.layout.box.params.Background.PaintLayer(radial),
						new net.zamasoft.foliojet.layout.box.params.Background.PaintLayer(conic) },
				net.zamasoft.foliojet.layout.box.params.Background.BORDER_BOX);
		final String serialized = serializedValue(background);
		assertTrue(serialized.contains(".sizeX.pt=d:"));
		assertTrue(serialized.contains(".fromAngle=d:"));
		assertTrue(serialized.contains(".repeating=Boolean:true"));
		assertFalse(serialized.contains(".ua="));
		assertFalse(serializedValue(linear).equals(serializedValue(
				new net.zamasoft.foliojet.css.value.css3.LinearGradientValue(1.001, stops, true))));
		assertFalse(serializedValue(radial).equals(serializedValue(
				new net.zamasoft.foliojet.css.value.css3.RadialGradientValue(false, explicit, length,
						net.zamasoft.foliojet.css.value.AbsoluteLengthValue.create(null, 12.001), half, length, stops, true))));
		assertFalse(serializedValue(conic).equals(serializedValue(
				new net.zamasoft.foliojet.css.value.css3.ConicGradientValue(.5, length, half, stops, false))));
		final var spread = net.zamasoft.pdfg2d.gc.paint.SpreadMethod.REPEAT;
		for (final Object paint : List.of(
				new net.zamasoft.pdfg2d.gc.paint.LinearGradient(0, 0, 10, 20, fractions, colors, new AffineTransform(), spread),
				new net.zamasoft.pdfg2d.gc.paint.RadialGradient(1, 2, 10, 3, 4, fractions, colors, new AffineTransform(), spread),
				new net.zamasoft.pdfg2d.gc.paint.ConicGradient(1, 2, .5, fractions, colors, new AffineTransform(), spread))) {
			final String before = serializedValue(paint);
			fractions[1] = .999;
			assertFalse(before.equals(serializedValue(paint)));
			fractions[1] = 1;
			assertEquals(before, serializedValue(paint));
		}
	}

	private record PluginDrawable(double width, Map<String, Object> attributes) implements Drawable {
		@Override
		public void draw(final net.zamasoft.pdfg2d.gc.GC gc, final double x, final double y) {
			gc.fill(new java.awt.geom.Rectangle2D.Double(x, y, this.width, 10));
		}

		@Override
		public String toString() { throw new AssertionError("plugin.toString は使わない"); }
	}

	private static final class PluginBean {
		public int scale = 2;
		private Object detail;
		public Object getDetail() { return this.detail; }
	}

	public void testDigestPluginPublicPropertiesAndDrawing() throws Exception {
		final Map<String, Object> first = new LinkedHashMap<>(), second = new LinkedHashMap<>();
		first.put("a", List.of(1, "日本語")); first.put("b", new PluginBean());
		second.put("b", new PluginBean()); second.put("a", List.of(1, "日本語"));
		final String digest = drawingDigest(new PluginDrawable(10, first), 0);
		assertEquals(digest, drawingDigest(new PluginDrawable(10, second), 0));
		((PluginBean) second.get("b")).scale = 3;
		assertFalse(digest.equals(drawingDigest(new PluginDrawable(10, second), 0)));
		assertFalse(digest.equals(drawingDigest(new PluginDrawable(10.001, first), 0)));
		final Image image = new net.zamasoft.foliojet.plugins.test.TestInlineObjectFactory().createInlineObject().getImage(null);
		final String drawing = serializedValue(image);
		assertEquals(drawing, serializedValue(new net.zamasoft.foliojet.plugins.test.TestInlineObjectFactory().createInlineObject().getImage(null)));
		assertTrue(drawing.contains(".commands=list["));
		assertTrue(drawing.contains("TestInlineObject$1"));
		assertTrue(drawing.contains("getter:getWidth"));
		for (final Object invalid : List.of(new Object(), new StringBuilder("identity"), first)) {
			((PluginBean) first.get("b")).detail = invalid;
			try {
				drawingDigest(new PluginDrawable(10, first), 0);
				fail("未知属性/循環を受理した");
			} catch (final IllegalArgumentException expected) {
				assertTrue(causeChain(expected).contains("D7:"));
			}
		}
	}

	private static String serializedValue(final Object value) throws Exception {
		final DigestSerializer serializer = new DigestSerializer(new IdentityHashMap<>());
		serializer.value("value", value);
		return serializer.out.toString();
	}

	public void testDigestEncodedAndUndecodableImages() throws Exception {
		final var first = new net.zamasoft.pdfg2d.pdf.gc.PDFImage("I0", 12, 34);
		assertEquals(serializedValue(first), serializedValue(new net.zamasoft.pdfg2d.pdf.gc.PDFImage("I99", 12, 34)));
		assertFalse(serializedValue(first).equals(serializedValue(new net.zamasoft.pdfg2d.pdf.gc.PDFImage("I0", 13, 34))));
		final byte[] encoded = { 0, 1, 2, 3 };
		final var bitmap = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
		final var raster = new net.zamasoft.foliojet.ua.impl.image.EncodedRasterImage(bitmap, encoded, "image/jp2", "jp2");
		final String before = serializedValue(raster);
		encoded[3] = 4;
		assertFalse(before.equals(serializedValue(raster)));
		final URI uri = URI.create("test:image.jp2");
		final var released = new java.util.concurrent.atomic.AtomicInteger();
		final var ua = (net.zamasoft.foliojet.ua.UserAgent) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { net.zamasoft.foliojet.ua.UserAgent.class }, (proxy, method, args) -> {
					if (method.getName().equals("resolve")) {
						return new net.zamasoft.zstream.resolver.protocol.stream.StreamSource(uri,
								new java.io.ByteArrayInputStream(encoded), "image/jp2", null, encoded.length);
					}
					if (method.getName().equals("release")) { released.incrementAndGet(); return null; }
					throw new UnsupportedOperationException(method.getName());
				});
		final var pixels = new net.zamasoft.foliojet.ua.impl.pdf.PixelBackedImage(first, () -> null, uri);
		final DigestSerializer serializer = new DigestSerializer(new IdentityHashMap<>());
		serializer.userAgent = ua;
		serializer.value("image", pixels);
		assertTrue(serializer.out.toString().contains(".sourceSha256=\"" + sha256(encoded) + "\""));
		assertEquals(1, released.get());
		encoded[3] = 5;
		final DigestSerializer changed = new DigestSerializer(new IdentityHashMap<>());
		changed.userAgent = ua;
		changed.value("image", pixels);
		assertFalse(serializer.out.toString().equals(changed.out.toString()));
		assertEquals(2, released.get());
		try {
			serializedValue(new net.zamasoft.foliojet.ua.impl.pdf.PixelBackedImage(first, () -> null));
			fail("復号画素も元資源もない画像を受理した");
		} catch (final IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("PDF 画像の元資源を取得できない"));
		}
	}

	private static String drawingDigest(final Drawable drawable, final double x) throws Exception {
		final Drawer drawer = new Drawer(0);
		drawer.visitDrawable(drawable, x, 0);
		return sha256(new DigestSerializer(new IdentityHashMap<>()).page(drawer));
	}

	public void testDigestImageContentAndStroke() throws Exception {
		final var bitmap = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		final var image = new net.zamasoft.pdfg2d.g2d.image.RasterImageImpl(bitmap);
		bitmap.setRGB(0, 0, 0xffff0000);
		final DigestSerializer first = new DigestSerializer(new IdentityHashMap<>());
		first.value("image", image);
		bitmap.setRGB(0, 0, 0xff0000ff);
		final DigestSerializer second = new DigestSerializer(new IdentityHashMap<>());
		second.value("image", image);
		assertFalse(first.out.toString().equals(second.out.toString()));
		final RecorderGC gc = new RecorderGC(null, true);
		gc.setLinePattern(new double[] { 1, 2 });
		gc.draw(new java.awt.geom.Line2D.Double(0, 0, 10, 10));
		final DigestSerializer dashed = new DigestSerializer(new IdentityHashMap<>());
		dashed.value("commands", gc.getPage().commands());
		final RecorderGC other = new RecorderGC(null, true);
		other.setLinePattern(new double[] { 1, 2.001 });
		other.draw(new java.awt.geom.Line2D.Double(0, 0, 10, 10));
		final DigestSerializer changed = new DigestSerializer(new IdentityHashMap<>());
		changed.value("commands", other.getPage().commands());
		assertFalse(dashed.out.toString().equals(changed.out.toString()));
	}

	public void testDigestKeepsLaterPaintInLogicalLine() throws Exception {
		final var params = new net.zamasoft.foliojet.layout.box.params.BlockParams();
		params.fontManager = (net.zamasoft.pdfg2d.gc.font.FontManager) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { net.zamasoft.pdfg2d.gc.font.FontManager.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getFontListMetrics")) {
						return new net.zamasoft.pdfg2d.gc.font.FontListMetrics(new net.zamasoft.pdfg2d.gc.font.FontMetrics[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final var constructor = Class.forName(DigestSerializer.BOX + "AbstractTextBox$TextSequenceDrawable")
				.getDeclaredConstructor(PageBox.class, Shape.class, AffineTransform.class, List.class,
						int.class, int.class, AbstractTextParams.class, double.class, double.class,
						LogicalLineEmission.class, String.class, BidiSlice[].class);
		assertTrue(constructor.trySetAccessible());
		final Drawable text = (Drawable) constructor.newInstance(null, null, new AffineTransform(), List.of(),
				0, 0, params, 10.0, 2.0, new LogicalLineEmission(9876, "same line"), "same line", null);
		final Drawer first = new Drawer(0), second = new Drawer(0);
		first.visitDrawable(text, 0, 0);
		second.visitDrawable(text, 0, 0);
		first.visitDrawable(text, 10.001, 0);
		second.visitDrawable(text, 10.002, 0);
		final StringBuilder oldFirst = new StringBuilder(), oldSecond = new StringBuilder();
		first.dump(oldFirst, "");
		second.dump(oldSecond, "");
		assertEquals(oldFirst.toString(), oldSecond.toString());
		assertFalse(Arrays.equals(new DigestSerializer(new IdentityHashMap<>()).page(first),
				new DigestSerializer(new IdentityHashMap<>()).page(second)));
	}

	public void testDigestObserverCrossThreadAndRestore() throws Exception {
		final var observed = new java.util.concurrent.atomic.AtomicInteger();
		try (final var outer = DisplayListDumper.observePages((drawer, page) -> observed.addAndGet(page))) {
			try (final var inner = DisplayListDumper.observePages((drawer, page) -> observed.addAndGet(10 * page))) {
				final Thread thread = new Thread(() -> DisplayListDumper.dumpPage(new Drawer(0), 1));
				thread.start();
				thread.join();
			}
			DisplayListDumper.dumpPage(new Drawer(0), 2);
			assertEquals(12, observed.get());
		}
	}

	/** 空/途中までの基準を初回扱いして退行を隠さない。bootstrap も候補の手動反映が必要。 */
	public void testDigestTableValidation() throws Exception {
		final String hash = sha256(new byte[0]);
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
		final String first = tsv("doc.html", "1", hash), second = tsv("doc.html", "2", hash);
		assertEquals(2, readDigests(parseTable(List.of(first, second))).size());
		assertTrue(readDigests(parseTable(List.of(BOOTSTRAP))).isEmpty());
		assertInvalid(() -> readDigests(parseTable(List.of(DIGEST_HEADER))));
		assertInvalid(() -> readManifest(parseTable(List.of(MANIFEST_HEADER))));
		assertInvalid(() -> readDigests(parseTable(List.of(first, first))));
		assertInvalid(() -> readDigests(parseTable(List.of(second))));
		assertInvalid(() -> readDigests(parseTable(List.of(first, tsv("doc.html", "3", hash)))));
		assertInvalid(() -> readDigests(parseTable(List.of(tsv("doc.html", "0", hash)))));
		assertInvalid(() -> readDigests(parseTable(List.of(tsv("doc.html", "1", "bad")))));
		assertInvalid(() -> parseTable(List.of(BOOTSTRAP, first)));
	}

	private static void assertInvalid(final Runnable action) {
		try {
			action.run();
			fail("不正な台帳/基準を受理した");
		} catch (final IllegalArgumentException expected) {
			// expected
		}
	}

	private static String summary(final Report report, final Map<PageKey, ExceptionEntry> exceptions,
			final int manifestSize, final String mode, final long started) {
		final StringBuilder md = new StringBuilder("# TwoPass range digest (D7)\n\n");
		md.append("mode: ").append(mode).append(" / elapsed: ").append(elapsed(started)).append("\n\n")
				.append("manifest: ").append(manifestSize).append(" 文書 / 変換・比較完了: ").append(report.converted)
				.append('/').append(report.candidates).append(" / 入力不在: ").append(report.missing)
				.append(" / 範囲側頁数: ").append(report.pages).append("\n\n")
				.append("履歴台帳の範囲側照合: ").append(report.acceptedPages)
				.append(" 頁 / 失敗: ").append(report.failures.size()).append("\n\n")
				.append("manifest 候補生成: ").append(report.manifestWritten).append(" / digests 候補生成: ").append(report.digestsWritten)
				.append("。基準と例外台帳は自動更新しない。\n\n")
				.append("| 理由 | 登録文書数 | 今回照合できた文書数 |\n|---|---:|---:|\n");
		for (final Reason reason : Reason.values()) {
			final long registered = exceptions.entrySet().stream().filter(entry -> entry.getValue().reason() == reason)
					.map(entry -> entry.getKey().doc()).distinct().count();
			final int accepted = report.accepted.getOrDefault(reason, Set.of()).size();
			md.append("| ").append(reason).append(" | ").append(registered).append(" | ").append(accepted).append(" |\n");
			System.err.println("[D7] " + reason + " registeredDocuments=" + registered + " matchedDocuments=" + accepted);
		}
		md.append("\n文書数は理由ごとに重複排除。同じ文書に複数理由があれば各理由に数える。頁数不一致は免除しない。\n")
				.append("\n通常実行は保存条件を使う。削除・入力不在は失敗、追加は通知(strict では失敗)。regenerate は候補だけを生成する。\n")
				.append("\nGradle の総時間 timeout は未設定。noProgressSeconds=120 は個々の変換の進捗停止検出。\n");
		if (!report.drift.isEmpty()) {
			md.append("\n## manifest 差分\n\n");
			report.drift.forEach(line -> md.append("- ").append(line).append('\n'));
		}
		if (!report.failures.isEmpty()) {
			md.append("\n## 失敗\n\n");
			report.failures.forEach(line -> md.append("- ").append(line).append('\n'));
		}
		return md.toString();
	}

	private static String elapsed(final long started) {
		return String.format(Locale.ROOT, "%.1fs", (System.nanoTime() - started) / 1_000_000_000.0);
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) throw new IllegalArgumentException(message);
	}
}
