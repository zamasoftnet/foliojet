package jp.cssj.test.unit._0390_writing_mode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import jp.cssj.test.unit.AbstractTestCase;
import jp.cssj.test.unit.TestPDFUserAgent;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** position:relativeを挟む直交フローでも、画像の%寸法を直接の包含ブロックへ解決する。 */
public class OrthogonalPercentHeightTest extends AbstractTestCase {
	private static final File FIXTURE = new File(
			"files/unittest/0390-writing-mode/orthogonal-percent-height.html");
	private static final double WIDTH_60MM = 170.08;
	private static final double HEIGHT_40MM = 113.39;

	public OrthogonalPercentHeightTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, FIXTURE, "text/html", "UTF-8");
		this.transcodeHorizontalPage();
	}

	private void transcodeHorizontalPage() throws Exception {
		final String source = Files.readString(FIXTURE.toPath(), StandardCharsets.UTF_8)
				.replace("class=\"vertical-page\"", "class=\"horizontal-page\"");
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession mirrorSession = (DirectSession) new DirectDriver()
				.getSession(java.net.URI.create("copper:direct:"), null);
		try {
			mirrorSession.setUserAgent(new TestPDFUserAgent(this));
			mirrorSession.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			mirrorSession.setMessageHandler(this);
			mirrorSession.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			mirrorSession.property("input.include", "**");
			CTISessionHelper.transcodeStream(mirrorSession,
					new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), FIXTURE.toURI(), "text/html",
					"UTF-8");
		} finally {
			mirrorSession.close();
		}
	}

	private boolean checkImage(final String name, final IBox box) {
		if (box.getType() != BoxType.REPLACED) {
			return false;
		}
		assertEquals(name + " width", WIDTH_60MM, box.getWidth(), .01);
		assertEquals(name + " height", HEIGHT_40MM, box.getHeight(), .01);
		return true;
	}

	public boolean check_with_figure_mode(final IBox box, final int page, final double x, final double y) {
		return this.checkImage("figure writing-mode", box);
	}

	public boolean check_without_figure_mode(final IBox box, final int page, final double x, final double y) {
		return this.checkImage("inherited figure writing-mode", box);
	}

	public boolean check_three_level(final IBox box, final int page, final double x, final double y) {
		return this.checkImage("three-level nesting", box);
	}

	public boolean check_mirror(final IBox box, final int page, final double x, final double y) {
		return this.checkImage("horizontal page mirror", box);
	}

	public boolean check_mirror_width(final IBox box, final int page, final double x, final double y) {
		return this.checkImage("vertical percentage-width mirror", box);
	}
}
