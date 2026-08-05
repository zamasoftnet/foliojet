package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 表示リスト(Drawerのダンプ)のgolden比較テストです。
 * レイアウトの幾何・描画順の回帰を、画像比較より厳密に検出します。
 *
 * <p>
 * 基準データは files/unittest/display-list-golden/ 以下にあります。
 * 意図的なレイアウト変更で更新する場合は、該当ディレクトリを削除して
 * このテストを実行すると再生成されます(再生成した実行は失敗扱いになります)。
 * </p>
 */
public class DisplayListGoldenTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 対象文書。ブロック・フロート・テーブル・縦書き・段組・生成内容をカバーする。 */
	private static final String[] DOCUMENTS = { //
			// 固定幅の子が兄弟の内在寸法を消していた欠陥(2026-08-04)。
			// フロート/セル/絶対/フレックス/inline-block を横に並べて押さえる
			"0080-width/intrinsic-fixed-sibling.html", //
			// 負の width/height 属性(2026-08-05)。attr() は計算値の段階でしか
			// 解けないので parseValue の非負検査を素通りし、表が最小内容幅へ潰れていた
			"0080-width/negative-attr-width.html", //
			"0120-float/auto-width.html", //
			// margin:auto の表の直後の浮動体(2026-08-05)。行方向カーソルの
			// 押し引きが非対称で、フロートが x=-106.75(紙の左外)へ飛んでいた
			"0120-float/after-auto-margin-table.html", //
			"0120-float/nested-float-shrink.html", //
			"0120-float/collapse-float-measure.html", //
			"0120-float/float-in-moved-block.html", //
			"0460-segment-restyle/mid-paragraph.html", //
			"0460-segment-restyle/moved-blocks.html", //
			"0460-segment-restyle/text-tail-avoid.html", //
			"0460-segment-restyle/float-in-moved.html", //
			"0460-segment-restyle/float-split-in-chain.html", //
			"0460-segment-restyle/float-uncut-before-prefix.html", //
			"0460-segment-restyle/nested-break-in-replay.html", //
			"0460-segment-restyle/moved-table-caption.html", //
			"0390-writing-mode/vert-cell-specified-pagebreak.html", //
			"0390-writing-mode/vert-fixed-colgroup-spacing.html", //
			"0390-writing-mode/orthogonal-cell-fixed.html", //
			"0240-table/z-order.html", //
			"0240-table/rowspan-after-empty-row.html", //
			// 表の属性(frame/rules/align/valign/bordercolor)をCSSへ移した際の
			// 罫線の座標を固定する(2026-08-04)
			"0240-table/frame-rules.html", //
			"3080-MODERN-CSS/layer-important.html", //
			"0242-table-height/percent-rowspan-groups.html", //
			"0242-table-height/group-size-empty-rows.html", //
			"0242-table-height/zero-percent-row-rowspan.html", //
			"0330-table-border/collapse-asymmetric-fixed.html", //
			"0330-table-border/collapse-group-inner-lines.html", //
			"0330-table-border/collapse-multi-groups.html", //
			"0330-table-border/collapse-rowspan-spacing.html", //
			"0330-table-border/collapse-illegal.html", //
			"0240-table/absolute.html", //
			"0219-pagebreak-table-inrow/valign-split.html", //
			// ルビ=注釈付きテキスト(2026-07-25仕様裁定)。単位<行のため
			// ページ境界は段落の行分割で自然に泣き別れする(旧箱方式の
			// 「同方向ルビ本文は分割可」特別契約は消滅)
			"3060-RUBY/ruby-split-through.html", //
			// ルビ単位の組み立て(複数rb/rtの対応づけ・断片の書式・
			// ネスト・片側だけのmalformed・縦書き)を直接固定する
			"3060-RUBY/ruby-annotation.html", //
			"0219-pagebreak-table-inrow/valign-split-vert.html", //
			"0390-writing-mode/border-collapse.html", //
			"0390-writing-mode/absolute.html", //
			"0400-column-count/nest.html", //
			"0350-line-height/small-line-height.html", //
			"0140-content/counters.html", //
			// 読めない形式の @font-face src を飛ばすこと(2026-08-05)。
			// 読み込みが非同期なので、失敗しても次の候補へ落ちていなかった
			"1080-FONT/font-face-format.html", //
			"0450-hyphens/hyphens.html", //
			"0450-hyphens/word-then-paren.html", //
			// 入れ子のフレックス(2026-08-05)。項目自身が宣言した幅を内在寸法に
			// 数えておらず、空きが負になって中の項目が幅0へ潰れていた
			"0510-flex/nested-flex-child-lost.html", //
			"0510-flex/float-item.html", //
			// flexアイテムのpadding・marginの実寸解決(2026-08-04)。行方向では
			// どちらも丸ごと消えていた——縦方向は別経路で偶然効いていたので
			// 片方向だけ見ても捕まらない
			"0510-flex/item-padding-margin.html", //
			"0470-margin-boxes/margin-boxes.html", //
			// 属性の前方/後方/部分一致の大文字小文字(2026-08-05)。両辺を
			// 小文字化しており li[type^="a"] と li[type^="A"] が共に当たっていた
			"3000-SELECTOR/attr-prefix-case.html", //
			"3000-SELECTOR/nth.html", //
			"3000-SELECTOR/dir.html", //
			"3000-SELECTOR/html5-elements.html", //
			"3080-MODERN-CSS/initial-unset.html", //
			"3080-MODERN-CSS/calc.html", //
			// calc()の中のフォント相対単位(2026-08-03)。em/remは計算値の
			// 段階でしか解けないので、絶対成分・割合成分と分けて持ち回る
			"3080-MODERN-CSS/calc-font-relative.html", //
			// 型付きattr()(2026-08-03)。HTMLの表現属性をCSSから扱う土台
			"3080-MODERN-CSS/typed-attr.html", //
			// 論理境界プロパティ(2026-08-03)。border-block-end等12個
			"3080-MODERN-CSS/logical-borders.html", //
			"3000-SELECTOR/is-not-where-sibling.html", //
			"3070-AT-RULE/media-supports.html", //
			// @page の marks / bleed(2026-08-02)。CSSから指定した断ち代の
			// 分だけ紙面が広がり、版面がその内側へ寄ることを座標で固定する
			"3070-AT-RULE/page-marks-bleed.html", //
			"3080-MODERN-CSS/logical-properties.html", //
			"3000-SELECTOR/is-not-where-descendant.html", //
			"3080-MODERN-CSS/var.html", //
			// CSS Nesting(2026-08-02)。子孫結合・&(先頭/非先頭/複合)・
			// 相対セレクタ・セレクタリスト直積・3層入れ子・入れ子後宣言の
			// 順序保存を要素幅で固定する
			"3080-MODERN-CSS/nesting.html", //
			// @counter-style(2026-08-02)。cyclic/fixed(範囲外fallback)/
			// additive/numeric+pad+negative/alphabetic/extendsの表現と
			// prefix・suffix、counter()経路(prefix/suffixなし)を固定する
			"3080-MODERN-CSS/counter-style.html", //
			// 標準名の別名とcounter-set(2026-08-02)。overflow-wrapの
			// normal/break-word/anywhere・counter-setが入れ子を作らずに
			// 既存カウンタへ代入すること・未知の名前はその要素に作られること
			"3080-MODERN-CSS/standard-aliases.html", //
			// フォーム部品の幾何(2026-08-02)。ボタンのラベルが箱の内側の
			// どこに置かれるか、入力欄・選択・複数行の寸法を固定する
			"3080-MODERN-CSS/form-controls.html", //
			// 救済分割(2026-07-25、増分5)。ページ先頭でもはみ出す置換要素を
			// 幾何学的に切って次ページへ送る唯一の経路——断片の座標・
			// artifact印・ページ数をここで固定する
			// HTMLに直接書いたSVG(2026-08-06)。xmlns を書かないと名前空間の
			// 宣言が組み立て器へ渡らず、丸ごと描画されなかった
			"3050-IMG/inline-svg-implicit-ns.html", //
			"3050-IMG/rescue-tall.html", //
			"3050-IMG/rescue-tall-vert.html", //
			"3050-IMG/rescue-exact.html", //
			"3050-IMG/rescue-huge.html", //
			"3050-IMG/rescue-absolute.html", //
			"3050-IMG/rescue-column.html", //
			// 救済分割(2026-07-25、増分6/7)。巨大な行・書字方向不一致
			// ブロック・表セル・段組・浮動体まで広げた各種別と、
			// 「ちょうど割り切れる高さ」で余分なページを作らないこと
			"0480-rescue-split/huge-font-line.html", //
			"0480-rescue-split/huge-font-exact.html", //
			// 脚注(F0〜F5、2026-07-31)。call/markerラベル・脚注領域の座標・
			// ページ毎採番・本文短縮・carry-in番号保持を描画順ごと固定する
			"0125-footnote/footnote-f1.html", //
			"0125-footnote/footnote-pagereset.html", //
			"0125-footnote/footnote-pagelimit.html", //
			"0125-footnote/footnote-carryin.html", //
			"0125-footnote/footnote-vertical-rl.html", //
			// ページフロート(2026-08-02)。float: bottomが版面下端(脚注が
			// あればその上)へ、float: topが次ページ先頭へ置かれ、以後の
			// フローがその下から始まることを座標で固定する
			"0125-footnote/page-float.html", //
			// Grid G1(2026-07-31)。固定トラックの列開始・行開始・gap・
			// Grid総高(後続ブロックの位置)と、不適格Grid(1fr)のG0
			// フォールバック+atomicページ送りを固定する
			"0500-grid/fixed-2x2.html", //
			"0500-grid/mixed-items.html", //
			"0500-grid/auto-columns.html", //
			"0500-grid/intrinsic-auto-fr.html", //
			"0500-grid/grid-in-float.html", //
			"0500-grid/grid-in-float-shrink.html", //
			"0500-grid/nested-grid.html", //
			"0500-grid/explicit-columns.html", //
			"0500-grid/explicit-rows-sparse.html", //
			"0500-grid/explicit-overlap.html", //
			"0500-grid/empty-row-gap.html", //
			"0500-grid/explicit-placement-in-float.html", //
			"0500-grid/row-span-auto.html", //
			"0500-grid/alignment-items.html", //
			"0500-grid/alignment-content.html", //
			"0500-grid/alignment-in-float.html", //
			"0500-grid/oversized-atomic.html", //
			// 絶対配置のdisplayブロック化(2026-08-02)。position:absoluteの
			// display(flex/grid/table-row/table-cell/inline-block/list-item)
			// が例外にならず、静的位置がブロックとして決まることを固定する
			// ——実在のページ(yahoo.co.jp)のクラッシュ回帰
			"0500-grid/absolute-flex-grid.html", //
			// 和文詰めA2(2026-07-31)。text-autospaceのgap(0.125em)を
			// run境界のx座標で固定する(off/on/numeric限定/明示空白抑止)
			"0510-text-spacing/autospace-horizontal.html", //
			"0510-text-spacing/autospace-vertical.html", //
			"0510-text-spacing/autospace-in-float.html", //
			// 和文詰めT1b(2026-07-31)。text-spacing-trim: normal(=T1aで
			// 移管した詰め)とspace-all(全角のまま)の対比を固定する
			"0510-text-spacing/trim-pairs.html", //
			// 和文詰めT2/H1(2026-07-31)。行末ぶら下げ(allow-end)の
			// 追い込みと、行末trimの条件付き半角化を固定する
			"0510-text-spacing/hanging-end.html", //
			// 名前付きページN1b(2026-07-31)。rootのpage名で@page chapterの
			// 柱が出て、chapter:firstが特異性で先頭ページに勝つこと・
			// 無名@pageのマージンは継承合成されることを固定する
			"0520-named-page/named-margin-box.html", //
			// N2a: page名遷移の強制改ページ(author breakとの合成で二重に
			// 送らない)と、無名への復帰を固定する
			"0520-named-page/transition-no-double-break.html", //
			// N3/N4: @page sizeのlandscape章(可変ページ寸法)。柱の中央位置
			// (=ページ幅/2)がportrait→landscape→portraitで往復することを
			// 固定する
			"0520-named-page/landscape-section.html", //
			// N2b: 無名ラッパー内の深部で名前が変わっても遷移が伝播し、
			// A→B→A が3セクションになることを固定する
			"0520-named-page/nested-transition.html", //
			// N2b: 明示改ページ直後(ページ先頭)の名前遷移。旧名の白紙
			// ページは落ち、新名の柱とlandscape寸法で作り直されることを
			// 固定する
			"0520-named-page/head-transition.html", //
			// N2b: 文書先頭の名前付き内容。無名の初期ページが白紙のまま
			// 差し替わり、1ページ目から新名の柱と寸法になることを固定する
			"0520-named-page/doc-head-transition.html", //
			// leader() L1(2026-07-31): dotted/solid/custom、残余の割り付け、
			// 行末原点の位相揃え(1桁/2桁の行でドット列が縦に揃う)を固定する
			"0530-leader/basic.html", //
			// leader() H1: 長い章題の折り返し(リーダーは最終行のみ)と
			// 短い行の対比を固定する
			"0530-leader/wrap.html", //
			// leader() H1: 同一行の複数leaderが残余を等分することを固定する
			"0530-leader/multiple.html", //
			// leader() H1: leader行はjustifyの伸長が≈0(先に残余を消費)、
			// 通常行のjustifyは退行しないことを固定する
			"0530-leader/justify.html", //
			// leader() V1: vertical-rlでの割り付け(軸中立)と縦描画を固定する
			"0530-leader/vertical.html", //
			// leader() H1: 改ページを跨ぐ目次(@page sizeで小ページ化)。
			// 範囲再生でLeaderイベントが再駆動され幅が漏れないことを固定する
			"0530-leader/replay.html", //
			"0500-grid/atomic-move.html", //
			"0480-rescue-split/tall-inline-block.html", //
			"0480-rescue-split/orthogonal-block.html", //
			"0480-rescue-split/orthogonal-block-exact.html", //
			"0480-rescue-split/cell-tall-image.html", //
			"0480-rescue-split/cell-tall-image-exact.html", //
			"0480-rescue-split/column-tall-block.html", //
			"0480-rescue-split/column-tall-block-exact.html", //
			// ページ自体が縦書きのときの断片座標(2026-07-25追加)。既存の
			// 縦書きfixtureはいずれも「横書きページの中の縦書きブロック」で、
			// VisualRescueBox.sourceDrawXの縦書き分岐を一度も通していなかった
			"0480-rescue-split/vertical-page-rl.html", //
			// vertical-lr は vertical-rl の鏡映になる(2026-07-25にLRを実装)
			"0480-rescue-split/vertical-page-lr.html", //
			"0390-writing-mode/vertical-lr-blocks.html", //
			// セル連結(2026-07-25、独立レビュー+ランダム検査で見つけた3件)。
			// (a) rowspanが行グループを越えてtbody先頭セルを消していた
			// (b) 連結セルの座標がRL専用式で、vertical-lrで表の外へずれていた
			"0495-span/rowspan-crosses-rowgroup.html", //
			"0495-span/rowspan-vertical-lr.html", //
			"0495-span/rowspan-vertical-rl.html", //
			// (c) 強制改ページで連結セルが次ページに現れなかった
			"0495-span/rowspan-forced-break.html", //
			// (d) 空行の後ろのrowspanが2行で打ち切られる——**未解決**。
			// goldenは現状(誤った出力)の記録であり正しさの記録ではない。
			// 直したら差分が出るので、そのとき更新すること
			"0495-span/rowspan-after-empty-row.html", //
			// (e) 列数を超えるcolspan × つぶし境界、縦書き×fixed×rowspan。
			// いずれも独立レビュー指摘だが**再現は取れなかった**——
			// goldenは「今後変わったら気づく」ための固定
			"0495-span/colspan-beyond-columns-collapse.html", //
			"0495-span/rowspan-vertical-fixed.html", //
			"0480-rescue-split/float-tall.html", //
			"0480-rescue-split/float-exact.html", //
	};

	/**
	 * processing.pass-count&gt;=2を要する文書(STRUCTURE_SCANを使う
	 * :has()/:last-child系。docs/PLAN.md「2パス制御モード」参照)。
	 * 上のDOCUMENTSとは別枠にしているのは、既定のpass-count=1のまま
	 * 全文書に一律でpass-count=2を課すと無関係な文書のコストが増えるため。
	 */
	private static final String[] MULTI_PASS_DOCUMENTS = { //
			"3000-SELECTOR/last-child-family.html", //
			"3000-SELECTOR/has.html", //
	};

	public void testDisplayLists() throws Exception {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
		List<String> failures = new ArrayList<>();
		for (String doc : DOCUMENTS) {
			checkDocument(doc, 1, failures);
		}
		for (String doc : MULTI_PASS_DOCUMENTS) {
			checkDocument(doc, 2, failures);
		}
		reportTwoPassRangeBind(failures);
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/**
	 * TwoPass range化(E-6増分4b、production default-on)のコーパス実測
	 * レポートと配線検証です。golden一致だけでは「常にLegacyRecordsへ
	 * フォールバックしている」空虚な緑と区別できないため、(a)range bind
	 * がこのコーパスで実際に発火していること、(b)seal適格数とrange bind数が
	 * 一致すること(sealで取得したRetentionLeaseが全てbindのfinallyで解放
	 * された証拠——取り残しはcompactを永久にclampする)を固定する。
	 */
	private static void reportTwoPassRangeBind(List<String> failures) {
		if (Boolean.getBoolean("foliojet.noTwoPassRangeBind")) {
			return; // kill switch下では対象経路が無効
		}
		final long seals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get();
		final long rangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.RANGE_FIRST_BINDS.get();
		final long cellSeals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_SEALS.get();
		final long cellRangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_BINDS.get();
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 two-pass range bind / golden corpus]\n");
		s.append("  RANGE_FIRST_BINDS=").append(rangeBinds).append('\n');
		s.append("  LEGACY_RECORD_BINDS=")
				.append(net.zamasoft.foliojet.layout.fragment.ContinuationStats.LEGACY_RECORD_BINDS.get()).append('\n');
		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(seals).append('\n');
		s.append("  CELL_RANGE_SEALS=").append(cellSeals).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(cellRangeBinds).append('\n');
		s.append("  CELL_LEGACY_BINDS=")
				.append(net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_LEGACY_BINDS.get()).append('\n');
		// E-6増分5b-2: 表Pass C(行単位逐次bind)の表単位採用率
		final long passCTables = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_PASS_C_TABLES.get();
		final long legacyBindRows = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_LEGACY_BINDROWS.get();
		s.append("  TABLE_PASS_C_TABLES=").append(passCTables).append('\n');
		s.append("  TABLE_LEGACY_BINDROWS=").append(legacyBindRows).append('\n');
		s.append("  TABLE_PASS_B_CELL_MEASURES=")
				.append(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TABLE_PASS_B_CELL_MEASURES.get())
				.append('\n');
		long total = seals;
		for (final net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject r : net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject
				.values()) {
			final long count = net.zamasoft.foliojet.layout.fragment.ContinuationStats.twoPassSealRejects(r);
			total += count;
			s.append("  REJECT_").append(r).append('=').append(count).append('\n');
		}
		s.append("  ELIGIBLE_RATE=").append(seals).append('/').append(total).append('\n');
		System.err.print(s);
		if (rangeBinds == 0) {
			failures.add("TwoPass range bindがgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// DP増分3: 親range化に吸収された子seal(SUBSUMED)はbindされずに
		// リースを手放すため、完了条件はseals == binds + subsumed
		final long subsumed = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get();
		System.err.println("  TWO_PASS_SEALS_SUBSUMED=" + subsumed);
		if (seals != rangeBinds + subsumed) {
			failures.add("seal適格数とrange bind+吸収数が一致しません(リース取り残しの疑い): seals=" + seals
					+ ", rangeBinds=" + rangeBinds + ", subsumed=" + subsumed);
		}
		// E-6増分5a: 表セルのrange化の配線検証+リース1:1検出。コーパスは
		// auto表(0240/0242/0330等)を含むためセルsealが実際に発火する
		if (cellSeals == 0) {
			failures.add("表セルのrange seal(E-6増分5a)がgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// 表吸収(codex増分5): 親range化に吸収されたセルseal(SUBSUMED)は
		// bindされずにリースを手放すため、完了条件はseals == binds + subsumed
		final long cellSubsumed = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_SEALS_SUBSUMED
				.get();
		System.err.println("  CELL_RANGE_SEALS_SUBSUMED=" + cellSubsumed);
		if (cellSeals != cellRangeBinds + cellSubsumed) {
			failures.add("セルseal数とセルrange bind+吸収数が一致しません(セルのリース取り残しの疑い): cellSeals="
					+ cellSeals + ", cellRangeBinds=" + cellRangeBinds + ", cellSubsumed=" + cellSubsumed);
		}
		// E-6増分5b-2: 表Pass C(行単位逐次bind)の配線検証。コーパスは全実セル
		// 適格のRetained表を含むため、Pass Cが一度も発火しないのは空虚な緑
		if (passCTables == 0) {
			failures.add("表Pass C(E-6増分5b-2)がgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// 増分10(2026-07-30): 表の全セル先行bind(旧経路)はこのコーパスで
		// 0を固定する。表吸収(codex増分5)・absolute吸収(増分9)により
		// 全Retained表がPass C適格になった——旧経路はseal不適格セル
		// (キャプション付き表入りセル等、野生文書で発生しうる)向けの
		// 安全fallbackとして残す(bindRecordsと同じ終着形。物理撤去は
		// 「不適格を変換失敗にする」仕様変更を伴うため不採用——
		// クラッシュ排除・変換失敗排除の絶対要件)
		if (legacyBindRows != 0) {
			failures.add("表の全セル先行bind(旧経路)がgoldenコーパスで発火しました(増分10の0固定の退行): "
					+ legacyBindRows);
		}
	}

	private void checkDocument(String doc, int passCount, List<String> failures) throws Exception {
		String name = doc.replace('/', '_').replace(".html", "");
		File outDir = new File("local/unittest/display-list/" + name);
		deleteChildren(outDir);
		File goldenDir = new File("files/unittest/display-list-golden/" + name);

		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			this.transcode(new File("files/unittest/" + doc), name, passCount);
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + doc, pages);
		assertTrue("表示リストが出力されていません: " + doc, pages.length > 0);

		if (!goldenDir.isDirectory()) {
			// 基準データの初回生成
			goldenDir.mkdirs();
			for (File page : pages) {
				Files.copy(page.toPath(), new File(goldenDir, page.getName()).toPath());
			}
			failures.add(doc + ": 基準データを生成しました。内容を確認してコミットしてください: " + goldenDir);
			return;
		}

		File[] goldenPages = goldenDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (goldenPages.length != pages.length) {
			failures.add(
					doc + ": ページ数が基準と異なります (golden=" + goldenPages.length + ", actual=" + pages.length + ")");
			return;
		}
		for (File golden : goldenPages) {
			Path actual = new File(outDir, golden.getName()).toPath();
			String expected = Files.readString(golden.toPath(), StandardCharsets.UTF_8);
			String got = Files.readString(actual, StandardCharsets.UTF_8);
			if (!expected.equals(got)) {
				failures.add(doc + "/" + golden.getName() + ": 表示リストが基準と一致しません (expected=" + golden
						+ ", actual=" + actual + ")");
			}
		}
	}

	private void transcode(File source, String name, int passCount) throws Exception {
		File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (passCount > 1) {
					session.property("processing.pass-count", String.valueOf(passCount));
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteChildren(File dir) {
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			child.delete();
		}
	}
}
