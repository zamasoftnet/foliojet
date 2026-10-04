package net.zamasoft.foliojet.ua.props;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.message.MessageCodes;

/**
 * 入出力プロパティの登録簿と不正な値の扱いを固定します(2026-10-04、全体レビュー)。
 */
public class UAPropsTest extends TestCase {
	/** {@code all()} は手で並べた一覧ではなく、宣言されたものすべて(以前は 8 件が漏れていた)。 */
	public void testAllHasEveryDeclaredManager() {
		final List<PropManager> all = UAProps.all();
		for (final PropManager manager : new PropManager[] { UAProps.INPUT_SIZE_LIMIT, UAProps.INPUT_RESOURCE_SIZE_LIMIT,
				UAProps.INPUT_RESOURCE_COUNT_LIMIT, UAProps.INPUT_IMAGE_PIXEL_LIMIT, UAProps.OUTPUT_IMAGE_PIXEL_LIMIT,
				UAProps.PROCESSING_TIME_LIMIT, UAProps.OUTPUT_PAGED_SVG_FONT_SCOPE, UAProps.OUTPUT_PAGED_SVG_BASE_URI,
				UAProps.OUTPUT_TYPE, UAProps.OUTPUT_SVG_TEXT }) {
			assertTrue(manager.getName(), all.contains(manager));
		}
		assertEquals("no duplicates", all.size(), all.stream().map(PropManager::getName).distinct().count());
	}

	/** 不正な真偽値は警告して既定値にする(以前は false にして、既定が true の性質を切っていた)。 */
	public void testInvalidBooleanFallsBackToTheDefault() {
		final List<Short> codes = new ArrayList<>();
		final BooleanPropManager on = new BooleanPropManager("test.on", true);
		assertTrue(on.getBoolean("yes", (code, args) -> codes.add(code)));
		assertFalse(on.getBoolean("false", (code, args) -> codes.add(code)));
		assertEquals(List.of(MessageCodes.WARN_BAD_IO_PROPERTY), codes);
	}
}
