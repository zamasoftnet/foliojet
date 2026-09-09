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
 * 保持量の上限の設計（D1）による。
 * </p>
 */
public final class RetainedTextLimit implements AutoCloseable {
	/** 診断・試験用。制限の判定には使いません。 */
	public static final AtomicLong HIGH_WATER = new AtomicLong();

	/** 診断・試験用。再生中の加算保留に入った回数です。 */
	public static final AtomicLong SUSPEND_ENTRIES = new AtomicLong();

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
		net.zamasoft.foliojet.layout.fragment.ScratchReplayScope.register(scope);
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
		this.check(accounting, accounting.elements.getFirst().elementName);
	}

	private void check(final Accounting accounting, final String owner) {
		final long bytes = accounting.windowBytes > Long.MAX_VALUE - accounting.currentBytes
				? Long.MAX_VALUE : accounting.currentBytes + accounting.windowBytes;
		if (bytes > this.highWater) {
			this.highWater = bytes;
			if (this.highWater > HIGH_WATER.get()) {
				HIGH_WATER.accumulateAndGet(this.highWater, Math::max);
			}
		}
		if (this.limit > 0 && bytes > this.limit) {
			final String[] args = { owner, Long.toString(this.limit), Long.toString(bytes) };
			this.ua.message(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, args);
			throw new RetainedTextLimitException(MessageCodes.ERROR_RETAINED_TEXT_LIMIT, args);
		}
	}

	public long getCurrentBytes() {
		return this.accounting.currentBytes + this.accounting.windowBytes;
	}

	/** 宿主スタックを開かずに、未配達文字だけを所有するpage-windowです。 */
	public PageWindow pageWindow() {
		return new PageWindow();
	}

	public final class PageWindow implements AutoCloseable {
		private final Accounting accounting = RetainedTextLimit.this.accounting;
		private long bytes;
		private boolean closed;

		/** 保持する前に呼ぶ。Bの独立会計の接続外でだけ使用します。 */
		public void add(final long bytes) {
			if (this.closed || bytes < 0) throw new IllegalStateException("不正なpage-window加算");
			this.bytes = Math.addExact(this.bytes, bytes);
			this.accounting.windowBytes = Math.addExact(this.accounting.windowBytes, bytes);
			RetainedTextLimit.this.check(this.accounting, "page-window");
		}

		public void remove(final long bytes) {
			if (this.closed || bytes < 0 || bytes > this.bytes) throw new IllegalStateException("不正なpage-window減算");
			this.bytes -= bytes;
			this.accounting.windowBytes -= bytes;
		}

		public long currentBytes() {
			return this.bytes;
		}

		@Override
		public void close() {
			if (this.closed) return;
			this.remove(this.bytes);
			this.closed = true;
		}
	}

	public long getHighWater() {
		return this.highWater;
	}

	/** 独立した子UAの診断値だけを集約します。累計と上限は変更しません。呼び出し側で排他します。 */
	public void mergeHighWater(final RetainedTextLimit child) {
		this.highWater = Math.max(this.highWater, child.getHighWater());
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

	/** 接続を外しても累計・スコープ・加算保留を保持する、scratch専用の会計です。 */
	public MeasurementAccount measurementAccount(final String elementName) {
		return new MeasurementAccount(elementName);
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
		private Accounting previous;
		private final Deque<Scope> elements = new ArrayDeque<>();
		private long currentBytes;
		private long windowBytes;
		private int suspensions;
		private boolean closed;
		private boolean attached;

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
			while (RetainedTextLimit.this.accounting.closed && !RetainedTextLimit.this.accounting.attached
					&& RetainedTextLimit.this.accounting.previous != null) {
				RetainedTextLimit.this.accounting = RetainedTextLimit.this.accounting.previous;
			}
		}
	}

	/** 同一スレッドで、MAINの会計と交互に接続します。上限・high-waterは従来どおり共有します。 */
	public final class MeasurementAccount implements AutoCloseable {
		private final Accounting accounting = new Accounting(null);
		private MeasurementAttachment attachment;

		private MeasurementAccount(final String elementName) {
			final Accounting previous = RetainedTextLimit.this.accounting;
			final String owner = previous.elements.isEmpty() ? elementName : previous.elements.getFirst().elementName;
			this.accounting.elements.addLast(new Scope(this.accounting, owner));
		}

		public MeasurementAttachment attach() {
			if (this.accounting.closed || this.attachment != null) {
				throw new IllegalStateException("計測会計は未解放・未接続の間だけ接続できます");
			}
			this.attachment = new MeasurementAttachment(this);
			return this.attachment;
		}

		/** 連続probeではページを最外要素相当とし、子スコープを保ったまま累計を区切ります。 */
		public long finishPage() {
			if (this.accounting.closed) throw new IllegalStateException("解放済みの計測会計");
			final long bytes = this.accounting.currentBytes;
			this.accounting.currentBytes = 0;
			return bytes;
		}

		public long currentBytes() {
			return this.accounting.currentBytes;
		}

		/** 累計はMAINへ加算せずに捨て、共有high-waterは残します。冪等。 */
		public void release() {
			this.accounting.close();
		}

		@Override
		public void close() {
			this.release();
		}
	}

	/** 計測会計への一時接続。closeで前の会計へ戻し、計測会計自体は畳みません。 */
	public final class MeasurementAttachment implements AutoCloseable {
		private final MeasurementAccount owner;
		private boolean closed;

		private MeasurementAttachment(final MeasurementAccount owner) {
			this.owner = owner;
			owner.accounting.previous = RetainedTextLimit.this.accounting;
			owner.accounting.attached = true;
			RetainedTextLimit.this.accounting = owner.accounting;
		}

		@Override
		public void close() {
			if (this.closed || RetainedTextLimit.this.accounting != this.owner.accounting) {
				throw new IllegalStateException("計測会計の接続は取得と逆順に一度だけ閉じます");
			}
			this.closed = true;
			RetainedTextLimit.this.accounting = this.owner.accounting.previous;
			this.owner.accounting.previous = null;
			this.owner.accounting.attached = false;
			this.owner.attachment = null;
			// 外側の従来型Measurementが先に閉じられていたら、その会計は復活させない。
			while (RetainedTextLimit.this.accounting.closed && !RetainedTextLimit.this.accounting.attached
					&& RetainedTextLimit.this.accounting.previous != null) {
				RetainedTextLimit.this.accounting = RetainedTextLimit.this.accounting.previous;
			}
		}
	}

	public final class Suspension implements AutoCloseable {
		private final Accounting accounting = RetainedTextLimit.this.accounting;
		private boolean closed;

		private Suspension() {
			++this.accounting.suspensions;
			SUSPEND_ENTRIES.incrementAndGet();
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
		private final Accounting accounting;
		private final String elementName;
		private boolean closed;

		private Scope(final String elementName) {
			this(RetainedTextLimit.this.accounting, elementName);
		}

		private Scope(final Accounting accounting, final String elementName) {
			this.accounting = accounting;
			this.elementName = elementName;
		}

		public boolean isClosed() {
			return this.closed;
		}

		@Override
		public void close() {
			while (!this.closed) {
				this.accounting.leave();
			}
		}
	}
}
