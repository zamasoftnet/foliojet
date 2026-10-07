package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.selector.Condition;

/**
 * Results for selectors whose truth is unknown until element end
 * (or the end of the relevant subtree for :has()). Keyed by {@code CSSElement.elementKey}
 * (document-order sequence number, stable across passes).
 * <p>
 * The `:last-child` family is resolved all at once by the lightweight preliminary
 * {@code STRUCTURE_SCAN} (an independent pass phase) without actual layout, then read-only
 * in all subsequent LAYOUT passes. `:has()` does not use {@code STRUCTURE_SCAN};
 * normal LAYOUT passes (`StyleContext.merge`) progressively resolve it by inspecting each
 * element's ancestor chain (the same "accumulate values across multiple passes" design as
 * `PageRef`; see the development plan, "2パス制御モード," for the reason:
 * evaluating relative selectors in `:has()` requires actual `CSSElement` objects including
 * class/id/attributes and `StyleContext.matchesFromPath`, beyond the lightweight walker
 * dedicated to `STRUCTURE_SCAN`).
 * </p>
 * <p>
 * Intentionally separate from {@link PageRef}: {@code PageRef} is a URI/id-based store solely
 * for page references and TOC, without stable keys for id-less elements.
 * Its lifecycle is different: this class resets only once at `STRUCTURE_SCAN` start and
 * continues accumulating through all later passes. Sharing would confuse this with
 * `PageRef`'s `reset()` (see the development plan).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SelectorFacts {
	// ---- E-6 increment 1 (2026-07-24): retention high-water counters ----
	// Measurement basis for spill thresholds and target selection in spillable tape infrastructure (codex design §1.3:
	// SelectorFacts is not converted to tape in E-6; first measure entry counts on real corpora).
	// Only updates the maximum entry count of each Map/Set; does not affect behavior.

	/** High-water mark of {@code lastChild} entry count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong LAST_CHILD_HIGH_WATER = new AtomicLong();

	/** High-water mark of {@code lastOfType} entry count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong LAST_OF_TYPE_HIGH_WATER = new AtomicLong();

	/** High-water mark of {@code empty} entry count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong EMPTY_HIGH_WATER = new AtomicLong();

	/** High-water mark of {@code positionFromEnd} entry count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong POSITION_FROM_END_HIGH_WATER = new AtomicLong();

	/** High-water mark of {@code typePositionFromEnd} entry count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong TYPE_POSITION_FROM_END_HIGH_WATER = new AtomicLong();

	/** High-water mark of {@code hasMatches} key (element) count (E-6 increment 1; behavior unchanged). */
	public static final AtomicLong HAS_MATCH_ELEMENT_HIGH_WATER = new AtomicLong();

	/**
	 * High-water mark of total (element, condition) pairs in {@code hasMatches}
	 * (E-6 increment 1; behavior unchanged). Measures the worst case O(N×H).
	 */
	public static final AtomicLong HAS_MATCH_PAIR_HIGH_WATER = new AtomicLong();

	/** Current total (element, condition) pairs (auxiliary observation counter; not used for decisions). */
	private long hasPairCount;

	private Set<Long> lastChild;
	private Set<Long> lastOfType;
	private Set<Long> empty;

	/** :has(). elementKey → set of HAS_CONDITION (SelectorListCondition) values true for that element. */
	private Map<Long, Set<Condition>> hasMatches;

	/**
	 * Sequence number counted from the end among children of the same parent (1-based).
	 * For {@code :nth-last-child(An+B)}, passes directly to {@code NthCondition.matches(int)}
	 * (reuses the same matching logic as :nth-child()).
	 */
	private Map<Long, Integer> positionFromEnd;

	/** Sequence number from the end among same-named children of the same parent (1-based). */
	private Map<Long, Integer> typePositionFromEnd;

	/**
	 * Call before recording new facts in this pass. Discards all retained facts to avoid
	 * carrying over the preceding scan's results (another document or a restarted scan).
	 */
	public void reset() {
		this.lastChild = null;
		this.lastOfType = null;
		this.empty = null;
		this.positionFromEnd = null;
		this.typePositionFromEnd = null;
		this.hasMatches = null;
		this.hasPairCount = 0;
	}

	/**
	 * Records that hasCondition is true for the element identified by elementKey.
	 * Once true, it never changes (:has() checks existence).
	 */
	public void setHasMatch(long elementKey, Condition hasCondition) {
		if (this.hasMatches == null) {
			this.hasMatches = new HashMap<Long, Set<Condition>>();
		}
		Set<Condition> set = this.hasMatches.get(elementKey);
		if (set == null) {
			set = new HashSet<Condition>();
			this.hasMatches.put(elementKey, set);
		}
		if (set.add(hasCondition)) {
			++this.hasPairCount;
		}
		HAS_MATCH_ELEMENT_HIGH_WATER.accumulateAndGet(this.hasMatches.size(), Math::max);
		HAS_MATCH_PAIR_HIGH_WATER.accumulateAndGet(this.hasPairCount, Math::max);
	}

	public boolean isHasMatch(long elementKey, Condition hasCondition) {
		if (this.hasMatches == null) {
			return false;
		}
		Set<Condition> set = this.hasMatches.get(elementKey);
		return set != null && set.contains(hasCondition);
	}

	public void setLastChild(long elementKey) {
		if (this.lastChild == null) {
			this.lastChild = new HashSet<Long>();
		}
		this.lastChild.add(elementKey);
		LAST_CHILD_HIGH_WATER.accumulateAndGet(this.lastChild.size(), Math::max);
	}

	public boolean isLastChild(long elementKey) {
		return this.lastChild != null && this.lastChild.contains(elementKey);
	}

	public void setLastOfType(long elementKey) {
		if (this.lastOfType == null) {
			this.lastOfType = new HashSet<Long>();
		}
		this.lastOfType.add(elementKey);
		LAST_OF_TYPE_HIGH_WATER.accumulateAndGet(this.lastOfType.size(), Math::max);
	}

	public boolean isLastOfType(long elementKey) {
		return this.lastOfType != null && this.lastOfType.contains(elementKey);
	}

	public void setEmpty(long elementKey) {
		if (this.empty == null) {
			this.empty = new HashSet<Long>();
		}
		this.empty.add(elementKey);
		EMPTY_HIGH_WATER.accumulateAndGet(this.empty.size(), Math::max);
	}

	public boolean isEmpty(long elementKey) {
		return this.empty != null && this.empty.contains(elementKey);
	}

	public void setPositionFromEnd(long elementKey, int position) {
		if (this.positionFromEnd == null) {
			this.positionFromEnd = new HashMap<Long, Integer>();
		}
		this.positionFromEnd.put(elementKey, position);
		POSITION_FROM_END_HIGH_WATER.accumulateAndGet(this.positionFromEnd.size(), Math::max);
	}

	/** Sequence number from the end. -1 if no scan result (same warning + nonmatch path as unsupported selectors). */
	public int getPositionFromEnd(long elementKey) {
		if (this.positionFromEnd == null) {
			return -1;
		}
		Integer position = this.positionFromEnd.get(elementKey);
		return position != null ? position.intValue() : -1;
	}

	public void setTypePositionFromEnd(long elementKey, int position) {
		if (this.typePositionFromEnd == null) {
			this.typePositionFromEnd = new HashMap<Long, Integer>();
		}
		this.typePositionFromEnd.put(elementKey, position);
		TYPE_POSITION_FROM_END_HIGH_WATER.accumulateAndGet(this.typePositionFromEnd.size(), Math::max);
	}

	public int getTypePositionFromEnd(long elementKey) {
		if (this.typePositionFromEnd == null) {
			return -1;
		}
		Integer position = this.typePositionFromEnd.get(elementKey);
		return position != null ? position.intValue() : -1;
	}
}
