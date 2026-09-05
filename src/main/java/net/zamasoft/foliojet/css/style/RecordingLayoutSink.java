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
					this.contentsSources.remove(style);
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
	 * {@link #boxKind}が非nullを返す全13 kindをカバーする総関数で、
	 * {@code ReplacedRecipe.freeze}と違い失敗変種({@code StartLive})は
	 * 必要ない。
	 * </p>
	 */
	void start(final INonReplacedBox box) {
		if (this.events != null) {
			final LayoutSource.BoxKind kind = boxKind(box);
			if (kind == null && box instanceof net.zamasoft.foliojet.layout.box.impl.TableBox table) {
				final var block = table.getBlockBox();
				final var placement = boxKind(block);
				if (placement != null) {
					this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.BeginBox(new BoxRecipe.PlacedTable(
							net.zamasoft.foliojet.layout.segment.TableParamsTemplate.freeze(table.getTableParams()),
							BoxRecipe.freeze(placement, block.getParams(), block.getPos()))));
					return;
				}
			}
			if (kind == null) {
				throw new IllegalArgumentException("running: unsupported box " + box.getClass().getSimpleName());
			}
			final var pos = kind == LayoutSource.BoxKind.TABLE
					? ((net.zamasoft.foliojet.layout.box.impl.TableBox) box).getBlockBox().getPos() : box.getPos();
			this.events.accept(new net.zamasoft.foliojet.layout.segment.SegmentEvent.BeginBox(
					BoxRecipe.freeze(kind, box.getParams(), pos)));
			return;
		}
		if (this.sourceBox == null && box.getParams().element == this.sourceElement) {
			this.sourceBox = box;
		}
		// recipe化できる種別は Start(recipe) として記録し、未対応の種別
		// (表キャプション・非適格な表等)は Opaque として位置だけ占有する
		// (範囲に Opaque を含む再生はフォールバック)
		final LayoutSource.BoxKind kind = boxKind(box);
		if (kind != null) {
			// TABLEだけは「外側TableBoxのTablePos」ではなく「内側blockBoxの
			// FlowPos」を凍結する(G-1の記録契約の是正、2026-07-30復活)。
			// TableBox.getPos()はfinalで常にTablePos.POSを返し配置種別を
			// 持たない——配置(FLOW/FLOAT/INLINE/ABSOLUTE)は内側blockBox側に
			// ある。再生側(BoxRecipeBoxFactoryのTABLE分岐)がparamsを共有して
			// new TableBox(params, new FlowBlockBox(params, pos))を作る構造と
			// 対になる。外側posのままだと(FlowPos)キャストでCCEになる
			final net.zamasoft.foliojet.layout.box.params.Pos pos = kind == LayoutSource.BoxKind.TABLE
					? ((net.zamasoft.foliojet.layout.box.impl.TableBox) box).getBlockBox().getPos()
					: box.getPos();
			box.setSourceAnchor(this.layoutSource
					.append(new LayoutSource.Start(BoxRecipe.freeze(kind, box.getParams(), pos))));
		} else {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Opaque()));
		}
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
	 * 再生はフォールバックする。
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
		this.layoutSource.append(new LayoutSource.EndBlock());
		this.doc.endBox();
	}

	/**
	 * {@code leader()}をログに記録してから doc に渡します(leader() L1)。
	 */
	void leader(final String pattern) {
		this.layoutSource.append(new LayoutSource.Leader(pattern));
		this.doc.addLeader(pattern);
	}

	/**
	 * テキストをログに記録してから doc に渡します(M6b v3)。
	 */
	void characters(final int charOffset, final char[] ch, final int off, final int len, final boolean fixed) {
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
		this.layoutSource.appendChars(charOffset, ch, off, len, fixed);
		this.doc.characters(charOffset, ch, off, len, fixed);
	}

	private static boolean preserved(final char c, final byte whiteSpace, final boolean fixed) {
		return !TextUtils.isWhiteSpace(c) || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE
				|| whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_WRAP
				|| c == '\n' && (fixed || whiteSpace == AbstractTextParams.WHITE_SPACE_PRE_LINE);
	}

	/**
	 * ソース再生で再インスタンス化できるボックス種別を返します
	 * (未対応なら null = Opaque 記録)。SourceReplayer.newBox と対。
	 * 完全一致比較を維持する——未知のsubclassはfail-closedでOpaqueにする。
	 */
	private static LayoutSource.BoxKind boxKind(final INonReplacedBox box) {
		final Class<?> type = box.getClass();
		if (type == net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class) {
			if (box.getPos() instanceof net.zamasoft.foliojet.layout.box.params.TableCaptionPos) {
				// 表キャプションは実行時クラスこそ FlowBlockBox だが、再生時に
				// TableBuilder.newContext() を経由しないと正しく配置できず、
				// 単独(表を伴わない)再生では DocumentBuilder.tableBuilder() が
				// 例外を投げる(builderStack の先頭が TableBuilder でない)。
				// 表本体と同じく Opaque とし、単独再生の対象から外す。
				//
				// F-4(2026-07-25): キャプションの recipe 化を単独で行っても
				// 得るものはない。キャプションは必ず表要素の内側にあり
				// (DocumentBuilder の TABLE_CAPTION 分岐が tableBuilder() を
				// 要求する)、その表自身が下の TableBox 分岐で Opaque として
				// 記録されるため、キャプションを含む範囲は表の Opaque で
				// 既に containsOpaque=true になる。実測の内訳も表310に対し
				// キャプション9で、後者は前者の真部分集合。
				//
				// G-1(2026-07-25): キャプションの recipe 化を実際に試作して
				// 実測したところ【クラッシュした】(実測2文書)。原因は
				// 「キャプションが単独 replay 範囲の根になれてしまう」こと
				// ——replay の適格判定は範囲が Opaque/float/absolute/multicol/
				// mixed-flow を含むかしか見ておらず、「囲みビルダーなしで
				// startBox できない種別か」を見ていないため、moved caption が
				// そのまま SourceReplayer.replay へ渡り
				// DocumentBuilder.tableBuilder() が「表構造の外」で落ちる。
				// caption recipe化C1(2026-08-01、consult-codex-2026-08-01-
				// caption-recipe.txt): G-1の単独replay根クラッシュは「根禁止」
				// ではなく「同一範囲内に対応するTABLE Startの確立を要求する
				// context-complete検証」で防ぐ設計が確定した。C1ではrecipe
				// 記録に切り替えつつ、routing不変のため再生側の全ゲート
				// (stampRanges/TwoPass seal/canReplayChildren)へ
				// containsCaptionの一律拒否を敷く——C2でcontext-complete検証へ
				// 置換するまでキャプションを含む範囲は従来どおりbox-restyle。
				// CAPTION_OPAQUE_RECORDSは0になる(C0の観測値9→0が
				// recipe化の証明)
				return LayoutSource.BoxKind.CAPTION;
			}
			return LayoutSource.BoxKind.FLOW;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox.class) {
			return LayoutSource.BoxKind.MULTICOL;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.GridBox.class) {
			// Grid G0c: exact class+素のFlowPosのみ(他のサブクラス・posは
			// fail closedでOpaqueへ)
			if (box.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FlowPos.class) {
				return LayoutSource.BoxKind.GRID;
			}
			return null;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.FlexBox.class) {
			// Flex F0c: Gridと同じくexact class+素のFlowPosのみ
			if (box.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FlowPos.class) {
				return LayoutSource.BoxKind.FLEX;
			}
			return null;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.InlineBox.class) {
			return LayoutSource.BoxKind.INLINE;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox.class) {
			return LayoutSource.BoxKind.MARKER;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.FloatBlockBox.class) {
			return LayoutSource.BoxKind.FLOAT_BLOCK;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.InlineBlockBox.class) {
			return LayoutSource.BoxKind.INLINE_BLOCK;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox.class) {
			return LayoutSource.BoxKind.INSIDE_MARKER;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableBox.class) {
			// 表セット(2026-07-30、G-1裁定のユーザー承認による更新):
			// 内側blockBoxが素のFlowBlockBox+素のFlowPos(通常フロー配置)
			// かつparams alias成立の表だけをrecipe記録する。再生側
			// (BoxRecipeBoxFactoryのTABLE分岐)が作れるのは
			// new TableBox(params, new FlowBlockBox(params, FlowPos))だけの
			// ため、float/inline-table/absolute配置の表・非aliasはfail
			// closedでOpaqueのまま。記録側は内側posを渡す契約(start参照)。
			final net.zamasoft.foliojet.layout.box.impl.TableBox tableBox = (net.zamasoft.foliojet.layout.box.impl.TableBox) box;
			final net.zamasoft.foliojet.layout.box.AbstractBlockBox blockBox = tableBox.getBlockBox();
			if (blockBox.getClass() == net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class
					&& blockBox.getPos().getClass() == net.zamasoft.foliojet.layout.box.params.FlowPos.class
					&& blockBox.getParams() == tableBox.getTableParams()) {
				return LayoutSource.BoxKind.TABLE;
			}
			return null;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox.class) {
			return LayoutSource.BoxKind.TABLE_ROW_GROUP;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableRowBox.class) {
			return LayoutSource.BoxKind.TABLE_ROW;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableCellBox.class) {
			return LayoutSource.BoxKind.TABLE_CELL;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox.class) {
			return LayoutSource.BoxKind.TABLE_COLUMN_GROUP;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.TableColumnBox.class) {
			return LayoutSource.BoxKind.TABLE_COLUMN;
		}
		if (type == net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox.class) {
			// 絶対配置ブロック(E-6増分4e、2026-07-24)。記録の主目的は
			// 絶対配置ビルダー自身の本文range seal(旧Opaque記録では
			// endOfが引けずTwoPassSealReject.NO_RANGE——4b実測131件中81件の
			// 最大残件)。絶対配置を「含む」範囲の再生可否は
			// LayoutSource.containsAbsoluteゲートが従来どおり
			// フォールバックさせる(係留・deferred bindの二重化防止)。
			// 絶対配置の「表」はTableBoxで記録されるため従来どおりOpaque
			return LayoutSource.BoxKind.ABSOLUTE;
		}
		return null;
	}
}
