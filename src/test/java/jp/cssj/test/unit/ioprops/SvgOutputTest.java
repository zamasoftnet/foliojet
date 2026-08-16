package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** SVG出力、とくにラスター画像を含む文書の回帰テスト。 */
public class SvgOutputTest extends TestCase {
    static {
        System.setProperty("jp.cssj.copper.config",
                System.getProperty("jp.cssj.copper.config", "build/conf"));
        System.setProperty("jp.cssj.driver.default",
                System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
    }

    private static final URI COPPER_URI = URI.create("copper:direct:");

    /** PNGを含んでもBatikの画像エンコーダ不足で失敗せず、自己完結SVGになること。 */
    public void testRasterImageIsEmbedded() throws Exception {
        final String transparentPng =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body><img width=\"20\" height=\"20\" src=\"data:image/png;base64,"
                + transparentPng + "\"></body></html>";
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
        try {
            session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
            session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
            session.property("input.include", "**");
            session.property("output.type", "image/svg+xml");
            session.property("output.page-width", "100pt");
            session.property("output.page-height", "80pt");
            CTISessionHelper.transcodeStream(session,
                    new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
                    URI.create("urn:test:svg-raster"), "text/html", "UTF-8");
        } finally {
            session.close();
        }
        final String svg = out.toString(StandardCharsets.UTF_8);
        assertTrue("SVGルートが出力されること", svg.contains("<svg"));
        assertTrue("PNGがdata URIとして埋め込まれること", svg.contains("data:image/png;base64,"));
    }
}
