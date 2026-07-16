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
}
