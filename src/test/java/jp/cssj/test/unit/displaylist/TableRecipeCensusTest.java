package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSourceTestHooks;
import net.zamasoft.foliojet.layout.segment.BoxKind;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code files/unittest}全数のOpaque/recipe内訳センサスと、変換の
 * クラッシュ有無を実測する調査ハーネスです(G-1「表のrecipe記録化」、
 * 2026-07-25新設)。
 *
 * <p>
 * <b>既定では何もしません</b>——{@code -Dfoliojet.tableRecipe.census}を
 * 付けたときだけ全436文書を直列変換します(通常の{@code test}実行を
 * 数分遅くしないため)。{@code -Dfoliojet.tableRecipe=fixed|table}と
 * 組み合わせて、表のrecipe記録化のbefore/afterを同じ物差しで測ります。
 * </p>
 *
 * <pre>
 * # センサス(Opaque内訳・recipe内訳・TwoPassSealReject内訳・発火数)
 * ./gradlew test --tests "*.TableRecipeCensusTest" \
 *     -Dfoliojet.tableRecipe.census= -Dfoliojet.tableRecipe=table
 *
 * # フルコーパス表示リストparity(off/onは別JVM=別起動。static finalのため)
 * ./gradlew test --tests "*.TableRecipeCensusTest" -Dfoliojet.tableRecipe.census= \
 *     -Dfoliojet.tableRecipe.parityDir=local/parity-off
 * ./gradlew test --tests "*.TableRecipeCensusTest" -Dfoliojet.tableRecipe.census= \
 *     -Dfoliojet.tableRecipe=table -Dfoliojet.tableRecipe.parityDir=local/parity-table
 * diff -r local/parity-off local/parity-table
 * </pre>
 *
 * <p>
 * <b>撤去条件</b>: G-2(TwoPassの{@code NESTED_BUILDER}制約の解消、
 * および表replay消費者=
 * {@code FlowContainer.restyleItem}の{@code case TABLE}の設計)の計測で
 * 再利用するために残している。表replay消費者を作らないと決めた時点で、
 * {@code StyleBuilder}の{@code tableRecipe}関連一式・
 * {@code LayoutSource.containsTable}のゲート群と<b>同時に</b>撤去すること
 * (放置された移行足場は患部になる——E-3の教訓)。
 * </p>
 */
public class TableRecipeCensusTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 「表が丸ごと次ページへ移動する」文書を生成して変換し、表の
	 * ソース再生({@code BoxRecipeBoxFactory.TABLE_REPLAYS})が実際に
	 * 発火するかを実測します(空虚な緑の検出)。表示リストは
	 * {@code -Dfoliojet.tableRecipe.parityDir}配下へ出すので、off/onを
	 * 別JVMで走らせてディレクトリdiffするとparityが取れます。
	 */
	public void testMovedTableReplay() throws Exception {
		if (System.getProperty("foliojet.tableRecipe.census") == null) {
			return;
		}
		final File dir = new File("local/unittest/table-recipe-moved");
		dir.mkdirs();
		final String[] layouts = { "fixed", "auto" };
		final String[] widths = { "60pt", "auto" };
		// wrap=true は表を page-break-inside:avoid の div で包む。restyle
		// worklist の case BLOCK だけが replayFromSource を試すため、
		// 表単体(case TABLE = addBound 直行)では再生が起きない
		final boolean[] wraps = { false, true };
		final StringBuilder report = new StringBuilder("[G-1 moved-table replay] mode=")
				.append(System.getProperty("foliojet.tableRecipe", "off")).append('\n');
		for (final String layout : layouts) {
			for (final String width : widths) {
			for (final boolean wrap : wraps) {
			for (final int fillers : new int[] { 1, 2, 3, 4 }) {
				final StringBuilder html = new StringBuilder();
				html.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">"
						+ "<?jp.cssj.property name=\"output.page-width\" value=\"150pt\"?>"
						+ "<?jp.cssj.property name=\"output.page-height\" value=\"80pt\"?>"
						+ "<html><head><meta http-equiv='Content-Type' "
						+ "content='text/html; charset=UTF-8'><style>@page{margin:0;}"
						+ "body{font:normal 10pt/1 serif;margin:0;}p{margin:0;}"
						+ "table{table-layout:").append(layout).append(";width:").append(width)
						.append(";border-collapse:collapse;margin:0;}"
						+ "td{border:1pt solid Black;padding:1pt;}</style></head><body>");
				for (int i = 0; i < fillers; ++i) {
					html.append("<p>filler line ").append(i).append(" xxxxxxxxxxxxxxxxxxxx</p>");
				}
				if (wrap) {
					html.append("<div style='page-break-inside:avoid;margin:0'>");
				}
				html.append("<table><tr><td>a1</td><td>b1</td></tr>"
						+ "<tr><td>a2</td><td>b2</td></tr><tr><td>a3</td><td>b3</td></tr></table>");
				if (wrap) {
					html.append("</div>");
				}
				html.append("<p>after the table</p></body></html>");
				final String name = "moved-table-" + layout + "-w" + width + (wrap ? "-wrap" : "-bare") + "-"
						+ fillers;
				final File src = new File(dir, name + ".html");
				Files.writeString(src.toPath(), html.toString(), java.nio.charset.StandardCharsets.UTF_8);
				final long before = net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.TABLE_REPLAYS.get();
				final String parityDir = System.getProperty("foliojet.tableRecipe.parityDir");
				if (parityDir != null) {
					final File dumpDir = new File(parityDir, name);
					dumpDir.mkdirs();
					for (final File old : dumpDir.listFiles()) {
						old.delete();
					}
					System.setProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY,
							dumpDir.getPath());
				}
				String result = "ok";
				try (OutputStream out = new FileOutputStream(new File(dir, name + ".pdf"))) {
					final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
					try {
						session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
						session.setMessageHandler(CTIMessageHelper
								.createStreamMessageHandler(new java.io.PrintStream(OutputStream.nullOutputStream())));
						session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
						session.property("input.include", "**");
						session.property("input.property-pi", "true");
						CTISessionHelper.transcodeFile(session, src, "text/html", null);
					} finally {
						session.close();
					}
				} catch (final Throwable t) {
					result = "EXCEPTION " + t;
					t.printStackTrace();
				} finally {
					System.clearProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY);
				}
				report.append("  ").append(name).append(": TABLE_REPLAYS+=")
						.append(net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.TABLE_REPLAYS.get() - before)
						.append(" SUBTREE_REPLAYS=").append(SourceReplayer.SUBTREE_REPLAYS.get()).append(' ')
						.append(result).append('\n');
			}
			}
			}
		}
		System.err.println(report);
	}

	public void testCensus() throws Exception {
		if (System.getProperty("foliojet.tableRecipe.census") == null) {
			return;
		}
		final List<Path> docs = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(new File("files/unittest").toPath())) {
			walk.filter(p -> p.getFileName().toString().endsWith(".html")).sorted(Comparator.naturalOrder())
					.forEach(docs::add);
		}
		final Map<BoxKind, long[]> recipeCounts = new EnumMap<>(BoxKind.class);
		for (final BoxKind k : BoxKind.values()) {
			recipeCounts.put(k, new long[1]);
		}
		final long[] opaque = { 0 };
		final long[] replaced = { 0 };
		final List<String> failures = new ArrayList<>();
		ContinuationStats.reset();
		final File outDir = new File("local/unittest/table-recipe-census");
		outDir.mkdirs();
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Opaque) {
				++opaque[0];
			} else if (event instanceof LayoutSource.Start(final BoxRecipe recipe)) {
				++recipeCounts.get(recipe.kind())[0];
			} else if (event instanceof LayoutSource.Replaced) {
				++replaced[0];
			}
		});
		int converted = 0;
		try {
			for (final Path doc : docs) {
				final File pdf = new File(outDir, "census.pdf");
				// 表示リストのフルコーパスparity用ダンプ(off/onを別JVMで実行し
				// ディレクトリごとdiffする)。-Dfoliojet.tableRecipe.parityDir=<dir>
				final String parityDir = System.getProperty("foliojet.tableRecipe.parityDir");
				if (parityDir != null) {
					final String name = new File("files/unittest").toPath().relativize(doc).toString()
							.replace('\\', '_').replace('/', '_').replace(".html", "");
					final File dumpDir = new File(parityDir, name);
					dumpDir.mkdirs();
					for (final File old : dumpDir.listFiles()) {
						old.delete();
					}
					System.setProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY,
							dumpDir.getPath());
				}
				try (OutputStream out = new FileOutputStream(pdf)) {
					final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
					try {
						session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
						session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(
								new java.io.PrintStream(OutputStream.nullOutputStream())));
						session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
						session.property("input.include", "**");
						session.property("input.property-pi", "true");
						CTISessionHelper.transcodeFile(session, doc.toFile(), "text/html", null);
						++converted;
					} finally {
						session.close();
					}
				} catch (final Throwable t) {
					failures.add(doc + " => " + t);
					t.printStackTrace();
					for (Throwable c = t.getCause(); c != null; c = c.getCause()) {
						System.err.println("--- caused by ---");
						c.printStackTrace();
					}
				} finally {
					System.clearProperty(net.zamasoft.foliojet.layout.draw.DisplayListDumper.DIR_PROPERTY);
				}
			}
		} finally {
			LayoutSourceTestHooks.setAppendObserver(null);
		}

		final StringBuilder s = new StringBuilder();
		s.append("[G-1 table recipe census] mode=").append(System.getProperty("foliojet.tableRecipe", "off"))
				.append(" docs=").append(docs.size()).append(" converted=").append(converted).append('\n');
		s.append("  Opaque=").append(opaque[0]).append(" Replaced=").append(replaced[0]).append('\n');
		for (final BoxKind k : BoxKind.values()) {
			final long v = recipeCounts.get(k)[0];
			if (v > 0) {
				s.append("  recipe:").append(k).append('=').append(v).append('\n');
			}
		}
		s.append("  SUBTREE_REPLAYS=").append(SourceReplayer.SUBTREE_REPLAYS.get()).append(" PREFIX_REPLAYS=")
				.append(SourceReplayer.PREFIX_REPLAYS.get()).append(" TEXT_TAIL_REPLAYS=")
				.append(SourceReplayer.TEXT_TAIL_REPLAYS.get()).append(" BALANCE_REPLAYS=")
				.append(SourceReplayer.BALANCE_REPLAYS.get()).append('\n');
		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get())
				.append(" RANGE_FIRST_BINDS=").append(ContinuationStats.RANGE_FIRST_BINDS.get())
				.append(" LEGACY_RECORD_BINDS=").append(ContinuationStats.LEGACY_RECORD_BINDS.get()).append('\n');
		for (final ContinuationStats.TwoPassSealReject r : ContinuationStats.TwoPassSealReject.values()) {
			s.append("  TwoPassSealReject.").append(r).append('=').append(ContinuationStats.twoPassSealRejects(r))
					.append('\n');
		}
		s.append("  TABLE_REPLAYS=").append(net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.TABLE_REPLAYS.get())
				.append('\n');
		s.append("  CELL_RANGE_SEALS=").append(ContinuationStats.CELL_RANGE_SEALS.get()).append(" CELL_RANGE_BINDS=")
				.append(ContinuationStats.CELL_RANGE_BINDS.get()).append('\n');
		s.append("  failures=").append(failures.size()).append('\n');
		for (final String f : failures) {
			s.append("    ").append(f).append('\n');
		}
		System.err.println(s);
		// 調査ハーネス: 失敗があってもレポートを出したうえで赤にする
		assertTrue(s.toString(), failures.isEmpty());
	}
}
