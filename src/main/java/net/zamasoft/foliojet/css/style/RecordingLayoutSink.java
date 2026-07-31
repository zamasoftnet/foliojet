package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.BoxRecipe;
import net.zamasoft.foliojet.layout.segment.ReplacedRecipe;

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
		final java.util.Optional<ReplacedRecipe> recipe = ReplacedRecipe.freeze(box);
		if (recipe.isPresent()) {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Replaced(recipe.get())));
		} else {
			box.setSourceAnchor(this.layoutSource.append(new LayoutSource.Opaque()));
			this.layoutSource.append(new LayoutSource.EndBlock());
		}
		this.doc.addReplacedBox(box);
	}

	/**
	 * ボックスの終了をログに記録してから doc に渡します(M6b v3)。
	 */
	void end() {
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
		// E-6増分3b-2: 防御コピー・spill判定(予算制)はLayoutSourceが行う
		this.layoutSource.appendChars(charOffset, ch, off, len, fixed);
		this.doc.characters(charOffset, ch, off, len, fixed);
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
