package net.zamasoft.foliojet.layout.fragment;

/** Distinguishes final placement from temporary measurement that does not consume the body. */
public enum ReplayIntent {
	MAIN, MEASURE;

	private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

	/** Propagates the current replay intent through synchronous STF/Grid/Flex/table bind chains. */
	public static ReplayIntent current() {
		final Scope scope = CURRENT.get();
		return scope == null ? MAIN : scope.intent;
	}

	/** Inside MEASURE, even bind calls with the argument omitted do not consume the body. */
	public Scope enter() {
		return new Scope(this);
	}

	/** Dynamic scope for replay intent. Close in LIFO order on the calling thread. */
	public static final class Scope implements AutoCloseable {
		private final Scope previous;
		private final ReplayIntent intent;

		private Scope(final ReplayIntent intent) {
			this.previous = CURRENT.get();
			this.intent = current() == MEASURE ? MEASURE : intent;
			CURRENT.set(this);
		}

		@Override
		public void close() {
			if (CURRENT.get() != this) {
				throw new IllegalStateException("再生意図は取得と逆順に一度だけ閉じます");
			}
			if (this.previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(this.previous);
			}
		}
	}
}
