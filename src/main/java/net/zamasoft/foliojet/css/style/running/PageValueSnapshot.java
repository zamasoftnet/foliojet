package net.zamasoft.foliojet.css.style.running;

import java.net.URI;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.ua.Counter;
import net.zamasoft.foliojet.ua.CounterScope;
import net.zamasoft.foliojet.ua.PageAssignmentState.Mode;
import net.zamasoft.foliojet.ua.PageAssignmentState.Presence;
import net.zamasoft.foliojet.ua.UserAgent;

/** フローのvisit後に確定した頁の値です。可変なカウンタ・文字列状態は公開しません。 */
public final class PageValueSnapshot {
	private final Map<String, Integer> counters;
	private final Map<String, Map<Mode, String>> strings;
	private final boolean left, right, first, single;
	private final String pageName;
	private final net.zamasoft.foliojet.ua.PageRef.CounterView references;

	public PageValueSnapshot(final UserAgent ua, final CSSElement page, final String pageName) {
		final Map<String, Integer> counters = new HashMap<String, Integer>();
		final CounterScope scope = ua.getPassContext().getCounterScope(0, false);
		final Counter[] values = scope == null ? null : scope.copyCounters();
		if (values != null) {
			for (final Counter counter : values) {
				counters.put(counter.name.toLowerCase(Locale.ROOT), counter.value);
			}
		}
		this.counters = Map.copyOf(counters);
		final Map<String, Map<Mode, String>> strings = new HashMap<String, Map<Mode, String>>();
		final var state = ua.getPassContext().getStringState();
		for (final String name : state.names()) {
			final Map<Mode, String> modes = new EnumMap<Mode, String>(Mode.class);
			for (final Mode mode : Mode.values()) {
				final var value = state.resolve(name, mode);
				modes.put(mode, value.presence() == Presence.VALUE ? value.value() : "");
			}
			strings.put(name, Map.copyOf(modes));
		}
		this.strings = Map.copyOf(strings);
		this.left = page != null && page.isPseudoClass(CSSElement.PC_LEFT);
		this.right = page != null && page.isPseudoClass(CSSElement.PC_RIGHT);
		this.first = page != null && page.isPseudoClass(CSSElement.PC_FIRST);
		this.single = page == CSSElement.PAGE_SINGLE || page == CSSElement.PAGE_SINGLE_FIRST;
		this.pageName = pageName;
		final var pageRef = ua.getUAContext().getPageRef();
		this.references = pageRef == null ? (uri, name, all) -> List.of() : pageRef.counterView(ua.isLastPass());
	}

	public int counter(final String name) {
		return this.counters.getOrDefault(name.toLowerCase(Locale.ROOT), 0);
	}

	/** マージンボックスが参照するroot scopeの階層は一つです。 */
	public List<Integer> counters(final String name) {
		return List.of(this.counter(name));
	}

	public String string(final String name, final Mode mode) {
		return this.strings.getOrDefault(name, Map.of()).getOrDefault(mode, "");
	}

	/** 参照先fragmentの値です。表示頁のcounterとは独立です。 */
	public List<Integer> targetCounters(final URI uri, final String name, final boolean all) {
		return this.references.counters(uri, name, all);
	}

	public boolean left() { return this.left; }
	public boolean right() { return this.right; }
	public boolean first() { return this.first; }
	public boolean single() { return this.single; }
	public String pageName() { return this.pageName; }
}
