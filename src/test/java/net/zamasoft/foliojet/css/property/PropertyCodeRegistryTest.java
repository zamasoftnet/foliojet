package net.zamasoft.foliojet.css.property;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * A static exhaustive check that every interpretable property has a cascade code
 * (2026-08-01).
 *
 * <p>
 * Cascade codes ({@link ElementPropertySet#getCode}) form a single registry assigned only by
 * static registration (reg/regCode) in {@code ElementPropertySet}.
 * Properties registered only by name in other PropertySets (@page, @font-face)
 * <b>silently disappear</b> in {@code CSSStyle.set/get}. This trap actually occurred with
 * @page {@code size} (2026-07-31) and caused lengthy debugging.
 * This test immediately detects missing registrations for new properties during testing
 * (a second defense alongside -ea assertions in {@code CSSStyle.set/get}).
 * </p>
 */
public class PropertyCodeRegistryTest extends TestCase {

	private static void assertAllCoded(final String setName, final PropertySet set, final List<String> missing) {
		for (final PropertyInfo info : set.registeredInfos()) {
			if (info instanceof PrimitivePropertyInfo primitive) {
				if (ElementPropertySet.getCode(primitive) < 0) {
					missing.add(setName + ": " + info.getName());
				}
			}
			// Non-primitives (shorthands) are parsed into component Entries before set.
			// Each set also registers components by name
			// (the put(Shorthand.INFO, components...) convention), so this enumeration
			// checks them as primitives.
		}
	}

	public void testEveryParseablePropertyHasCascadeCode() {
		final List<String> missing = new ArrayList<>();
		assertAllCoded("element", ElementPropertySet.getInstance(), missing);
		assertAllCoded("page", PagePropertySet.getInstance(), missing);
		assertAllCoded("font-face", FontFacePropertySet.getInstance(), missing);
		assertTrue("カスケード用コード未割当の解釈可能プロパティ(ElementPropertySetへのregCode漏れ): " + missing,
				missing.isEmpty());
	}
}
