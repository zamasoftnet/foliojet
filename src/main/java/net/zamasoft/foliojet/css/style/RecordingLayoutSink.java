package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;
import net.zamasoft.foliojet.layout.util.TextUtils;

/**
 * M6b v3 のレイアウトソースプロトコルtee——doc入力プロトコル
 * (StartBlock/Chars/EndBlock)を{@link LayoutSource}へ記録した<b>直後に</b>
 * 同じイベントを{@link DocumentBuilder}へ渡します(StyleBuilder解体・
 * 増分1で抽出、2026-07-30。挙動は抽出前と同一)。
 *
 * <p>
 * 記録と引き渡しの<b>順序とfreeze時点が契約</b>である——記録は
 * {@code LayoutSource.append/freeze}の直後に{@code doc}へ渡す。
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

	/** clearは直前の文字でなく、宣言を持つ要素自身の配置へ結びます。 */
	void clearAssignments(final java.util.List<String> names, final net.zamasoft.foliojet.layout.box.IBox source) {
		final long order = this.assignments.nextOrder();
		this.assignments.clear(order, names);
		this.layoutSource.append(new LayoutSource.Assignment(order));
		if (source != null) {
			this.assignments.bindBox(order, source.getAssignmentAnchor());
		} else if (!this.anchors.isEmpty()) {
			this.anchors.peek().waiting.add(order);
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
	private void preDispatch(final DocumentBuilder.DispatchEvent event,
			final net.zamasoft.foliojet.layout.box.IBox box) {
		final LayoutSource.Event boundary = this.doc.preDispatch(event, box, this.layoutSource.nextId());
		if (boundary != null) {
			this.layoutSource.append(boundary);
		}
	}

	void compact(final long watermark) {
		this.layoutSource.compact(watermark == Long.MAX_VALUE ? this.layoutSource.nextId() : watermark);
	}

	/**
	 * レイアウトソースのspillストア(一時ファイル)を閉じます
	 * (E-6増分3b-2)。冪等。
	 */
	void close() {
		this.layoutSource.close();
		this.anchors.clear();
		this.contentsSources.clear();
		this.sourceBox = null;
		this.sourceElement = null;
		if (this.assignments != null) {
			this.assignments.discardPending();
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
		this.preDispatch(DocumentBuilder.DispatchEvent.START_BOX, box);
		final BoxRecipe recipe = this.recordRecipe(box);
		box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Start(recipe)));
		this.bindWaitingBox(box.getSourceAnchor());
		this.anchors.push(new AnchorFrame(box.getSourceAnchor()));
		if (box.getParams() instanceof AbstractTextParams params) {
			this.anchors.peek().whiteSpace = params.whiteSpace;
		}
		this.doc.startBox(box);
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
		this.preDispatch(DocumentBuilder.DispatchEvent.REPLACED, box);
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
		this.doc.addReplacedBox(box);
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
		this.preDispatch(DocumentBuilder.DispatchEvent.END_BOX, null);
		this.layoutSource.append(new LayoutSource.EndBlock());
		this.doc.endBox();
	}

	/**
	 * {@code leader()}をログに記録してから doc に渡します(leader() L1)。
	 */
	void leader(final String pattern) {
		if (this.events != null) {
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.Leader(pattern));
			return;
		}
		this.preDispatch(DocumentBuilder.DispatchEvent.LEADER, null);
		this.layoutSource.append(new LayoutSource.Leader(pattern));
		this.doc.addLeader(pattern);
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
		this.preDispatch(DocumentBuilder.DispatchEvent.TEXT, null);
		this.layoutSource.appendChars(charOffset, ch, off, len, fixed);
		this.doc.characters(charOffset, ch, off, len, fixed);
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
