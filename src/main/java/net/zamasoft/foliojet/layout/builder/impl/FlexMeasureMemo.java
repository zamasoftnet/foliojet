package net.zamasoft.foliojet.layout.builder.impl;

import java.util.HashMap;
import java.util.Map;

import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * Content-based main sizes of column flex items, shared by every container that one outermost
 * {@code FlexBuilder.bind} replays (2026-10-08).
 *
 * <p>
 * A retained column measures an item by replaying its body into a copy, then binds it by replaying it again, and a
 * column nested in the item does the same on each of those replays. Without sharing, the work doubled at every level:
 * 20 nested centered columns took seconds and charged 2^20 copies of their text (codex review 2026-10-08). The same
 * sealed range measured at the same width gives the same size within one bind (the same page and pass), so the first
 * result is reused; the memo lives only while the outermost bind runs.
 * </p>
 */
final class FlexMeasureMemo {
	private static final ThreadLocal<FlexMeasureMemo> CURRENT = new ThreadLocal<>();

	/** A sealed range (by identity of its source) measured at a cross size and a percentage base. */
	record Key(LayoutSource source, long fromId, long toId, long lineSize, long insetBase) {
		Key(final LayoutSource source, final long fromId, final long toId, final double lineSize,
				final double insetBase) {
			this(source, fromId, toId, Double.doubleToLongBits(lineSize), Double.doubleToLongBits(insetBase));
		}
	}

	/** The memo held open by one bind. */
	interface Scope extends AutoCloseable {
		@Override
		void close();
	}

	private final Map<Key, Double> sizes = new HashMap<>();

	private FlexMeasureMemo() {
	}

	/** Opens the memo for an outermost bind; a nested bind shares the open one (closing it then does nothing). */
	static Scope enter() {
		if (CURRENT.get() != null) {
			return () -> {
			};
		}
		CURRENT.set(new FlexMeasureMemo());
		return CURRENT::remove;
	}

	/** The open memo, or null outside a bind. */
	static FlexMeasureMemo current() {
		return CURRENT.get();
	}

	Double get(final Key key) {
		return this.sizes.get(key);
	}

	void put(final Key key, final double size) {
		this.sizes.put(key, size);
	}
}
