package net.zamasoft.foliojet.ua.props;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.message.MessageCodes;

/**
 * Locks down the I/O property registry and handling of invalid values (2026-10-04, overall review).
 */
public class UAPropsTest extends TestCase {
	/**
	 * {@code all()} includes every declaration, rather than a hand-maintained list (previously missed eight
	 * entries).
	 */
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

	/** Invalid booleans warn and use the default (previously became false, disabling features that default to true). */
	public void testInvalidBooleanFallsBackToTheDefault() {
		final List<Short> codes = new ArrayList<>();
		final BooleanPropManager on = new BooleanPropManager("test.on", true);
		assertTrue(on.getBoolean("yes", (code, args) -> codes.add(code)));
		assertFalse(on.getBoolean("false", (code, args) -> codes.add(code)));
		assertEquals(List.of(MessageCodes.WARN_BAD_IO_PROPERTY), codes);
	}
}
