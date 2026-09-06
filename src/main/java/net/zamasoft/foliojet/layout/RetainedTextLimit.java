package net.zamasoft.foliojet.layout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.RootBuilder;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * 溜め込みの上限({@code processing.retained-text-limit})の会計です。
 *
 * <p>
 * 寸法が決まるまで中身を溜める要素(auto 表・TwoPass 宿主・計測用複製・grid/flex 宿主・
 * balance 段組・絶対配置・固定幅 float/inline-block/直交ブロック)が開いている間、
 * 最も外側の要素の累計に組版済み文字の payload(2×charCount)を足す。閉じたら忘れる。
 * 設計: copperpdf4/docs/design/retained-layout-budget-design.md §3 D1。
 * </p>
 */
public final class RetainedTextLimit implements AutoCloseable {
	/** 診断・試験用。制限の判定には使いません。 */
	public static final AtomicLong HIGH_WATER = new AtomicLong();

	/** Pass B終了後・MAIN開始前の観測だけに使います。試験は保存・復元すること。 */
	public static volatile java.util.function.Consumer<RetainedTextLimit> beforeTableMainBind;

	private final UserAgent ua;
	private long limit;
	private Accounting accounting = new Accounting(null);
	private long highWater;

	public RetainedTextLimit(final UserAgent ua) {
		this.ua = ua;
		this.limit = UAProps.PROCESSING_RETAINED_TEXT_LIMIT.getLong(ua);
	}

	/** ページ文脈のない単体ビルダーと柱のミニレイアウトには会計を作りません。 */
	public static RetainedTextLimit get(final LayoutStack stack) {
		final RootBuilder root = stack == null ? null : stack.getPageContext();
		if (root == null) return null;
		final var generator = root.getPageGenerator();
		if (generator instanceof MeasurePageGenerator measure && !measure.isRetainedTextCounted()) return null;
		return generator.getUserAgent().getRetainedTextLimit();
	}

	/** 変換開始時だけ呼びます。同一変換内の複数パスとstaticの観測値はリセットしません。 */
	public void reset() {
		this.close();
		this.accounting = new Accounting(null);
		this.limit = UAProps.PROCESSING_RETAINED_TEXT_LIMIT.getLong(this.ua);
		this.highWater = 0;
	}

	public static String elementName(final Params params, final String fallback) {
		final String name = params.element == null ? null : params.element.lName();
		return name == null || name.isEmpty() ? fallback : name;
	}

	public Scope enter(final String elementName) {
		final Scope scope = new Scope(elementName);
		this.accounting.elements.addLast(scope);
		return scope;
	}

	public void leave() {
		this.accounting.leave();
	}

	public void add(final long payloadBytes) {
		final Accounting accounting = this.accounting;
		if (accounting.elements.isEmpty() || accounting.suspensions > 0 || payloadBytes == 0) {
			return;
		}
		if (payloadBytes < 0) {
			throw new IllegalArgumentException("文字payloadは負にできません");
		}
		accounting.currentBytes = payloadBytes > Long.MAX_VALUE - accounting.currentBytes
				? Long.MAX_VALUE : accounting.currentBytes + payloadBytes;
		if (accounting.currentBytes > this.highWater) {
			this.highWater = accounting.currentBytes;
			if (this.highWater > HIGH_WATER.get()) {
				HIGH_WATER.accumulateAndGet(this.highWater, Math::max);
			}
		}
		if (this.limit > 0 && accounting.currentBytes > this.limit) {
			final String[] args = { accounting.elements.getFirst().elementName, Long.toString(this.limit),
					Long.toString(accounting.currentBytes) };
			this.ua.message(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, args);
			throw new RetainedTextLimitException(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, args);
		}
	}

	public long getCurrentBytes() {
		return this.accounting.currentBytes;
	}

	public long getHighWater() {
		return this.highWater;
	}

	public long getLimit() {
		return this.limit;
	}

	/**
	 * 破棄する複製の会計。MAINの親へ計測量を持ち越しません。
	 * 親のbind中にPass Bが走るため、通常の入れ子と異なりスタックごと退避します。
	 * high-waterと上限は共有し、計測中も同じ上限で検査します。
	 */
	public Measurement measurement(final String elementName) {
		return new Measurement(elementName);
	}

	/** 数え済みの内容の再生中だけ加算を保留します。入れ子可、独立計測には引き継ぎません。 */
	public Suspension suspend() {
		return new Suspension();
	}

	@Override
	public void close() {
		for (Accounting accounting = this.accounting; accounting != null; accounting = accounting.previous) {
			accounting.close();
		}
	}

	/** スタック・累計・加算保留を同じ会計に所属させます。 */
	private static final class Accounting {
		private final Accounting previous;
		private final Deque<Scope> elements = new ArrayDeque<>();
		private long currentBytes;
		private int suspensions;
		private boolean closed;

		private Accounting(final Accounting previous) {
			this.previous = previous;
		}

		private void leave() {
			this.elements.removeLast().closed = true;
			if (this.elements.isEmpty()) this.currentBytes = 0;
		}

		private void close() {
			while (!this.elements.isEmpty()) this.leave();
			this.closed = true;
		}
	}

	public final class Measurement implements AutoCloseable {
		private final Accounting accounting;

		private Measurement(final String elementName) {
			final Accounting previous = RetainedTextLimit.this.accounting;
			final String owner = previous.elements.isEmpty() ? elementName
					: previous.elements.getFirst().elementName;
			this.accounting = new Accounting(previous);
			RetainedTextLimit.this.accounting = this.accounting;
			RetainedTextLimit.this.enter(owner);
		}

		@Override
		public void close() {
			this.accounting.close();
			// 外側の計測が先に閉じても、現在の別会計を畳まない。
			while (RetainedTextLimit.this.accounting.closed && RetainedTextLimit.this.accounting.previous != null) {
				RetainedTextLimit.this.accounting = RetainedTextLimit.this.accounting.previous;
			}
		}
	}

	public final class Suspension implements AutoCloseable {
		private final Accounting accounting = RetainedTextLimit.this.accounting;
		private boolean closed;

		private Suspension() {
			++this.accounting.suspensions;
		}

		@Override
		public void close() {
			if (this.closed) return;
			--this.accounting.suspensions;
			this.closed = true;
		}
	}

	/** SAXの途中で失敗した場合も、未完の子要素とともにfinallyで閉じます。 */
	public final class Scope implements AutoCloseable {
		private final Accounting accounting = RetainedTextLimit.this.accounting;
		private final String elementName;
		private boolean closed;

		private Scope(final String elementName) {
			this.elementName = elementName;
		}

		@Override
		public void close() {
			while (!this.closed) {
				this.accounting.leave();
			}
		}
	}
}
