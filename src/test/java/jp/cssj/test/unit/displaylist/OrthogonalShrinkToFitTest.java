package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 縦組みの shrink-to-fit の入れ物(float: bottom など)の中の、直交する(横組みの)表・段落の寸法を固定します
 * (2026-10-05、jigensha の報告)。
 *
 * <p>
 * 模倣計測は直交する子の行方向の寸法(縦組みの入れ物では高さ)を知らず、表の幅や 1 行ぶんで代わりにしていた。
 * そのうえ表の {@code margin: auto} が高さの決まる前に中央へ寄せられ、表が入れ物の中ほどから版面の下へはみ出した。
 * 今は入れ物を一度組んで直交する子の実寸を測り、寸法の決まらない表は寄せない。
 * </p>
 */
public class OrthogonalShrinkToFitTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** A5・余白 21mm の版面の高さ。 */
	private static final double CONTENT_HEIGHT = (210 - 42) * 72 / 25.4;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]$", Pattern.MULTILINE);

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	private static final String TABLE = """
			<table><thead><tr><th>x</th><th>5のx乗を23で割った余り</th><th>x</th><th>5のx乗を23で割った余り</th></tr></thead>
			<tbody><tr><td>1</td><td>5</td><td>12</td><td>18</td></tr><tr><td>2</td><td>2</td><td>13</td><td>21</td></tr>
			<tr><td>3</td><td>10</td><td>14</td><td>13</td></tr><tr><td>4</td><td>4</td><td>15</td><td>19</td></tr>
			<tr><td>5</td><td>20</td><td>16</td><td>3</td></tr><tr><td>6</td><td>8</td><td>17</td><td>15</td></tr>
			<tr><td>7</td><td>17</td><td>18</td><td>6</td></tr><tr><td>8</td><td>16</td><td>19</td><td>7</td></tr>
			<tr><td>9</td><td>11</td><td>20</td><td>12</td></tr><tr><td>10</td><td>9</td><td>21</td><td>14</td></tr>
			<tr><td>11</td><td>22</td><td>22</td><td>1</td></tr></tbody></table>""";

	public OrthogonalShrinkToFitTest(String name) {
		super(name);
	}

	private static String document(final String boxStyle, final String content) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page{size:148mm 210mm;margin:21mm 16.6mm 21mm 21mm}
				html{writing-mode:vertical-rl;font-size:10pt}
				body{margin:0}
				p{margin:0;line-height:1.8}
				.box{%s;margin:5mm 0 0 0;background:#eef}
				table{writing-mode:horizontal-tb;margin:auto;border-collapse:collapse}
				th,td{padding:1mm 3mm;text-align:center}
				.h{writing-mode:horizontal-tb;width:80mm}
				</style></head><body>
				<p>縦組みの本文です。</p><div class="box">%s</div><p>あとの本文。</p>
				</body></html>
				""".formatted(boxStyle, content);
	}

	/** jigensha の再現: float: bottom の入れ物(縦組みのまま)の中の横組みの表。入れ物は表の高さで、版面の下端に寄る。 */
	public void testTableInVerticalBottomFloat() throws Exception {
		assertFitsAtBottom(convert("table", document("float:bottom", TABLE)));
	}

	/** 横組みの段落(80mm 幅で数行に折り返す)。以前は 1 行ぶんの高さに見積もられて下へはみ出した。 */
	public void testParagraphInVerticalBottomFloat() throws Exception {
		assertFitsAtBottom(convert("paragraph", document("float:bottom",
				"<div class=\"h\"><p>" + "横組みの段落です。何行かに折り返す長さの文です。".repeat(3) + "</p></div>")));
	}

	/** 普通の流れの縦組みの入れ物では、横組みの表は従来どおり auto の余白で中央に寄る。 */
	public void testTableInNormalFlowStaysCentered() throws Exception {
		final String[] pages = convert("flow", document("display:block", TABLE));
		boolean found = false;
		for (final String page : pages) {
			final Matcher t = TEXT.matcher(page);
			while (t.find()) {
				if (t.group(3).equals("5のx乗を23で割った余り")) {
					// 中央なら表の上端は入れ物の上端から 100pt 以上下(始端に置くと 20pt ほど)
					assertTrue("表が中央に寄っていない: " + t.group(), Double.parseDouble(t.group(2)) > 100);
					found = true;
				}
			}
		}
		assertTrue("表が無い", found);
	}

	/** 背景つきの入れ物が版面の下端に接し、中の文字が全部その中にある。 */
	private static void assertFitsAtBottom(final String[] pages) {
		boolean found = false;
		for (final String page : pages) {
			final Matcher f = FRAME.matcher(page);
			while (f.find()) {
				final double top = Double.parseDouble(f.group(2));
				final double bottom = top + Double.parseDouble(f.group(4));
				if (Double.parseDouble(f.group(3)) > 400 || bottom < CONTENT_HEIGHT - 1) {
					continue;
				}
				found = true;
				assertEquals("入れ物の下端は版面の下端", CONTENT_HEIGHT, bottom, 0.5);
				final Matcher t = TEXT.matcher(page);
				while (t.find()) {
					final double y = Double.parseDouble(t.group(2));
					if (y >= top - 0.5) {
						final double end = y + Double.parseDouble(t.group(4)) + Double.parseDouble(t.group(5));
						assertTrue("入れ物の下へはみ出す字: " + t.group() + " 入れ物 [" + top + ", " + bottom + "]",
								end <= bottom + 0.5);
					}
				}
			}
		}
		assertTrue("版面の下端に接する入れ物が無い", found);
	}

	/** 変換して、各頁の表示リストを頁順に返します。 */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/orthogonal-stf/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "orthogonal-stf-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
