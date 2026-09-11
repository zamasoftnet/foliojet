package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.FootnotePageProbe;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * M6b v3 のレイアウトソースプロトコルtee——doc入力プロトコル
 * (StartBlock/Chars/EndBlock)を{@link LayoutSource}へ記録し、
 * {@link DocumentBuilder}へ渡します。既定文書は記録直後に直通し、
 * bottom+縦組みだけBへ即時配達してCを待ち行列で遅らせます。
 *
 * <p>
 * 記録と引き渡しの<b>順序とfreeze時点が契約</b>である——記録は
 * {@code LayoutSource.append/freeze}で入力を凍結してから配達する。
 * 改ページ残余の再生はこのログから、ライブ状態に無干渉な専用ドライバ
 * ({@code SourceReplayer})が行う。
 * </p>
 *
 * <p>
 * {@link LayoutSource}の寿命は変換1回に一致し、closeは
 * {@code StyleBuilder.finish()}(成功経路の早期解放)と
 * {@code CSSProcessor.dispose()}(formatterのfinally——例外時清算)の
 * 両方から保証される(冪等)。
 * </p>
 */
final class RecordingLayoutSink {
	private final DocumentBuilder doc;

	boolean isEligibleFootnoteColumnOwner() {
		return this.doc.isEligibleFootnoteColumnOwner();
	}
	private final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events;
	private net.zamasoft.foliojet.layout.box.IBox sourceBox;
	private net.zamasoft.foliojet.css.CSSElement sourceElement;
	private final java.util.Map<net.zamasoft.foliojet.css.CSSStyle, StringBuilder> contentsSources =
			new java.util.IdentityHashMap<net.zamasoft.foliojet.css.CSSStyle, StringBuilder>();

	void beginSource(final net.zamasoft.foliojet.css.CSSElement element) {
		this.sourceBox = null;
		this.sourceElement = element;
	}

	net.zamasoft.foliojet.layout.box.IBox sourceBox() {
		return this.sourceBox;
	}

	void endContentsSource(final net.zamasoft.foliojet.css.CSSStyle style) {
		this.contentsSources.remove(style);
	}
	private net.zamasoft.foliojet.css.style.running.RunningRegistry assignments;
	private static final class AnchorFrame {
		final long source;
		byte whiteSpace = AbstractTextParams.WHITE_SPACE_NORMAL;
		int lastChar = -1;
		int tailChar = -1;
		long lastBox = -1;
		final java.util.List<Long> waiting = new java.util.ArrayList<Long>();

		AnchorFrame(final long source) {
			this.source = source;
		}
	}
	private final java.util.Deque<AnchorFrame> anchors = new java.util.ArrayDeque<AnchorFrame>();

	void setAssignments(final net.zamasoft.foliojet.css.style.running.RunningRegistry assignments) {
		this.assignments = assignments;
	}

	/** 組版入力を一切発生させず、直前の文字か次の配置内容へtokenを結びます。 */
	void assignment(final long order) {
		this.layoutSource.append(new LayoutSource.Assignment(order));
		if (this.anchors.isEmpty()) {
			this.anchors.push(new AnchorFrame(-1));
		}
		final AnchorFrame frame = this.anchors.peek();
		if (frame.lastChar >= 0) {
			this.assignments.bindCharacters(order, frame.lastChar, false);
		} else {
			frame.waiting.add(order);
		}
	}

	/** string-setは代入元の開始アンカーへ結び、runningと同じcommit経路を通します。 */
	void stringAssignments(final java.util.List<net.zamasoft.foliojet.ua.PendingStringSet> strings,
			final net.zamasoft.foliojet.css.CSSStyle style, final net.zamasoft.foliojet.layout.box.IBox source) {
		final long order = strings.get(0).order;
		this.layoutSource.append(new LayoutSource.Assignment(order));
		if (source != null) {
			// 完成テキストはこのアンカーの配置断片から読む(元のInlineBoxは再組版され得る)。
			this.assignments.strings(order, strings);
			this.assignments.bindBox(order, source.getAssignmentAnchor());
		} else {
			// display:contentsには自身の箱がない。自身の入力だけを集め、次の配置へ結ぶ。
			if (strings.stream().anyMatch(value -> value.parts.contains(net.zamasoft.foliojet.ua.PendingStringSet.CONTENT))) {
				final StringBuilder text = new StringBuilder();
				this.contentsSources.put(style, text);
				this.assignments.strings(order, strings, buffer -> {
					buffer.append(text);
					// 配置待ちは完成テキストだけを必要とする。閉じたstyleと親の
					// 計算値配列をlambdaから保持しない(StringBuilderはidentity比較)。
					this.contentsSources.values().remove(text);
				});
			} else {
				this.assignments.strings(order, strings);
			}
			if (!this.anchors.isEmpty()) {
				this.anchors.peek().waiting.add(order);
			}
		}
	}

	private void bindWaitingBox(final long source) {
		if (!this.anchors.isEmpty()) {
			final AnchorFrame frame = this.anchors.peek();
			for (final long order : frame.waiting) {
				this.assignments.bindBox(order, source);
			}
			frame.waiting.clear();
		}
	}

	/**
	 * レイアウトソースプロトコルログです(M6b v3)。E-6増分3b-2:
	 * text payloadのspill予算(processing.text-spill-budget)を注入する。
	 */
	private final LayoutSource layoutSource;
	private FootnotePageProbe probe;
	private boolean inputDelivered;
	private boolean closed;
	private boolean probeFinished;
	private long reportEnd, deliveredEventEnd, consumedWatermark;
	private long resolvedReportGeneration;
	private long reportEventId = -1, windowEventId = -1;
	private long windowDeliveries;
	/** B/Cの世代がずれても入力窓・主ログの保持量を観測できる値だけのhook。 */
	static volatile java.util.function.Consumer<FootnotePageProbe.WindowRetention> windowObserver;
	private final java.util.NavigableMap<Long, net.zamasoft.foliojet.layout.FootnotePageProbeReport> reports = new java.util.TreeMap<>();
	private record Delivery(DocumentBuilder.DispatchEvent type, long id, LayoutSource.Event boundary,
			net.zamasoft.foliojet.layout.box.IBox box, Runnable dispatch, long textBytes) {
		long fromId() { return this.boundary == null ? this.id : this.id - 1; }
	}
	private final java.util.ArrayDeque<Delivery> deliveries = new java.util.ArrayDeque<>();
	private LayoutSource.RetentionLease deliveryLease;
	private net.zamasoft.foliojet.layout.RetainedTextLimit.PageWindow pageWindow;
	private boolean splitCharacters;

	/** Cの初回ページは子を開く前に幾何とpinを確保し、Bの入力から駆動します。 */
	void pageStarted(final net.zamasoft.foliojet.layout.box.impl.PageBox page,
			final double width, final double height, final String pageName,
			final java.util.function.BiFunction<String, Integer, FootnotePageProbe.PageGeometry> geometry) {
		if (this.closed || this.inputDelivered || this.probe != null
				|| page.getUserAgent().getUAContext().getFootnoteArea().isHeightFixed()
				|| !page.getBlockParams().flow.isVertical()
				|| !page.getUserAgent().getUAContext().getFootnoteArea().isPageBand()) return;
		this.pageWindow = page.getUserAgent().getRetainedTextLimit().pageWindow();
		this.deliveryLease = this.layoutSource.retainFrom(0);
		// NFCは入力呼び出し単位で適用されるので、その場合だけ元の文字境界を保つ。
		this.splitCharacters = !net.zamasoft.foliojet.ua.props.UAProps.INPUT_NORMALIZE_TEXT.getBoolean(page.getUserAgent());
		final net.zamasoft.foliojet.ua.UserAgent ua = page.getUserAgent();
		this.probe = new FootnotePageProbe(FootnotePageProbe.PageStart.capture(page, width, height, pageName), this.layoutSource, report -> {
			this.windowEventId = this.reportEventId;
			this.reportEventId = report.eventId();
			this.reportEnd = report.generation();
			if (report.generation() > this.resolvedReportGeneration
					&& report.generation() >= this.doc.getPageGeneration()) this.reports.put(report.generation(), report);
			final var listener = ua.getUAContext().getFootnotePageProbeListener();
			if (listener != null) listener.accept(report);
		});
		this.probe.setPageGeometry(geometry);
		this.probe.observeReports(this.doc::getPageGeneration, this.reports::size);
	}

	private void deliver(final DocumentBuilder.DispatchEvent type, final long id,
			final LayoutSource.Event boundary, final net.zamasoft.foliojet.layout.box.IBox box,
			final Runnable dispatch, final long textBytes) {
		this.inputDelivered = true;
		if (this.probe == null) {
			dispatch.run();
			return;
		}
		this.deliveries.addLast(new Delivery(type, id, boundary, box, dispatch, textBytes));
		this.probe.deliver(type, this.layoutSource.get(id), id, boundary);
		this.observeWindow();
		this.drain(false);
	}

	/**
	 * 世代対応で待てない場合も、Bの一つ前の確定ページの入力までは配達する。
	 * 名前付きページの幾何差でCが多く改頁しても窓を文書全体へ広げない。
	 * 1配達内の複数改頁は止めず、報告のないbeginPageは持ち越しだけで進む。
	 */
	private void drain(final boolean endOfInput) {
		while (!this.deliveries.isEmpty()
				&& (endOfInput || this.reportEnd >= this.doc.getPageGeneration() + 1
						|| this.deliveries.peekFirst().id() <= this.windowEventId)) {
			if (!endOfInput && this.reportEnd < this.doc.getPageGeneration() + 1) ++this.windowDeliveries;
			this.doc.startFootnoteInput();
			final Delivery delivery = this.deliveries.peekFirst();
			this.deliveredEventEnd = delivery.id() + 1;
			// 記録済み境界の元IDで、C自身のlive境界を判定する。再追記はしない。
			final LayoutSource.Event boundary = this.doc.preDispatch(delivery.type(), delivery.box(), delivery.fromId());
			if (!java.util.Objects.equals(delivery.boundary(), boundary)) {
				throw new IllegalStateException("B/Cの匿名境界が一致しません: " + delivery.fromId());
			}
			delivery.dispatch().run();
			this.pageWindow.remove(delivery.textBytes());
			this.deliveries.removeFirst();
			final long from = Math.min(this.deliveries.isEmpty() ? this.deliveredEventEnd : this.deliveries.peekFirst().fromId(),
					this.doc.oldestUnfinishedSourceId());
			final LayoutSource.RetentionLease lease = this.layoutSource.retainFrom(from);
			final boolean retryCompaction = this.deliveryLease.fromId() < this.consumedWatermark
					&& from > this.deliveryLease.fromId();
			this.deliveryLease.close();
			this.deliveryLease = lease;
			if (retryCompaction) this.layoutSource.compact(this.consumedWatermark);
		}
		this.observeWindow();
	}

	private void observeWindow() {
		final var observer = windowObserver;
		if (observer != null && this.pageWindow != null) observer.accept(new FootnotePageProbe.WindowRetention(
				this.pageWindow.currentBytes(), this.deliveries.size(), this.reportEnd, this.doc.getPageGeneration(),
				this.windowDeliveries, this.layoutSource.retentionSnapshot(), this.layoutSource.retainedInlineTextBytes(), this.reports.size()));
	}

	boolean isFootnoteInputDelayed() {
		return this.pageWindow != null;
	}

	double footnoteLineWidth(final net.zamasoft.foliojet.layout.box.impl.PageBox page) {
		final double width = this.probe == null ? Double.NaN : this.probe.namedPageWidth();
		return Double.isNaN(width) ? page.getInnerWidth() : width;
	}

	long deliveredEventEnd() {
		return this.pageWindow == null ? Long.MAX_VALUE : this.deliveredEventEnd;
	}

	/**
	 * Cの開始時に一度だけ消費します。採用・幾何不一致等の不採用とも以後は不要です。
	 * 報告なしで計画を固定した世代への後着報告も保持しません。
	 */
	net.zamasoft.foliojet.layout.FootnotePageProbeReport report(final long generation) {
		this.resolvedReportGeneration = Math.max(this.resolvedReportGeneration, generation);
		this.reports.headMap(generation, false).clear();
		return this.reports.remove(generation);
	}

	boolean probeFinished() {
		return this.probeFinished;
	}

	private void discardProbe() {
		final FootnotePageProbe probe = this.probe;
		this.probe = null;
		if (probe != null) probe.discard();
	}

	void finishProbes() {
		try {
			final boolean complete = this.anchors.stream().noneMatch(frame -> frame.source >= 0);
			if (this.probe != null) this.probe.finishInput(complete);
			this.probeFinished = complete;
			this.drain(true);
		} finally {
			this.discardProbe();
		}
	}

	/**
	 * @param doc 構築<b>完了済み</b>のDocumentBuilder——StyleBuilderは
	 *            doc→sinkの順で構築するため、DocumentBuilderのコンストラクタに
	 *            {@code getLayoutSource()}を呼ぶコールバックを将来足すと
	 *            NPEになる(2026-07-30、agyレビュー指摘の前提明文化)
	 */
	RecordingLayoutSink(final DocumentBuilder doc, final long textSpillBudget) {
		this.doc = doc;
		this.layoutSource = new LayoutSource(textSpillBudget);
		this.events = null;
	}

	/** 反復内容の展開専用です。主ログ・DocumentBuilder・代入状態を所有しません。 */
	RecordingLayoutSink(final java.util.function.Consumer<net.zamasoft.foliojet.layout.segment.SegmentEvent> events) {
		this.doc = null;
		this.layoutSource = null;
		this.events = events;
	}

	LayoutSource source() {
		return this.layoutSource;
	}

	/** 境界判定 → 合成境界追記 → 実イベント追記 → dispatchのlive専用プロトコル。 */
	private LayoutSource.Event preDispatch(final DocumentBuilder.DispatchEvent event,
			final net.zamasoft.foliojet.layout.box.IBox box) {
		if (!this.inputDelivered) this.doc.prepareFootnotePage();
		final LayoutSource.Event boundary = this.probe == null
				? this.doc.preDispatch(event, box, this.layoutSource.nextId())
				: this.probe.preDispatch(event, box, this.layoutSource.nextId());
		if (boundary != null) {
			this.layoutSource.append(boundary);
		}
		return boundary;
	}

	void compact(final long watermark) {
		if (this.pageWindow == null) {
			this.layoutSource.compact(watermark == Long.MAX_VALUE ? this.layoutSource.nextId() : watermark);
			return;
		}
		this.consumedWatermark = Math.max(this.consumedWatermark, Math.min(watermark, this.deliveredEventEnd));
		this.layoutSource.compact(this.consumedWatermark);
	}

	/**
	 * レイアウトソースのspillストア(一時ファイル)を閉じます
	 * (E-6増分3b-2)。冪等。
	 */
	void close() {
		// 入力の停止(以後 B を作らない)と清算を分ける。清算が例外で途中終了しても
		// 後続の dispose の再呼び出しで残りを続けられるよう、`closed` で清算を
		// 省かない(discardProbe は probe を先に null にし、layoutSource.close は
		// 冪等——codex F-2a-2 レビューの必須 1)
		this.closed = true;
		try {
			this.discardProbe();
		} finally {
			try {
				if (this.deliveryLease != null) this.deliveryLease.close();
				if (this.pageWindow != null) this.pageWindow.close();
				this.deliveries.clear();
				this.reports.clear();
				this.layoutSource.close();
			} finally {
				this.anchors.clear();
				this.contentsSources.clear();
				this.sourceBox = null;
				this.sourceElement = null;
				if (this.assignments != null) this.assignments.discardPending();
			}
		}
	}

	/**
	 * ボックスの開始をログに記録してから doc に渡します(M6b v3)。
	 *
	 * <p>
	 * E-6増分3b-4(2026-07-24): 記録時に{@code BoxRecipe.freeze}で
	 * 凍結し、liveのparams/pos参照({@code CSSElement}グラフ含む)を
	 * ログに残さない。params/posの変異は全てこの記録前のStyleBuilder
	 * フェーズに閉じる(codex設計§1.1・独立cross-check済み)ため、
	 * 記録時freezeは従来の再生時共有と同値。freezeは
	 * {@link #boxKind}で列挙する既知kindを網羅し、未知の箱は例外にする。
	 * {@code ReplacedRecipe.freeze}と違い失敗変種({@code StartLive})は
	 * 必要ない。
	 * </p>
	 */
	void start(final INonReplacedBox box) {
		if (this.events != null) {
			final BoxRecipe recipe = this.recordRecipe(box);
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.BeginBox(recipe));
			return;
		}
		if (this.sourceBox == null && box.getParams().element == this.sourceElement) {
			this.sourceBox = box;
		}
		// 主ログもrunningも同じ凍結契約。未知の箱・配置は明確に失敗させる。
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.START_BOX, box);
		final BoxRecipe recipe = this.recordRecipe(box);
		box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Start(recipe)));
		this.bindWaitingBox(box.getSourceAnchor());
		this.anchors.push(new AnchorFrame(box.getSourceAnchor()));
		if (box.getParams() instanceof AbstractTextParams params) {
			this.anchors.peek().whiteSpace = params.whiteSpace;
		}
		this.deliver(DocumentBuilder.DispatchEvent.START_BOX, box.getSourceAnchor(), boundary, box,
				() -> this.doc.startBox(box), 0);
	}

	/**
	 * 置換要素をログに記録してから doc に渡します(M6b v3)。
	 * 記録しないと、置換要素を含む部分木が「再生可能」に見えて内容が
	 * 失われる(サイレントホールの防止)。
	 *
	 * <p>
	 * E-6増分3b-3(2026-07-24): 記録時に{@code ReplacedRecipe.freeze}で
	 * 凍結し、liveボックスへの参照をログに残さない(params/posの変異は
	 * この記録前のStyleBuilderフェーズに閉じるため、記録時freezeは
	 * 従来の再生時共有と同値——codex設計§1.5・独立cross-check済み)。
	 * E-6増分3b-6: {@code ReplacedBoxImage}参照のボックスもduplicate
	 * ベースでfreezeできるようになり(live型{@code ReplacedLive}は撤去)、
	 * freezeが空を返すのは未知の{@code AbstractReplacedBox}サブクラス
	 * のみ(現存4実装では構造的にゼロ)。その場合はfail closedで
	 * replay不能マーカー({@code Opaque}+対の{@code EndBlock}——
	 * {@code Opaque}は開始イベントとして{@code EndBlock}と対を成す規約
	 * のため単独では積めない)として位置を占有し、範囲にこれを含む
	 * TwoPassのsealは変換失敗になる。
	 * </p>
	 */
	void replaced(final AbstractReplacedBox box) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Replaced(
					ReplacedRecipe.freeze(box).orElseThrow()));
			return;
		}
		if (this.sourceBox == null && box.getParams().element == this.sourceElement) {
			this.sourceBox = box;
		}
		for (final StringBuilder text : this.contentsSources.values()) {
			box.getText(text);
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.REPLACED, box);
		final java.util.Optional<ReplacedRecipe> recipe = ReplacedRecipe.freeze(box);
		if (recipe.isPresent()) {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Replaced(recipe.get())));
		} else {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Opaque()));
			this.layoutSource.append(new LayoutSource.EndBlock());
		}
		this.bindWaitingBox(box.getSourceAnchor());
		if (!this.anchors.isEmpty()) {
			this.anchors.peek().lastBox = box.getSourceAnchor();
			this.anchors.peek().lastChar = -1;
			this.anchors.peek().tailChar = -1;
		}
		this.deliver(DocumentBuilder.DispatchEvent.REPLACED, box.getSourceAnchor(), boundary, box,
				() -> this.doc.addReplacedBox(box), 0);
	}

	/**
	 * ボックスの終了をログに記録してから doc に渡します(M6b v3)。
	 */
	void end() {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.EndBox());
			return;
		}
		final AnchorFrame frame = this.anchors.peek();
		if (frame != null) {
			if (frame.tailChar >= 0) {
				for (final long order : frame.waiting) {
					this.assignments.bindCharacters(order, frame.tailChar, false);
				}
				frame.waiting.clear();
			} else {
				this.bindWaitingBox(frame.lastBox >= 0 ? frame.lastBox : frame.source);
			}
			this.anchors.pop();
			if (!this.anchors.isEmpty()) {
				this.anchors.peek().lastBox = frame.source;
				this.anchors.peek().lastChar = -1;
				this.anchors.peek().tailChar = frame.tailChar;
			}
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.END_BOX, null);
		final long id = this.layoutSource.append(new LayoutSource.EndBlock());
		this.deliver(DocumentBuilder.DispatchEvent.END_BOX, id, boundary, null, this.doc::endBox, 0);
	}

	/**
	 * {@code leader()}をログに記録してから doc に渡します(leader() L1)。
	 */
	void leader(final String pattern) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Leader(pattern));
			return;
		}
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.LEADER, null);
		final long id = this.layoutSource.append(new LayoutSource.Leader(pattern));
		this.deliver(DocumentBuilder.DispatchEvent.LEADER, id, boundary, null, () -> this.doc.addLeader(pattern), 0);
	}

	/**
	 * テキストをログに記録してから doc に渡します(M6b v3)。
	 */
	void characters(final int charOffset, final char[] ch, final int off, final int len, final boolean fixed) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Text(
					charOffset, new String(ch, off, len), fixed));
			return;
		}
		if (!this.inputDelivered) this.doc.prepareFootnotePage();
		if (this.probe != null && this.splitCharacters && len > 256) {
			for (int offset = 0; offset < len;) {
				int size = Math.min(256, len - offset);
				if (offset + size < len && Character.isHighSurrogate(ch[off + offset + size - 1])
						&& Character.isLowSurrogate(ch[off + offset + size])) --size;
				this.characters(charOffset < 0 ? charOffset : charOffset + offset, ch, off + offset, size, fixed);
				offset += size;
			}
			return;
		}
		for (final StringBuilder text : this.contentsSources.values()) {
			text.append(ch, off, len);
		}
		if (!this.anchors.isEmpty() && charOffset < 0) {
			final AnchorFrame frame = this.anchors.peek();
			for (int i = 0; i < len; ++i) {
				if (ch[off + i] == '\n' && preserved('\n', frame.whiteSpace, fixed)) {
					// br等の生成改行にはソース文字がない。古い行のアンカーを再利用しない。
					frame.lastChar = frame.tailChar = -1;
				}
			}
		}
		if (!this.anchors.isEmpty() && charOffset >= 0 && len > 0) {
			final AnchorFrame frame = this.anchors.peek();
			// pre系の空白/改行は配置されるControl。縮退する行端空白だけを除く。
			int first = 0;
			while (first < len && !preserved(ch[off + first], frame.whiteSpace, fixed)) {
				++first;
			}
			if (first < len) {
				int last = len - 1;
				while (last > first && !preserved(ch[off + last], frame.whiteSpace, fixed)) {
					--last;
				}
				for (final long order : frame.waiting) {
					this.assignments.bindCharacters(order, charOffset + first, true);
				}
				frame.waiting.clear();
				frame.lastChar = frame.tailChar = charOffset + last;
				if (ch[off + last] == '\n') {
					// 改行の後の原位置は次の行。改行自身が既に出力済みでも次の配置を待つ。
					frame.lastChar = -1;
				}
			}
		}
		// E-6増分3b-2: 防御コピー・spill判定(予算制)はLayoutSourceが行う
		final LayoutSource.Event boundary = this.preDispatch(DocumentBuilder.DispatchEvent.TEXT, null);
		final long bytes = this.probe == null ? 0 : 2L * len;
		if (this.pageWindow != null) this.pageWindow.add(bytes);
		final long id = this.layoutSource.appendChars(charOffset, ch, off, len, fixed);
		if (this.probe == null) {
			this.deliver(DocumentBuilder.DispatchEvent.TEXT, id, boundary, null,
					() -> this.doc.characters(charOffset, ch, off, len, fixed), 0);
		} else {
			final char[] copy = java.util.Arrays.copyOfRange(ch, off, off + len);
			this.deliver(DocumentBuilder.DispatchEvent.TEXT, id, boundary, null,
					() -> this.doc.characters(charOffset, copy, 0, copy.length, fixed), bytes);
		}
	}

	private static boolean preserved(final char c, final byte whiteSpace, final boolean fixed) {
		return !TextUtils.isWhiteSpace(c) || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE
				|| whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_WRAP
				|| c == '\n' && (fixed || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_LINE);
	}


	/** 主ログと独立再生で共有する総関数。未知の箱・配置は変換失敗。 */
	private BoxRecipe recordRecipe(final INonReplacedBox box) {
		try {
			return boxRecipe(box);
		} catch (final net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException cause) {
			final long eventId = this.layoutSource == null ? -1 : this.layoutSource.nextId();
			final var failure = new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					cause.getMessage() + " " + (this.doc == null ? "uri=<unknown> owner state=RECORDING" : this.doc.sourceOwnerContext())
							+ " EventId=[" + eventId + "," + eventId + "]");
			failure.initCause(cause);
			throw failure;
		}
	}

	private static BoxRecipe boxRecipe(final INonReplacedBox box) {
		final LayoutSource.BoxKind kind = boxKind(box);
		return switch (kind) {
		case TABLE -> {
			final var table = (net.zamasoft.foliojet.layout.box.impl.TableBox) box;
			final var block = table.getBlockBox();
			if (block.getParams() != table.getTableParams()) throw unsupportedBox(box);
			final LayoutSource.BoxKind placement = boxKind(block);
			final boolean supported = switch (placement) {
			case FLOW -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FlowPos.class;
			case FLOAT_BLOCK -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FloatPos.class;
			case INLINE_BLOCK -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.InlinePos.class;
			case ABSOLUTE -> block.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.AbsolutePos.class;
			case MULTICOL, INLINE, MARKER, INSIDE_MARKER, TABLE, TABLE_ROW_GROUP, TABLE_ROW,
					TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, GRID, CAPTION, FLEX -> false;
			};
			if (!supported) throw unsupportedBox(box);
			yield placement == LayoutSource.BoxKind.FLOW ? BoxRecipe.freeze(kind, box)
					: new BoxRecipe.PlacedTable(
							net.zamasoft.foliojet.layout.segment.TableParamsTemplate.freeze(table.getTableParams()),
							BoxRecipe.freeze(placement, block));
		}
		case FLOW, MULTICOL, INLINE, MARKER, FLOAT_BLOCK, INLINE_BLOCK, INSIDE_MARKER, TABLE_ROW_GROUP,
				TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN, GRID, CAPTION, FLEX, ABSOLUTE ->
				BoxRecipe.freeze(kind, box);
		};
	}

	/** 非sealed階層なので既知の実クラスを列挙し、未知のsubclassも拒否する。 */
	private static LayoutSource.BoxKind boxKind(final INonReplacedBox box) {
		return switch (box) {
		case net.zamasoft.foliojet.layout.box.impl.FlowBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class -> box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.TableCaptionPos
				? LayoutSource.BoxKind.CAPTION : LayoutSource.BoxKind.FLOW;
		case net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox.class -> LayoutSource.BoxKind.MULTICOL;
		case net.zamasoft.foliojet.layout.box.impl.GridBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.GridBox.class -> plainItemHost(box, LayoutSource.BoxKind.GRID);
		case net.zamasoft.foliojet.layout.box.impl.FlexBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FlexBox.class -> plainItemHost(box, LayoutSource.BoxKind.FLEX);
		case net.zamasoft.foliojet.layout.box.impl.InlineBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InlineBox.class -> LayoutSource.BoxKind.INLINE;
		case net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox.class -> LayoutSource.BoxKind.MARKER;
		case net.zamasoft.foliojet.layout.box.impl.FloatBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.FloatBlockBox.class -> LayoutSource.BoxKind.FLOAT_BLOCK;
		case net.zamasoft.foliojet.layout.box.impl.InlineBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InlineBlockBox.class -> LayoutSource.BoxKind.INLINE_BLOCK;
		case net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox.class -> LayoutSource.BoxKind.INSIDE_MARKER;
		case net.zamasoft.foliojet.layout.box.impl.TableBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableBox.class -> LayoutSource.BoxKind.TABLE;
		case net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox.class -> LayoutSource.BoxKind.TABLE_ROW_GROUP;
		case net.zamasoft.foliojet.layout.box.impl.TableRowBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableRowBox.class -> LayoutSource.BoxKind.TABLE_ROW;
		case net.zamasoft.foliojet.layout.box.impl.TableCellBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableCellBox.class -> LayoutSource.BoxKind.TABLE_CELL;
		case net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox.class -> LayoutSource.BoxKind.TABLE_COLUMN_GROUP;
		case net.zamasoft.foliojet.layout.box.impl.TableColumnBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.TableColumnBox.class -> LayoutSource.BoxKind.TABLE_COLUMN;
		case net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox known
				when known.getClass() == net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox.class -> LayoutSource.BoxKind.ABSOLUTE;
		default -> throw unsupportedBox(box);
		};
	}

	private static LayoutSource.BoxKind plainItemHost(final INonReplacedBox box, final LayoutSource.BoxKind kind) {
		if (box.getPos().getClass() != net.zamasoft.foliojet.layout.box.params.FlowPos.class) throw unsupportedBox(box);
		return kind;
	}

	private static net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException unsupportedBox(
			final INonReplacedBox box) {
		return new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
				"Unsupported box recipe: box kind=" + box.getClass().getName() + " pos=" + box.getPos().getClass().getName());
	}
}
