package net.zamasoft.foliojet.layout.fragment;

/** 本配置と、本文を消費しない一時計測を区別します。 */
public enum ReplayIntent {
	MAIN, MEASURE;

	private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

	/** 同期したSTF・Grid・Flex・表のbind連鎖に現在の再生意図を引き継ぎます。 */
	public static ReplayIntent current() {
		final Scope scope = CURRENT.get();
		return scope == null ? MAIN : scope.intent;
	}

	/** MEASUREの内側では、引数省略のbindも本文を消費しません。 */
	public Scope enter() {
		return new Scope(this);
	}

	/** 再生意図の動的スコープ。呼び出しスレッド内でLIFOに閉じます。 */
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
