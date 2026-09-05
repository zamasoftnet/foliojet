package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.css.CSSElement;

/**
 * 現在のパスに関係するオブジェクトを保持します。
 */
public class PassContext {
	private final SectionState sectionState = new SectionState();
	private final PageAssignmentState<String> stringState = new PageAssignmentState<String>();
	private final BuildStringState buildStringState = new BuildStringState();
	private final net.zamasoft.foliojet.css.style.running.RunningRegistry runningRegistry =
			new net.zamasoft.foliojet.css.style.running.RunningRegistry();

	/** runningとstring-setが共有する配置アンカーの所有者です。 */
	public net.zamasoft.foliojet.css.style.running.RunningRegistry getRunningRegistry() {
		return this.runningRegistry;
	}

	public PageAssignmentState<net.zamasoft.foliojet.css.style.running.RunningTemplate> getRunningState() {
		return this.runningRegistry.state();
	}

	/** 本文の生成内容からの先行参照用です。配置済みの頁状態とは分離します。 */
	public BuildStringState getBuildStringState() {
		return this.buildStringState;
	}

	/** 先読み済みの代入を、配置済みの頁の終了では捨てない本文参照用状態です。 */
	public static final class BuildStringState {
		private static final class Candidates {
			final java.util.TreeMap<Long, String> pending = new java.util.TreeMap<Long, String>();
			final java.util.Set<Long> committed = new java.util.HashSet<Long>();
			PageAssignmentState.Assignment<String> latest, entry, pageLast;
		}

		private final java.util.Map<String, Candidates> names = new java.util.HashMap<String, Candidates>();

		public void begin(final String name, final long order) {
			this.names.computeIfAbsent(name, key -> new Candidates()).pending.putIfAbsent(order, null);
		}

		public void assign(final String name, final String value, final long order, final boolean beginsPage) {
			final Candidates c = this.names.computeIfAbsent(name, key -> new Candidates());
			c.pending.put(order, value);
			if (c.latest == null || order >= c.latest.order()) {
				c.latest = new PageAssignmentState.Assignment<String>(order, value, false, false);
			}
		}

		/** content() の完成値は同じ order を更新し、古い頁で最新値を巻き戻しません。 */
		public void complete(final String name, final String value, final long order) {
			this.assign(name, value, order, false);
			final Candidates c = this.names.get(name);
			c.committed.add(order);
			if (c.pageLast == null || order >= c.pageLast.order()) {
				c.pageLast = new PageAssignmentState.Assignment<String>(order, value, false, false);
			}
		}

		public PageAssignmentState.Resolution<String> resolve(final String name, final PageAssignmentState.Mode mode) {
			final Candidates c = this.names.get(name);
			if (c == null) {
				return new PageAssignmentState.Resolution<String>(PageAssignmentState.Presence.ABSENT, null);
			}
			if (mode == PageAssignmentState.Mode.FIRST_EXCEPT && !c.pending.isEmpty()) {
				return new PageAssignmentState.Resolution<String>(PageAssignmentState.Presence.SUPPRESSED, null);
			}
			final String value = switch (mode) {
			case LAST -> c.latest == null ? null : c.latest.value();
			case FIRST -> !c.pending.isEmpty() ? c.pending.firstEntry().getValue()
					: c.entry == null ? null : c.entry.value();
			case START, FIRST_EXCEPT -> c.entry == null ? null : c.entry.value();
			};
			return new PageAssignmentState.Resolution<String>(value == null
					? PageAssignmentState.Presence.ABSENT : PageAssignmentState.Presence.VALUE, value);
		}

		public void endPage() {
			for (final Candidates c : this.names.values()) {
				if (c.pageLast != null) {
					c.entry = c.pageLast;
					c.pageLast = null;
				}
				c.committed.forEach(c.pending::remove);
				c.committed.clear();
			}
		}
	}
	private CSSElement pageSide;
	private CounterContext counterContext = null;
	private int pageNumber = 0;

	public SectionState getSectionState() {
		return this.sectionState;
	}

	public PageAssignmentState<String> getStringState() {
		return this.stringState;
	}

	public CSSElement getPageSide() {
		return this.pageSide;
	}

	public void setPageSide(CSSElement pageSide) {
		this.pageSide = pageSide;
	}
	
	public void resetNonPageCounters() {
		if (this.counterContext == null) {
			return;
		}
		this.counterContext.resetNonPageCounters();
	}

	public CounterScope getCounterScope(int level, boolean create) {
		if (this.counterContext == null) {
			if (!create) {
				return null;
			}
			this.counterContext = new CounterContext();
		}
		return this.counterContext.getCounterScope(level, create);
	}

	public int getPageNumber() {
		return this.pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}
}
