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
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
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
			// 読み込みに失敗したimgがCSSのwidth/heightを無視して0x0に
			// 縮退する欠陥(2026-08-06)。詳細はfixture自身のコメント参照
			"0080-width/broken-image-declared-size.html", //
			// 改ページを跨いだ相対配置(2026-08-06)。確定ページの容器が寸法決めの
			// 走査から外れると、ずらし量が0のまま静かに出ていた
			"0170-position/relative-offset-after-break.html", //
			// 絶対配置の子のwidth:100%がbutton親とdiv親で同じ幅に解決される
			// こと(2026-08-07、yahoo.co.jpの検索候補ドロップダウンで発覚)
			"0170-position/absolute-width-in-button.html", //
			"0120-float/auto-width.html", //
			"0120-float/sliver-overflow-stays.html", //
			"0120-float/floats-only-block-first-child-move.html", //
			"0120-float/table-beside-start-float.html", //
			// 行の途中の行末側フロートの同一行配置(2026-08-08、kabutan)。
			// 収まる場合は現在行の上端(variant A〜E)、収まらない場合は
			// 従来の次の帯(variant F)
			"0120-float/float-end-midline.html", //
			// margin:auto の表の直後の浮動体(2026-08-05)。行方向カーソルの
			// 押し引きが非対称で、フロートが x=-106.75(紙の左外)へ飛んでいた
			"0120-float/after-auto-margin-table.html", //
			"0120-float/nested-float-shrink.html", //
			"0120-float/collapse-float-measure.html", //
			"0120-float/float-in-moved-block.html", //
			// shape-outside: circle(50%)(css-shapes-1、2026-08-29)。行が円の
			// 弦に沿って始まり、円の下で左端へ戻ること
			"0120-float/shape-outside-circle.html", //
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
			// CSS Writing Modes: mixed/upright/sidewaysで縦/横font sourceの
			// run分離と論理inline送りが変わることを固定する
			"0390-writing-mode/text-orientation.html", //
			// 直交ブロックのページ軸%は親の線軸基準(2026-08-10修正の固定)
			"0390-writing-mode/orthogonal-page-axis-percent.html", //
			// キャプションの箱は表の border box(margin box ではない)。
			// 左マージンのある表でキャプションが外へ広がっていた(2026-08-30)
			"0240-table/caption-table-margin.html", //
			// フォント相対単位 cap / ic / ric / rlh の計算値(2026-08-30)。
			// 実フォントのcap-heightと根のline-heightが要るので、宣言の
			// 解析テストでは押さえられない
			"3020-VALUE/font-relative-units.html", //
			"0240-table/z-order.html", //
			// 1行だけの縦長ラッパー表は先頭ページの残量で行の内容を分割する
			// (2026-08-27)。UA既定のセルpage-break-inside:avoid撤去の回帰。
			// 表全体が次ページへ送られ先頭ページがほぼ白紙になっていた
			// (kawasaki-ombuds)
			"0240-table/single-row-split-after-line.html", //
			"0240-table/rowspan-after-empty-row.html", //
			// 表の属性(frame/rules/align/valign/bordercolor)をCSSへ移した際の
			// 罫線の座標を固定する(2026-08-04)
			"0240-table/frame-rules.html", //
			"3080-MODERN-CSS/layer-important.html", //
			// content-visibility(2026-08-11): hiddenは中身だけ省く
			"3080-MODERN-CSS/content-visibility.html", //
			// 固有寸法キーワード max-content/min-content/fit-content(L)
			// (2026-08-29)。通常フローのブロックが内容幅で止まること、
			// 浮動体・inline-block・絶対配置・改ページ継続断片での幅を固定する
			"3080-MODERN-CSS/width-max-content.html", //
			"3080-MODERN-CSS/width-min-content.html", //
			"3080-MODERN-CSS/width-fit-content.html", //
			"3080-MODERN-CSS/width-intrinsic-float-inline-abs.html", //
			"3080-MODERN-CSS/width-intrinsic-page-split.html", //
			// ビューポート単位・env()・display別名・%グリッドトラック・
			// currentColor(2026-08-29)
			"3080-MODERN-CSS/viewport-env-aliases.html", //
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
			"3060-RUBY/ruby-advanced.html", // CSS Ruby Level 1: merge/align/overhang/position/rtc
			"3060-RUBY/warichu.html", // JLREQ 3.4: Copper拡張による横/縦の2段割注と禁則
			// JLREQ 3.5.5/3.5.6: 標準HTML/CSSによる添え字と振分け、
			// 3.6.3: ルビを行間へ出して基準行位置を保つこと
			"0510-text-spacing/jlreq-composed-features.html", //
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
			// 版面が埋まってブロックが閉じられる行の分綴ハイフン(2026-09-01)。
			// 通常の行溢れと違い drawLine(true) を通るため実体化が止まっていた
			"0450-hyphens/hyphen-at-page-break.html", //
			"0450-hyphens/word-then-paren.html", //
			// 入れ子のフレックス(2026-08-05)。項目自身が宣言した幅を内在寸法に
			// 数えておらず、空きが負になって中の項目が幅0へ潰れていた
			"0510-flex/nested-flex-child-lost.html", //
			"0510-flex/float-item.html", //
			// flexアイテムのpadding・marginの実寸解決(2026-08-04)。行方向では
			// どちらも丸ごと消えていた——縦方向は別経路で偶然効いていたので
			// 片方向だけ見ても捕まらない
			"0510-flex/item-padding-margin.html", //
			// inline-flexのブロックレベル近似(2026-08-11)
			"0510-flex/inline-flex.html", //
			// 縦中横: allは1em幅へ収める。horizontalと手動横組みは自然幅
			// のまま(2026-08-11)
			"0500-ext-css/text-combine-all.html", //
			// resolveRelativeOffsetの代入がFlex/Gridの主軸配置を上書きする
			// 欠陥(2026-08-06)。AbstractContainerBox.javaのjavadoc参照。
			// 2番目以降のitemが原点(x=0)へ潰れて重なっていた
			"0510-flex/row-position-static-offset.html", //
			// flex行分割(2026-08-07、Bug C)。行を跨ぐ強制分割で、同じ行の
			// item同士が同一切断線に揃わず階段状にずれていた
			"0510-flex/row-split-across-break.html", //
			// 継続断片のitem auto margin再解決の禁止(2026-08-27)。restyle
			// 再構築のcalculateSizeがmargin:0 autoをブロック則で再解決し、
			// 位置の二重シフト+restoreExtentsの枠肥大で内寸が世代ごとに
			// 縮んでいた(asahi.com記事の本文カラムが1文字幅へ潰れた)
			"0510-flex/auto-margin-item-fragmentation.html", //
			// paddingを持つflexコンテナの表セル内在幅(2026-08-08)。
			// IntrinsicMeasurerのflex/grid/table寄与がlineFrameを足して
			// おらず、枠ぶん狭くなってGitHubのファイル名が切れていた
			"0510-flex/padded-flex-in-auto-cell.html", //
			// 絶対配置子を含むflex行のページ跨ぎ丸ごと移動(2026-08-08、
			// yahoo)。restyle再構築でも2セルが同じ上端に整列し、絶対配置の
			// 数字だけを持つmin-width itemの寸法が保たれることを固定する
			"0510-flex/pushed-row-absolute-child.html", //
			// flex-basis:calc(50% - 16px)の解決(2026-08-08、asahi)。旧実装は
			// calcを一律auto扱いでmin-contentへ潰れた
			"0510-flex/basis-calc.html", //
			// ページ跨ぎ分割の継続断片がflexで縮んだ使用幅を保つ(2026-08-08、
			// asahi)。指定width:100%の%再解決で固定幅サイドが紙面外へ
			// 押し出されていた
			"0510-flex/split-item-keeps-flexed-width.html", //
			// %寸法の置換要素のmin-content寄与は0(2026-08-08、asahi)。旧実装は
			// 自然幅がminを吊り上げflex itemが縮めなくなっていた
			"0510-flex/percent-image-min-content.html", //
			// 入れ子flexコンテナitemの行方向寸法指定(2026-08-08、asahi)。
			// 中立wrapperが寸法を引き取り、子は充填で解決する
			"0510-flex/nested-container-item-width.html", //
			// min-width付き入れ子コンテナのmin寄与(2026-08-08、NHKナビ)。
			// 旧実装はflex-shrinkでmin-width未満に縮み背景が隣へ重なった
			"0510-flex/min-width-nested-container.html", //
			// box-sizing:border-boxのmin/max-widthは枠込み(2026-08-29)。通常フロー
			// のブロック(FlowBlockBox)が枠を二重に足して116pxに広がっていた
			"0510-flex/min-width-border-box-block.html", //
			// 入れ子コンテナitemの主軸autoマージン(2026-08-09)。中立wrapperが
			// authoredのautoマージンを引き取らず.ml-autoの右寄せが効かなかった
			// (5ch.ioヘッダの実バグ)
			"0510-flex/auto-margin-nested-container.html", //
			// 固定高itemを持つflex行のページ跨ぎ分割(2026-08-09)。
			// specifiedPageAxis不設定でFragmentStateが指定高を残量へ分割せず、
			// 継続断片が指定高をフル再解決して行が膨らんでいた
			"0510-flex/fixed-height-item-split.html", //
			// %幅の置換要素のflex item(2026-08-09)。中立wrapperがauthored寸法を
			// 引き取らず、内在寸法なしsvgのwidth:100%が二パス計測の0のまま
			// 幅0へ潰れていた(NHKナビのシェブロン空箱の後半)
			"0510-flex/percent-replaced-item.html", //
			// 空白を含む行の行箱が指定line-heightを超えない(2026-08-09、
			// pdfg2d)。WhiteSpace controlのメトリクスがフォントリスト最大で
			// 単語(先頭フォント)と不整合、空白入りの行だけ+4〜5%伸びていた
			"0350-line-height/space-run-height.html", //
			// place-items/place-self/place-contentショートハンド(2026-08-09)。
			// NHKナビのボタンのplace-items:centerが宣言無効でアイコンが
			// 左上に寄っていた
			"0500-grid/place-shorthand.html", //
			// Grid拡張(2026-08-29、50サイト掃過): grid-area+grid-template-areas+
			// 線名+grid-template-rows、%とrepeat(N,%)トラック、auto-fill/auto-fit、
			// grid-auto-flow:column+grid-auto-columns+grid-gap別名
			"0500-grid/area-template.html", //
			"0500-grid/percent-repeat-tracks.html", //
			"0500-grid/auto-fill.html", //
			"0500-grid/auto-flow-column.html", //
			// minmax()の両端(css-grid-1 §11.5)・subgrid(css-grid-2)・
			// grid-template/gridショートハンド・image-set()(2026-08-29)
			"0500-grid/minmax.html", //
			"0500-grid/subgrid.html", //
			"0500-grid/subgrid-rows.html", //
			"0500-grid/grid-shorthand.html", //
			"0500-grid/row-stretch.html", //
			"3080-MODERN-CSS/image-set.html", //
			// aspect-ratio(2026-08-29): 幅確定→高さ、高さ確定→幅、置換要素、
			// border-box、内容あふれ
			"3080-MODERN-CSS/aspect-ratio.html", //
			"0470-margin-boxes/margin-boxes.html", //
			// 縦書きの左右マージンボックス(2026-09-06、利用者申し送り §4)。19056 まで
			// 流れが TB 固定で柱が帯幅で折り返し同じ x に重なった。vertical-align は
			// 天地(y)、x は帯の中央、padding/margin で折り返さない(Vivliostyle 実測)
			"0470-margin-boxes/vertical-side-boxes.html", //
			// 属性の前方/後方/部分一致の大文字小文字(2026-08-05)。両辺を
			// 小文字化しており li[type^="a"] と li[type^="A"] が共に当たっていた
			"3000-SELECTOR/attr-prefix-case.html", //
			// %のtranslate成分のfreeze/materialize持ち越し(2026-08-08、
			// yahoo検索ボタンの虫眼鏡)。記録再生を通るインライン文脈の
			// 絶対配置のtranslateY(-50%)がgoldenのtf=行列に現れることを
			// 固定する(ダンプ座標はGC変換前のため、tf=出力が無いと
			// transform退行は一切見えない)
			"0490-transform/percent-translate-inline-context.html", //
			// 割合translateと他関数の併用(2026-08-29)。translate(-50%,-50%)
			// scale(1.1)が丸ごと無効になっていた。交差成分と3D縮退も固定する
			"0490-transform/percent-translate-with-scale.html", //
			// 個別変換プロパティ translate/rotate/scale と zoom(2026-08-29)。
			// 合成順 T·R·S·transform と、transformの割合成分がR·Sで写ること、
			// zoomが左上原点の拡大であることを固定する(期待行列はfixture内)
			"0490-transform/individual-properties.html", //
			// tab-size(2026-08-29)。数値=空白幅の倍数、長さ、0。2行目のタブが
			// 1行目と同じタブ位置へそろうこと
			"0050-white-space/tab-size.html", //
			// dialog:not([open])のUA既定(2026-08-07)。open属性のない
			// ネイティブダイアログが紙面に露出しないことを固定する
			"0130-display/dialog-closed.html", //
			// 閉じたdetailsはsummary以外を出さない(2026-08-08)。bbc.comの
			// no-JSナビ露出で発覚。開いたdetailsとの対比を固定する
			"0130-display/details-closed.html", //
			// display:contents(2026-08-07)。箱の透過・継承・flexアイテム化・
			// 表の中のラッパー・置換要素のnone化を固定する。MDNの
			// main{display:contents}で本文が全滅していた欠陥の回帰
			"0130-display/contents-basic.html", //
			"3000-SELECTOR/nth.html", //
			"3000-SELECTOR/dir.html", //
			"3000-SELECTOR/html5-elements.html", //
			"3080-MODERN-CSS/initial-unset.html", //
			"3080-MODERN-CSS/calc.html", //
			// calc()の中のフォント相対単位(2026-08-03)。em/remは計算値の
			// 段階でしか解けないので、絶対成分・割合成分と分けて持ち回る
			"3080-MODERN-CSS/calc-font-relative.html", //
			// font-size/line-heightに書かれたフォント相対単位入りcalc()
			// (2026-08-09)。root font-size縮小イディオムcalc(1em * 0.625)が
			// 捨てられrem全寸法が1.6倍になる欠陥(e-gov)と、%混在line-heightの
			// ClassCastExceptionの回帰
			"3080-MODERN-CSS/calc-font-size.html", //
			// lh単位(2026-08-27)。単独・calc内・line-height自身の自己参照
			// (継承値基準)・transformの近似解決を固定する。MDNの外部リンク
			// アイコン(translateY(calc(.5lh - .5em)))が上へずれていた欠陥の回帰
			"3080-MODERN-CSS/lh-unit.html", //
			// bodyの無いHTML断片(2026-08-09、e-Gov法令HTML)。TagBalancerの
			// html/body合成が開いた要素の内側へ注入され木が崩壊していた
			"3030-FRAGMENT/body-less-fragment.html", //
			// mask-imageのグラデーション近似(2026-08-09)。本文抜粋の
			// フェードアウト・イディオムをボックスクリップで近似する。
			// 5ch.ioでマスク無視によりはみ出し本文が後続へ重なった欠陥の回帰
			"3080-MODERN-CSS/mask-image-clip.html", //
			// overflow:scroll/autoのブラウザ同様クリップ(2026-08-09オーナー
			// 裁定)。従来ははみ出しをそのまま描き、絶対配置のタブ見出し等が
			// 全展開の中身と重なっていた(asahi p-tab)
			"0025-selector/invalid-list.html", //
			"0040-overflow/scroll-clip.html", //
			"0040-overflow/axis-properties.html", //
			// text-overflow: ellipsis(2026-08-29)。nowrap行の行末クリップと
			// 省略記号の追加描画、clip既定・overflow:visibleでの不適用を固定
			"0040-overflow/text-overflow-ellipsis.html", //
			// line-clamp / -webkit-line-clamp(2026-08-29)。N行目の末尾の省略記号、
			// N+1行目以降の抑止(ブロック高さ=N行)、N行未満の段落には付かないこと
			"0040-overflow/line-clamp.html", //
			// text-decoration-style/-thickness/text-underline-offset/-position
			// (2026-08-29)。装飾線は表示リストに幾何が出ないので行の配置の固定のみ
			// (線種の画素検査はTextDecorationStyleTest)
			"0160-text-decoration/decoration-styles.html", //
			// text-shadowのぼかし(2026-08-29)。表示リストは影を含まないので
			// 幾何の固定のみ(ぼかしの広がりはTextShadowBlurTestの画素検査)
			"0150-text-shadow/blur.html", //
			// clip-path: path()(2026-08-29)。px座標のSVGパスがptへ換算され
			// 参照ボックス左上を原点にクリップ矩形へ現れること
			"3080-MODERN-CSS/clip-path-path.html", //
			// mix-blend-mode(2026-08-29)。描画要素単位の近似——表示リストには
			// 出ないので幾何の固定のみ(合成結果はMixBlendModeTestの画素検査)
			"3080-MODERN-CSS/mix-blend-mode.html", //
			// radial/conic/repeating gradient・多層背景・filter(2026-08-29)。
			// 塗りの要約(bg=)とフィルタの字面(filter=)が枠の描画要素に出る
			"3080-MODERN-CSS/gradients.html", //
			"3080-MODERN-CSS/filter.html", //
			// flex/gridコンテナのbuttonへUAのZWSP(::before)を注入しない
			// (2026-08-09)。ZWSPが独立itemになりアイコンを箱外へ押し出していた
			"3080-MODERN-CSS/button-flex-grid-content.html", //
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
			// background shorthandの/cover・/containとアルファ付き背景色の
			// GCスコープ(2026-08-27。asahi.comの動画サムネイル黒化2件)
			"3080-MODERN-CSS/bg-shorthand-size-and-alpha.html", //
			// insetショートハンドとinset:0+margin:autoの絶対配置センタリング
			// (2026-08-27。asahi.comの再生アイコンが左上へ寄った)
			"3080-MODERN-CSS/inset-center.html", //
			// viewBoxのみのSVG背景のcontain制約(2026-08-27。asahi.com
			// フッターのRe:Ronロゴが原寸のまま箱からはみ出ていた)
			"3080-MODERN-CSS/bg-svg-intrinsic.html", //
			// 実サイトの警告から拾った穴の一括固定(2026-08-29): flow-root、
			// 8桁hex、padding:inherit、word-break:break-word、margin-inline、
			// -webkit-mask、background 4値position、font-kerning、isolate、
			// ベンダ接頭辞の段組
			"3080-MODERN-CSS/real-site-gaps.html", //
			// box-shadow(外側・内側・複数・角丸)とoutline(offset・auto)の
			// 描画物の有無と付記(2026-08-29。塗りの段数はBoxDecorationTest)
			"3080-MODERN-CSS/box-shadow-outline.html", //
			// flex/gridコンテナのfloat回避(2026-08-27。独立整形文脈は
			// floatと重ならない——asahi.comフッターのラベル重なり)
			"0510-flex/container-avoids-float.html", //
			// 救済分割(2026-07-25、増分5)。ページ先頭でもはみ出す置換要素を
			// 幾何学的に切って次ページへ送る唯一の経路——断片の座標・
			// artifact印・ページ数をここで固定する
			// HTMLに直接書いたSVG(2026-08-06)。xmlns を書かないと名前空間の
			// 宣言が組み立て器へ渡らず、丸ごと描画されなかった
			// 読み込めないobjectの子(フォールバック内容)が描かれること
			// (2026-08-07)。AltTextImage導入(2026-08-06)でobjectが置換
			// ボックス化され子が消えていた——acid2の目の消失として発覚
			"3050-IMG/object-fallback.html", //
			// object-fit/object-position(2026-08-27)。cover/contain/none/
			// scale-downの実描画矩形とクリップ、object-positionの
			// キーワード・%・長さ・単一値(y=center)を固定する。
			// jigensha.infoのサムネイルがcover無視で引き伸びていた欠陥の回帰
			"3050-IMG/object-fit.html", //
			"3050-IMG/inline-svg-implicit-ns.html", //
			// viewBoxのみ(width/height属性なし)のインラインSVGを、CSSクラス/
			// インラインstyleでサイズ指定(2026-08-06)。属性読み出しがXHTML
			// 名前空間限定で、foreign content配下の属性を読み落としていた
			"3050-IMG/svg-css-size-no-attrs.html", //
			// 逆スラッシュエスケープ入りのクラスセレクタ(2026-08-06)。ph-cssが
			// セレクタの生文字列をそのまま返し、CSS識別子エスケープを解決しない
			// ため、Tailwindのバリアント接頭辞(hover:・lg:・[&_svg]:等)由来の
			// クラスがHTMLのclass属性(エスケープ無し)と常に不一致だった
			"3050-IMG/svg-escaped-class-selector.html", //
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
			"0125-footnote/footnote-bottom-vertical-rl.html", // F-1: 持ち越し注を地の横書き帯へ
			// ページフロート(2026-08-02)。float: bottomが版面下端(脚注が
			// あればその上)へ、float: topが次ページ先頭へ置かれ、以後の
			// フローがその下から始まることを座標で固定する
			"0125-footnote/page-float.html", //
			"0125-footnote/page-margin-note-horizontal.html", // JLREQ横組の傍注（論理行末側）
			"0125-footnote/page-margin-note-vertical.html", // JLREQ縦組の頭注（論理行頭側）
			// Grid G1(2026-07-31)。固定トラックの列開始・行開始・gap・
			// Grid総高(後続ブロックの位置)と、不適格Grid(1fr)のG0
			// フォールバック+atomicページ送りを固定する
			"0500-grid/fixed-2x2.html", //
			"0500-twopass-range/t4b-cell-parent.html",
			"0500-twopass-range/t4b-flex-anonymous.html",
			"0500-twopass-range/t4b-flex-column-nowrap.html",
			"0500-twopass-range/t4b-flex-column-wrap.html",
			"0500-twopass-range/t4b-flex-middle-normal.html",
			"0500-twopass-range/t4b-flex-middle-pushed.html",
			"0500-twopass-range/t4b-flex-neutral-column.html",
			"0500-twopass-range/t4b-flex-neutral-row.html",
			"0500-twopass-range/t4b-flex-neutral-sealed-float.html",
			"0500-twopass-range/t4b-flex-neutral-sealed-inline-block.html",
			"0500-twopass-range/t4b-flex-row-nowrap.html",
			"0500-twopass-range/t4b-flex-row-wrap.html",
			"0500-twopass-range/t4b-flex-sealed-float.html",
			"0500-twopass-range/t4b-flex-sealed-inline-block.html",
			"0500-twopass-range/t4b-flex-takeover-content.html",
			"0500-twopass-range/t4b-float-parent.html",
			"0500-twopass-range/t4b-grid-anonymous.html",
			"0500-twopass-range/t4b-grid-neutral-roots.html",
			"0500-twopass-range/t4b-grid-neutral-sealed-float.html",
			"0500-twopass-range/t4b-grid-neutral-sealed-inline-block.html",
			"0500-twopass-range/t4b-grid-sealed-float.html",
			"0500-twopass-range/t4b-grid-sealed-inline-block.html",
			"0500-twopass-range/t4b-grid-span-areas.html",
			"0500-twopass-range/t4b-grid-takeover-content.html",
			"0500-twopass-range/t4b-grid-tracks-column.html",
			"0500-twopass-range/t4b-grid-tracks-fixed.html",
			"0500-twopass-range/t4b-grid-tracks-fr.html",
			"0500-twopass-range/t4b-item-lifecycle.html",
			"0500-twopass-range/t4b-anon-flex-fit-content-mixed.html",
			"0500-twopass-range/t4b-anon-flex-fit-content-text.html",
			"0500-twopass-range/t4b-anon-flex-max-content-mixed.html",
			"0500-twopass-range/t4b-anon-flex-max-content-text.html",
			"0500-twopass-range/t4b-anon-grid-fit-content-mixed.html",
			"0500-twopass-range/t4b-anon-grid-fit-content-text.html",
			"0500-twopass-range/t4b-anon-grid-max-content-mixed.html",
			"0500-twopass-range/t4b-anon-grid-max-content-text.html",
			"0500-twopass-range/t4b-table-absolute-in-table.html",
			"0500-twopass-range/t4b-table-absolute-table.html",
			"0500-twopass-range/t4b-table-float-across-pages.html",
			"0500-twopass-range/t4b-table-float-in-fixed-cell.html",
			"0500-twopass-range/t4b-table-float-in-table.html",
			"0500-twopass-range/t4b-table-inline-table.html",
			"0500-twopass-range/huge-grid.html", // T2: 巨大Gridの保持・改頁
			"0500-twopass-range/t4b-ledger-owners.html",
			"0500-twopass-range/anon-whitespace.html", // T3b: 匿名項目の合成イベント
			"0500-twopass-range/anon-text-absolute.html", // T3b: 匿名項目の合成イベント
			"0500-twopass-range/anon-float-text.html", // T3b: 匿名項目の合成イベント
			"0500-twopass-range/anon-generated.html", // T3b: 匿名項目の合成イベント
			"0500-twopass-range/anon-before.html", // T3b: 匿名項目の合成イベント
			"0500-twopass-range/anon-nested-in-range.html", // T3b: 匿名項目の合成イベント
			// minmax()/max()/min()の仕様外の近似対応(2026-08-06)。
			// GridTemplateTracks.javaのクラスjavadoc参照——最大値だけ採用し
			// 最小値は捨てる。yahoo.co.jpの実物CSSで発覚した未対応を埋める
			"0500-grid/minmax-max-approx.html", //
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
			"0500-grid/atomic-move-rowspan.html", //
			"0500-grid/row-split-carry.html", //
			"0500-grid/row-split-force.html", //
			"0500-grid/min-height-slack.html", //
			"0500-grid/row-split-pinned-order.html", //
			// 絶対配置のdisplayブロック化(2026-08-02)。position:absoluteの
			// display(flex/grid/table-row/table-cell/inline-block/list-item)
			// が例外にならず、静的位置がブロックとして決まることを固定する
			// ——実在のページ(yahoo.co.jp)のクラッシュ回帰
			"0500-grid/absolute-flex-grid.html", //
			// 和文詰めA2(2026-07-31)。text-autospaceのgap(0.125em)を
			// run境界のx座標で固定する(off/on/numeric限定/明示空白抑止)
			"0510-text-spacing/autospace-horizontal.html", //
			"0510-text-spacing/jlreq-justify-priority.html", // JLREQ 3.8.4の4段階追出し
			"0510-text-spacing/jlreq-shrink-priority.html", // JLREQ 3.8.3の6段階追込み
			"0510-text-spacing/autospace-vertical.html", //
			"0510-text-spacing/autospace-in-float.html", //
			// M2c実測ラッパーへのtext-autospace持ち越し(2026-08-08)。
			// inline-blockのshrink-to-fit実測がlatin→CJK境界のgapを
			// 含む幅で1行に収まることを固定する(kabutan回帰)
			"0510-text-spacing/autospace-inline-block-measured.html", //
			// 和文詰めT1b(2026-07-31/2026-08-23)。normal/trim-start/
			// space-allの行頭と連続約物の対比を固定する
			"0510-text-spacing/trim-pairs.html", //
			// CSS Text 4の行端ポリシー。trim-both/autoの無条件行末詰めと、
			// space-firstの初行・強制改行直後・自動折返しの差を固定する
			"0510-text-spacing/trim-line-edges.html", //
			// 和文詰めT2/H1(2026-07-31)。行末ぶら下げ(allow-end)の
			// 追い込みと、行末trimの条件付き半角化を固定する
			"0510-text-spacing/hanging-end.html", //
			// JLREQ E/F: style run境界を跨ぐ約物詰め、中点後ろの伸長抑制、
			// 横書き・縦書きのブロック先頭行の天付きを座標で固定する
			"0510-text-spacing/jlreq-boundaries.html", //
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

	record CorpusDocument(String path, int passCount) { }

	/** 他の全件観測試験でもgoldenの対象文書とpass数を共有する。 */
	static List<CorpusDocument> corpusDocuments() {
		final List<CorpusDocument> documents = new ArrayList<>();
		for (final String doc : DOCUMENTS) documents.add(new CorpusDocument(doc, 1));
		for (final String doc : MULTI_PASS_DOCUMENTS) documents.add(new CorpusDocument(doc, 2));
		return List.copyOf(documents);
	}

	public void testDisplayLists() throws Exception {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
		List<String> failures = new ArrayList<>();
		for (final CorpusDocument doc : corpusDocuments()) {
			if (!selectedByFilter(doc.path())) {
				continue;
			}
			checkDocument(doc.path(), doc.passCount(), failures);
		}
		if (System.getProperty("foliojet.displayListFilter") == null) {
			reportTwoPassRangeBind(failures);
		}
		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/** カンマ区切りの部分一致で、表示リストfixtureだけを高速に再実行する。 */
	private static boolean selectedByFilter(final String doc) {
		final String filter = System.getProperty("foliojet.displayListFilter");
		if (filter == null || filter.isBlank()) {
			return true;
		}
		for (final String token : filter.split(",")) {
			if (!token.isBlank() && doc.contains(token.trim())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * TwoPass range化(E-6増分4b、production default-on)のコーパス実測
	 * レポートと配線検証です。golden一致だけでは範囲再生の未発火を見逃すため、range bind
	 * がこのコーパスで実際に発火していることを固定する。
	 * T1以降のseal・消費・吸収・破棄の収支はstderrへ出し、未終端を検査する。
	 * 二重終端の防止はRangeHandleの状態機械が担う。
	 */
	private static void reportTwoPassRangeBind(List<String> failures) {
		final long seals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_ELIGIBLE.get();
		final long rangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.RANGE_FIRST_BINDS.get();
		final long cellSeals = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_SEALS.get();
		final long cellRangeBinds = net.zamasoft.foliojet.layout.fragment.ContinuationStats.CELL_RANGE_BINDS.get();
		final StringBuilder s = new StringBuilder();
		s.append("[E-6 two-pass range bind / golden corpus]\n");
		s.append("  RANGE_FIRST_BINDS=").append(rangeBinds).append('\n');

		s.append("  TWO_PASS_SEALS_ELIGIBLE=").append(seals).append('\n');
		s.append("  CELL_RANGE_SEALS=").append(cellSeals).append('\n');
		s.append("  CELL_RANGE_BINDS=").append(cellRangeBinds).append('\n');

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
		// リースを手放す。T1のscratch破棄も含めseals == consumed + subsumed + abandoned。
		final long subsumed = net.zamasoft.foliojet.layout.fragment.ContinuationStats.TWO_PASS_SEALS_SUBSUMED.get();
		System.err.println("  TWO_PASS_SEALS_SUBSUMED=" + subsumed);
		final long consumed = ContinuationStats.TWO_PASS_RANGES_CONSUMED.get();
		final long abandoned = ContinuationStats.TWO_PASS_SEALS_ABANDONED.get();
		System.err.println("  TWO_PASS_RANGES_CONSUMED=" + consumed);
		System.err.println("  TWO_PASS_SEALS_ABANDONED=" + abandoned);
		// T1: 収支は必要条件の観測。二重終端はRangeHandle自身が拒否する。
		System.err.println("  RANGE_BALANCE_DELTA=" + (seals - consumed - subsumed - abandoned));
		if (seals != consumed + subsumed + abandoned) {
			failures.add("未終端のRangeHandle: seals=" + seals + " consumed=" + consumed
					+ " subsumed=" + subsumed + " abandoned=" + abandoned);
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
		final long cellAbandoned = ContinuationStats.CELL_RANGE_SEALS_ABANDONED.get();
		System.err.println("  CELL_RANGE_SEALS_ABANDONED=" + cellAbandoned);
		System.err.println("  CELL_RANGE_BALANCE_DELTA="
				+ (cellSeals - cellRangeBinds - cellSubsumed - cellAbandoned));
		if (cellSeals != cellRangeBinds + cellSubsumed + cellAbandoned) {
			failures.add("未終端のセルリース: seals=" + cellSeals + " binds=" + cellRangeBinds
					+ " subsumed=" + cellSubsumed + " abandoned=" + cellAbandoned);
		}

		// E-6増分5b-2: 表Pass C(行単位逐次bind)の配線検証。コーパスは全実セル
		// 適格のRetained表を含むため、Pass Cが一度も発火しないのは空虚な緑
		if (passCTables == 0) {
			failures.add("表Pass C(E-6増分5b-2)がgoldenコーパスで一度も発火していません(空虚な緑)");
		}
		// 行単位bindと全セル先行bindは表の計測可否で分かれる別の契約。
		// このコーパスで全セル先行bindが発火しない性質を引き続き固定する。
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
