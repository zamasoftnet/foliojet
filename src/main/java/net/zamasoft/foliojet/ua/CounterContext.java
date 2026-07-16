package net.zamasoft.foliojet.ua;

class CounterContext {
	private static final int MAX_DEPTH = 512;

	private final CounterScope[] counters = new CounterScope[MAX_DEPTH];

	public CounterScope getCounterScope(int level, boolean create) {
		if (level >= MAX_DEPTH) {
			level = MAX_DEPTH - 1;
		}
		if (create && this.counters[level] == null) {
			this.counters[level] = new CounterScope();
		}
		return this.counters[level];
	}
	
	public void resetNonPageCounters() {
		for (int i = 1; i < MAX_DEPTH; ++i) {
			this.counters[i] = null;
		}
	}

	/**
	 * ページカウンタ(レベル0)以外のスコープの複製を返します(M6b)。
	 * 状態がなければ null。
	 */
	public CounterScope[] snapshotNonPageCounters() {
		CounterScope[] snapshot = null;
		for (int i = 1; i < MAX_DEPTH; ++i) {
			if (this.counters[i] != null) {
				if (snapshot == null) {
					snapshot = new CounterScope[MAX_DEPTH];
				}
				snapshot[i] = this.counters[i].copy();
			}
		}
		return snapshot;
	}

	/**
	 * snapshotNonPageCounters の結果を書き戻します。
	 * ページカウンタ(レベル0)は現在値を維持します。
	 */
	public void restoreNonPageCounters(final CounterScope[] snapshot) {
		for (int i = 1; i < MAX_DEPTH; ++i) {
			this.counters[i] = snapshot == null || snapshot[i] == null ? null : snapshot[i].copy();
		}
	}
}
