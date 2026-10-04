package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * flex の匿名 item(容れ物の直下の地の文字)が、行の高さまで伸びても容れ物の align-content で中身を寄せないことを
 * 固定します(2026-10-04、全体レビュー。grid には 2026-08-29 に入っていた中立化が flex の写しに無かった)。
 */
public class FlexAnonymousItemAlignContentTest extends TestCase {
	private static double textY(final String layout) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:300pt 200pt;margin:10pt} body{margin:0;font-size:10pt;line-height:1}</style></head><body>"
				+ "<div style='" + layout + ";align-content:center;align-items:stretch'>anon"
				+ "<div style='height:80pt;width:50pt'>tall</div></div></body></html>";
		final Path dir = Files.createTempDirectory("flex-anon-align");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		final String dump = Files.readString(dir.resolve("page-0001.txt"));
		final Matcher m = Pattern.compile("y=([-0-9.]+) Text\\[\"anon\"").matcher(dump);
		assertTrue(dump, m.find());
		return Double.parseDouble(m.group(1));
	}

	public void testAnonymousItemContentStaysAtTheStart() throws Exception {
		// 伸びた 80pt の中央(約 35pt)ではなく、上端に置く
		assertTrue("flex", textY("display:flex") < 10);
		assertTrue("grid", textY("display:grid;grid-auto-flow:column") < 10);
	}
}
