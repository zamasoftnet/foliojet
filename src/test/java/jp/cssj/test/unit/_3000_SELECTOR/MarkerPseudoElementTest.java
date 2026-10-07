package jp.cssj.test.unit._3000_SELECTOR;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Smoke test for {@code ::marker} (added on 2026-07-21, CSS Lists).
 *
 * <p>
 * Marker boxes ({@code OutsideMarkerBox}/{@code InsideMarkerBox}) are synthetic boxes without
 * corresponding real elements. {@code CSSElement.MARKER} has no id, so the existing
 * {@code check_ID} callback mechanism (based on element ids) cannot check them directly.
 * This test verifies only the contract that {@code ::marker} rules resolve through the cascade without
 * exceptions and the document completes. Style application itself uses exactly the same mechanism
 * as {@code ::before}/{@code ::after} (push CSSElement + merge + apply), already extensively
 * tested by the existing corpus.
 * </p>
 */
public class MarkerPseudoElementTest extends AbstractTestCase {
	public MarkerPseudoElementTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3000-SELECTOR/marker.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
