package net.zamasoft.foliojet.ua;

/**
 * カウンタのスコープです。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CounterScope {
	private Counter[] counters = null;

	public void reset(String name, int value) {
		Counter counter = this.getCounter(name);
		if (counter == null) {
			this.increment(name, value);
		} else {
			counter.value = value;
		}
	}

	public void increment(String name, int delta) {
		Counter counter = this.getCounter(name);
		if (counter == null) {
			counter = new Counter(name);
			if (this.counters == null) {
				this.counters = new Counter[] { counter };
			} else {
				Counter[] counters = new Counter[this.counters.length + 1];
				System.arraycopy(this.counters, 0, counters, 1, this.counters.length);
				counters[0] = counter;
				this.counters = counters;
			}
		}
		counter.value += delta;
	}

	public boolean defined(String name) {
		return this.getCounter(name) != null;
	}

	public int get(String name) {
		Counter counter = this.getCounter(name);
		if (counter == null) {
			return 0;
		}
		return counter.value;
	}

	private Counter getCounter(String name) {
		if (this.counters == null) {
			return null;
		}
		for (int i = 0; i < this.counters.length; ++i) {
			Counter counter = this.counters[i];
			if (counter.name.equalsIgnoreCase(name)) {
				return counter;
			}
		}
		return null;
	}

	/**
	 * スコープの複製を返します(M6b のスナップショット用)。
	 */
	public CounterScope copy() {
		final CounterScope scope = new CounterScope();
		scope.counters = this.copyCounters();
		return scope;
	}

	public Counter[] copyCounters() {
		if (this.counters == null) {
			return null;
		}
		Counter[] counters = new Counter[this.counters.length];
		for (int i = 0; i < counters.length; ++i) {
			Counter counter = this.counters[i];
			counters[i] = new Counter(counter.name, counter.value);
		}
		return counters;
	}
}
