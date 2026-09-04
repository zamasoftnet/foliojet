package net.zamasoft.foliojet.layout.util;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.text.TextLayoutHandler;
import net.zamasoft.pdfg2d.gc.text.breaking.TextBreakingRulesBundle;
import net.zamasoft.pdfg2d.gc.text.layout.PageLayoutGlyphHandler;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: LayoutUtils.java 1574 2018-10-26 02:44:00Z miyabe $
 */
public final class LayoutUtils {
	private LayoutUtils() {
		// unused
	}

	// magic number
	public static final double NONE = Double.MAX_VALUE * 0.958324758437;

	public static final boolean isNone(double v) {
		return v == NONE;
	}

	/**
	 * 「この箱が紙のどこまで描くかは測れない」を表す番兵です(2026-07-27新設)。
	 *
	 * <p>
	 * {@link net.zamasoft.foliojet.layout.box.IBox#paintedPageExtent}が返す
	 * 値で、<b>まだ中身が確定していない断片</b>(ソースからの再生を待つ
	 * テキストブロックの尾部など)に使います。判定側では「どこまでも描く」
	 * ように振る舞うので、白紙ページの抑止判定は必ず<b>安全側</b>
	 * (=改ページする・断片を捨てない)へ倒れます。
	 * </p>
	 *
	 * <p>
	 * <b>この値をレイアウトの寸法として使ってはいけません。</b>
	 * 用途は「描くものがあるか」の比較だけです。
	 * </p>
	 */
	public static final double PAINTS_UNKNOWN = Double.POSITIVE_INFINITY;

	/**
	 * 表示リストに載ってよい座標・寸法の絶対値の上限(ポイント)。
	 *
	 * <p>
	 * 1e8pt は約3,500km。PDFのページ寸法の上限14,400pt(200インチ)の
	 * 7,000倍近くあり、正当なレイアウトが届く値ではありません。逆に、
	 * {@link #NONE}(10<sup>308</sup>級)やそれに算術を施した値、
	 * {@code Double.MAX_VALUE}を「制約なし」として持ち回った値が
	 * 位置や確定寸法へ漏れた場合は必ずこれを超えます。
	 * </p>
	 */
	public static final double DRAWABLE_LIMIT = 1e8;

	/**
	 * 描画へ渡してよい値か(有限で、印刷物としてあり得る範囲か)を返します。
	 *
	 * <p>
	 * <b>{@link #isNone(double)}では足りない理由。</b>
	 * {@code isNone}は<b>番兵値そのもの</b>としか一致しないので、
	 * <b>倍率が変わる演算を通った番兵</b>({@code NONE / 2}、
	 * {@code -NONE}、座標変換のスケール)を検出できません——値は
	 * 10<sup>307</sup>級のゴミ座標のままなのに素通りします。
	 * {@code NaN}も同じ穴を通ります({@code NaN != NONE}なので
	 * {@code isNone(NaN)}は偽)。どちらも例外にはならず、
	 * <b>内容が紙面のどこにも現れないまま静かに欠落する</b>形で出ます。
	 * 帳票用途ではこれが最悪の壊れ方なので、範囲で弾きます。
	 * </p>
	 *
	 * <p>
	 * ({@code NONE + 10}のような加算は逃げ道になりません。この規模の
	 * doubleの刻み幅は10<sup>292</sup>程度あり、値が1ビットも変わらない
	 * ためです。)
	 * </p>
	 *
	 * <p>
	 * {@code NaN}は全ての比較が偽になるため、この2つの不等式で自動的に
	 * 弾かれます(明示的な{@code isNaN}は不要)。
	 * </p>
	 *
	 * <p>
	 * <b>守れない穴。</b>{@code NONE - NONE}は0になります。番兵同士の差は
	 * もっともらしい座標に化けるので、範囲では検出できません
	 * (テスト{@code DrawableRangeGuardTest}に記録済み)。
	 * </p>
	 *
	 * <p>
	 * 用途は<b>assertによるfail closed</b>です。本番ではassertが無効なので
	 * コストはゼロ。制約値({@code max-width}の「上限なし」を表す
	 * {@code Double.MAX_VALUE}など)にこれを掛けてはいけません——制約は
	 * 正当に巨大です。掛けてよいのは<b>位置と確定した寸法</b>だけです。
	 * </p>
	 */
	public static final boolean isDrawable(double v) {
		return v > -DRAWABLE_LIMIT && v < DRAWABLE_LIMIT;
	}

	public static final double THRESHOLD = .5;

	/**
	 * a &lt; bなら負、a &gt; bなら正、a = bならゼロを返します。<br>
	 * 計算誤差による判定間違いを防ぐため、 行の折り返し、浮動ボックス、行の位置指定、改ページ制御のための比較はこれを使用します。
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	public static int compare(double a, double b) {
		// 0.5未満の差は同一と見なします
		// IEでは切り落とし、Firefoxは小数点以下1桁でまるめている模様
		// 注：まるめてから比較する実装では、差が同じでも判定が変わるため、
		// 内容がページをはみ出していると判定された場合でも
		// ボックスの分割の途中で判定が矛盾することがあった
		double diff = a - b;
		if (diff < THRESHOLD && diff > -THRESHOLD) {
			return 0;
		}
		return a < b ? -1 : 1;
	}

	/**
	 * ボックスの行方向寸法が内容に依存する(実測パスが必要)であればtrueを返します。
	 * 2パス化の判定はこの述語ファミリに一元化されます(ARCHITECTURE.md §5.2b)。
	 * 絶対配置は ABSOLUTE 指定のみを固定とみなします(%やインセット由来は内容依存)。
	 *
	 * <p>
	 * 軸は<b>ボックス自身の書字方向</b>で判定する(2026-08-10)。shrinkToFitの
	 * 内部軸系(this.params.flow)と同じ軸でなければならない——親のflowで
	 * 判定すると直交ブロックで軸がずれ、「自身の線軸はautoなのにページ軸の
	 * 指定を見て実測不要」と誤判定し、線軸fit-contentが空実測の0になって
	 * 内容ごと消えていた(縦書き文書内のheight付き横ブロック)。
	 * 親と同軸のボックスでは従来と同値。
	 *
	 * @param containerBox 包含ブロック(現在は未使用。呼び出し側の文脈を残す)
	 * @param blockBox     対象ボックス
	 * @return 実測が必要であればtrue
	 */
	public static boolean needsIntrinsicSizing(AbstractContainerBox containerBox, AbstractContainerBox blockBox) {
		final BlockParams params = blockBox.getBlockParams();
		if (params.hasIntrinsicLine()) {
			// width/min-width/max-widthのいずれかが固有寸法キーワード
			// (2026-08-29): width:10pt; min-width:max-content のように幅が
			// 確定していても、min/maxの実測が要る
			return true;
		}
		final LengthType lineType = params.size.getLineType(params.flow);
		if (blockBox.getPos().getType() == PosType.ABSOLUTE) {
			return lineType != LengthType.ABSOLUTE;
		}
		return lineType == LengthType.AUTO;
	}

	/**
	 * テキストを描画します。
	 * 
	 * @param gc
	 * @param fontSize
	 * @param text
	 * @param x
	 * @param y
	 * @param width
	 */
	public static void drawText(GC gc, FontPolicyList fontPolicy, double fontSize, String text, double x, double y,
			double width) throws GraphicsException {
		assert isDrawable(x) : "描画位置xが異常: " + x;
		assert isDrawable(y) : "描画位置yが異常: " + y;
		assert isDrawable(width) : "描画幅が異常: " + width;
		try (final var gcState = gc.begin()) {
			gc.transform(AffineTransform.getTranslateInstance(x, y));

			PageLayoutGlyphHandler lineHandler = new PageLayoutGlyphHandler(gc);
		lineHandler.setLineAdvance(width);

		TextLayoutHandler tlf = new TextLayoutHandler(gc, TextBreakingRulesBundle.getRules(null), lineHandler);
		tlf.setFontFamilies(FontFamilyList.SERIF);
			tlf.setFontPolicy(fontPolicy);
			tlf.setFontSize(fontSize);
			tlf.characters(text);
			tlf.flush();

			lineHandler.close();
		}
	}

	/**
	 * 長さを計算します。
	 * 
	 * @param length
	 * @param ref
	 * @return
	 */
	public static double computeLength(Length length, double ref) {
		switch (length.getType()) {
		case RELATIVE:
			return length.getLength() * ref;
		case ABSOLUTE:
			return length.getLength();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return length.getLength() + length.getRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * AUTOをゼロとしてインセットを計算します。
	 * 
	 * @param ainsets
	 * @param insets
	 * @param refSize
	 */
	public static void computeMarginsAutoToZero(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		case AUTO:
			top = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		case AUTO:
			right = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		case AUTO:
			bottom = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		case AUTO:
			left = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	public static void computePaddings(AbsoluteInsets ainsets, Insets insets, double refSize) {
		double top, right, bottom, left;
		switch (insets.getTopType()) {
		case ABSOLUTE:
			top = insets.getTop();
			break;
		case RELATIVE:
			top = insets.getTop() * refSize;
			break;
		case MIXED:
			top = insets.getTop() + insets.getTopRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getRightType()) {
		case ABSOLUTE:
			right = insets.getRight();
			break;
		case RELATIVE:
			right = insets.getRight() * refSize;
			break;
		case MIXED:
			right = insets.getRight() + insets.getRightRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getBottomType()) {
		case ABSOLUTE:
			bottom = insets.getBottom();
			break;
		case RELATIVE:
			bottom = insets.getBottom() * refSize;
			break;
		case MIXED:
			bottom = insets.getBottom() + insets.getBottomRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (insets.getLeftType()) {
		case ABSOLUTE:
			left = insets.getLeft();
			break;
		case RELATIVE:
			left = insets.getLeft() * refSize;
			break;
		case MIXED:
			left = insets.getLeft() + insets.getLeftRatio() * refSize;
			break;
		default:
			throw new IllegalStateException();
		}
		ainsets.top = top;
		ainsets.right = right;
		ainsets.bottom = bottom;
		ainsets.left = left;
	}

	/**
	 * Dimensionの幅を計算します。 AUTOの場合はNaNを返します。
	 * 
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionWidth(Dimension size, double ref) {
		switch (size.getWidthType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() * ref;
		case ABSOLUTE:
			return size.getWidth();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getWidth() + size.getWidthRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Dimensionの高さを計算します。 AUTOの場合はNaNを返します。
	 * 
	 * @param size
	 * @param ref
	 * @return
	 */
	public static double computeDimensionHeight(Dimension size, double ref) {
		switch (size.getHeightType()) {
		case RELATIVE:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() * ref;
		case ABSOLUTE:
			return size.getHeight();
		case MIXED:
			if (ref == LayoutUtils.NONE) {
				return LayoutUtils.NONE;
			}
			return size.getHeight() + size.getHeightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Dimensionの行方向の寸法を計算します。 AUTOの場合はNONEを返します。
	 *
	 * @param size 寸法
	 * @param flow 軸を決める書字方向
	 * @param ref  相対値の基準
	 * @return 行方向の寸法
	 */
	public static double computeDimensionLine(Dimension size, WritingMode flow, double ref) {
		return flow.isVertical() ? computeDimensionHeight(size, ref) : computeDimensionWidth(size, ref);
	}

	/**
	 * 親の物理原点 x から、論理位置(行方向 childLineStart、ページ方向
	 * childPageStart/childPageEnd)に置かれる子の物理X座標を返します。
	 *
	 * <h2>ページ軸の向きはここと {@link #drawY} だけが知っている</h2>
	 *
	 * <p>
	 * 書字方向は<b>2つの独立した属性</b>で表せます——<b>どの物理次元が
	 * ページ軸か</b>と、<b>その軸が正負どちらへ進むか</b>。前者は
	 * {@link WritingMode#isVertical()}が答え、コード全体に散っていますが、
	 * <b>後者を物理座標へ変換するのは、main全体でこの関数と{@link #drawY}
	 * だけです</b>(2026-07-25に実測確認)。
	 * </p>
	 *
	 * <table border="1">
	 * <caption>ページ軸の次元と向き</caption>
	 * <tr><th>書字方向</th><th>ページ軸</th><th>向き</th><th>X座標に足すもの</th></tr>
	 * <tr><td>TB(横書き)</td><td>Y</td><td>正(上→下)</td><td>行方向のみ</td></tr>
	 * <tr><td>RL(縦書き・右→左)</td><td>X</td><td><b>負</b></td><td>{@code parentPageExtent - childPageEnd}</td></tr>
	 * <tr><td>LR(縦書き・左→右)</td><td>X</td><td>正</td><td>{@code childPageStart}</td></tr>
	 * </table>
	 *
	 * <p>
	 * LRがTBと同じ「始端を足すだけ」の形になるのは偶然ではなく、
	 * <b>向きが正である</b>という同じ性質の現れです。
	 * </p>
	 *
	 * <p>
	 * <b>この関数を迂回して自前で符号計算をしないこと。</b>
	 * {@code x += 親の内寸; x - 子のpageAxis - 子の寸法}という手書きは
	 * RL専用式であり、LRで誤ります(2026-07-25にLRを実装した際、
	 * FlowContainer・Floatings・表・救済分割に計10箇所の手書きが見つかり、
	 * すべてこの関数へ寄せた)。
	 * </p>
	 *
	 * @param flow             書字方向
	 * @param x                親の物理X原点
	 * @param parentPageExtent 親のページ方向寸法
	 * @param childPageStart   子のページ方向始端
	 * @param childPageEnd     子のページ方向終端(始端+子の寸法)
	 * @param childLineStart   子の行方向始端
	 * @return 子の物理X座標
	 */
	public static double drawX(WritingMode flow, double x, double parentPageExtent, double childPageStart,
			double childPageEnd, double childLineStart) {
		return switch (flow) {
		case TB -> x + childLineStart;
		case RL -> x + parentPageExtent - childPageEnd;
		case LR -> x + childPageStart;
		};
	}

	/**
	 * ページ軸の<b>向き</b>(+1 または -1)を返します。
	 *
	 * <p>
	 * {@link #drawX}が扱うのは「論理位置→物理座標」の変換ですが、
	 * <b>すでに物理座標にあるものをページ方向へずらす</b>操作
	 * (セルの{@code vertical-align}など)にも同じ向きが要ります。
	 * TBとLRは正、<b>RLだけが負</b>です(2026-07-25、vertical-lr対応で新設。
	 * それまでは{@code isVertical()}で分岐してRL専用に{@code -=}していた)。
	 * </p>
	 *
	 * @param flow 書字方向
	 * @return ページ軸が正方向なら+1、負方向(RL)なら-1
	 */
	public static double pageAxisSign(WritingMode flow) {
		return flow == WritingMode.RL ? -1 : 1;
	}

	/**
	 * 親の物理原点 y から、論理位置に置かれる子の物理Y座標を返します。
	 * 向きの扱いは{@link #drawX}の説明を参照(縦書きではページ軸がXなので、
	 * Yは常に行方向だけで決まり、RLとLRで違いはありません)。
	 *
	 * @param flow           書字方向
	 * @param y              親の物理Y原点
	 * @param childPageStart 子のページ方向始端
	 * @param childLineStart 子の行方向始端
	 * @return 子の物理Y座標
	 */
	public static double drawY(WritingMode flow, double y, double childPageStart, double childLineStart) {
		return flow.isVertical() ? y + childLineStart : y + childPageStart;
	}

	/**
	 * 論理行内区間 {@code [start, end]} の物理始端を返します。
	 *
	 * <p>
	 * sideways の行内進行が下から上なら、論理位置はそのまま保ち、物理化するときだけ
	 * {@code lineExtent - end} へ反転します。通常の縦組版は従来座標を保ちます。点を写す場合は
	 * {@code start == end} として呼び出します。この写像は逆写像も同じです。
	 * </p>
	 *
	 * @param params     行の組版方向
	 * @param lineExtent 行内軸の物理寸法
	 * @param start      論理区間の始端
	 * @param end        論理区間の終端
	 * @return 物理上端からの位置
	 */
	public static double inlineToPhysical(final AbstractTextParams params, final double lineExtent,
			final double start, final double end) {
		if (params.flow.isVertical()
				&& net.zamasoft.foliojet.layout.box.params.TypesettingMode.isHorizontal(params.flow,
						params.writingModeVariant)
				&& net.zamasoft.foliojet.layout.box.params.TypesettingMode.inlineProgression(params.flow,
						params.writingModeVariant, params.direction)
						== net.zamasoft.foliojet.layout.box.params.TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
			return lineExtent - end;
		}
		return start;
	}

	public static double computeInsetsTop(Insets insets, double ref) {
		switch (insets.getTopType()) {
		case ABSOLUTE:
			return insets.getTop();
		case RELATIVE:
			return insets.getTop() * ref;
		case MIXED:
			return insets.getTop() + insets.getTopRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsLeft(Insets insets, double ref) {
		switch (insets.getLeftType()) {
		case ABSOLUTE:
			return insets.getLeft();
		case RELATIVE:
			return insets.getLeft() * ref;
		case MIXED:
			return insets.getLeft() + insets.getLeftRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsRight(Insets insets, double ref) {
		switch (insets.getRightType()) {
		case ABSOLUTE:
			return insets.getRight();
		case RELATIVE:
			return insets.getRight() * ref;
		case MIXED:
			return insets.getRight() + insets.getRightRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeInsetsBottom(Insets insets, double ref) {
		switch (insets.getBottomType()) {
		case ABSOLUTE:
			return insets.getBottom();
		case RELATIVE:
			return insets.getBottom() * ref;
		case MIXED:
			return insets.getBottom() + insets.getBottomRatio() * ref;
		case AUTO:
			return LayoutUtils.NONE;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetX(Offset offset, IBox containerBox) {
		switch (offset.getXType()) {
		case ABSOLUTE:
			return offset.getX();
		case RELATIVE:
			// this.offsetX = pos.offset.getX() * container.getInnerWidth();
			// break;
		case MIXED:
			// RELATIVE同様、この経路は未実装(既存のTODO。MIXEDもさしあたり
			// RELATIVEと同じ扱いにして例外だけは避ける)。
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static double computeOffsetY(Offset offset, IBox containerBox) {
		switch (offset.getYType()) {
		case ABSOLUTE:
			return offset.getY();
		case RELATIVE:
			// this.offsetY = pos.offset.getY() * container.getInnerWidth();
			// break;
		case MIXED:
			// RELATIVE同様、この経路は未実装(既存のTODO。MIXEDもさしあたり
			// RELATIVEと同じ扱いにして例外だけは避ける)。
		case AUTO:
			return 0;
		default:
			throw new IllegalStateException();
		}
	}

	public static void calculateReplacedSize(Builder builder, AbstractReplacedBox replacedBox) {
		//
		// ■ 幅と高さの計算
		//
		double refWidth, refHeight, refMaxWidth, refMaxHeight;
		final AbstractContainerBox containerBox = builder.getFlowBox();
		final BlockParams params = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		// position:relativeの内側フローは絶対配置用のcontext boxにもなるが、
		// 通常フローの置換要素の包含ブロックであることは変わらない。
		// builder境界のroot自身がcontextの場合だけcontext側を検索し、入れ子の
		// contextが持つ確定寸法をflow検索から落とさない。
		final boolean rootContext = containerBox == builder.getRootBox()
				&& containerBox == builder.getContextBox();
		replacedBox.calculateFrame(lineSize);
		if (params.flow.isVertical()) {
			// 縦書き
			AbstractContainerBox box;
			if (rootContext) {
				box = builder.getFixedWidthContextBox();
			} else {
				box = builder.getFixedWidthFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// セル内でページ送りされた場合
					return;
				}
				refMaxWidth = refWidth = LayoutUtils.NONE;
				refMaxHeight = refHeight = LayoutUtils.NONE;
			} else {
				refWidth = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : box.getInnerWidth();
				refMaxWidth = box.getInnerWidth();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxHeight =refHeight = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (rootContext) {
						box = builder.getFixedHeightContextBox();
					} else {
						box = builder.getFixedHeightFlowBox();
					}
					if (box == null) {
						refMaxHeight =refHeight = LayoutUtils.NONE;
					} else {
						refMaxHeight =refHeight = box.getLineSize();
					}
				} else {
					refMaxHeight =refHeight = lineSize;
				}
			}
		} else {
			// 横書き
			AbstractContainerBox box;
			if (rootContext) {
				box = builder.getFixedHeightContextBox();
			} else {
				box = builder.getFixedHeightFlowBox();
			}
			if (box == null) {
				if (builder.getContextBox().getType() == BoxType.TABLE_CELL && builder instanceof BlockBuilder) {
					// セル内でページ送りされた場合
					return;
				}
				refMaxHeight = refHeight = LayoutUtils.NONE;
				refMaxWidth = refWidth = LayoutUtils.NONE;
			} else {
				refHeight = box.getType()== BoxType.PAGE ? LayoutUtils.NONE : box.getInnerHeight();
				refMaxHeight = box.getInnerHeight();
				// 通常のフローでないため行幅があてにならない時はフローを探す
				if (builder.isTwoPass()) {
					refMaxWidth = refWidth = LayoutUtils.NONE;
				} else if (containerBox.getPos().getType() != PosType.FLOW
						&& containerBox.getPos().getType() != PosType.FLOAT
						&& containerBox.getPos().getType() != PosType.TABLE_CELL) {
					if (rootContext) {
						box = builder.getFixedWidthContextBox();
					} else {
						box = builder.getFixedWidthFlowBox();
					}
					if (box == null) {
						refMaxWidth = refWidth = LayoutUtils.NONE;
					} else {
						refMaxWidth = refWidth = box.getLineSize();
					}
				} else {
					refMaxWidth = refWidth = lineSize;
				}
			}
		}
		// 中立wrapper(flex item)の行方向充填(2026-08-09)。wrapperが
		// authoredの%をflexコンテナ基準で解決済み(NeutralTransfer)のため、
		// 子が同じ%をwrapper内寸へ再適用すると二重になる(width:50%が
		// 25%相当へ縮む)。子の式がwrapper内寸ちょうどを返すよう%の基準を
		// 差し替える——100%は不動点で従来と同値。絶対長は二重にならないので
		// 触らない。子自身のmarginはアイコン用途で実質使われないため
		// 考慮しない(使われた場合はみ出す側=安全でない側に倒れない)
		if (containerBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox item
				&& item.isNeutralLineFill()) {
			final Dimension size = replacedBox.getReplacedParams().size;
			if (params.flow.isVertical()) {
				final double innerHeight = containerBox.getInnerHeight();
				if (size.getHeightType() == LengthType.RELATIVE && size.getHeight() != 0) {
					refHeight = refMaxHeight = innerHeight / size.getHeight();
				} else if (size.getHeightType() == LengthType.MIXED && size.getHeightRatio() != 0) {
					refHeight = refMaxHeight = (innerHeight - size.getHeight()) / size.getHeightRatio();
				}
			} else {
				final double innerWidth = containerBox.getInnerWidth();
				if (size.getWidthType() == LengthType.RELATIVE && size.getWidth() != 0) {
					refWidth = refMaxWidth = innerWidth / size.getWidth();
				} else if (size.getWidthType() == LengthType.MIXED && size.getWidthRatio() != 0) {
					refWidth = refMaxWidth = (innerWidth - size.getWidth()) / size.getWidthRatio();
				}
			}
		}
		replacedBox.calculateSize(refWidth, refHeight, refMaxWidth, refMaxHeight);
	}

	public static double getMaxAdvance(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		final double lineSize;
		if (params.flow.isVertical()) {
			// 縦書き
			lineSize = box.getInnerHeight();
		} else {
			// 横書き
			lineSize = box.getInnerWidth();
		}
		return lineSize;
	}

	/**
	 * {@code column-width}の<b>使用値</b>の下限(1px = 0.75pt)です。
	 *
	 * <p>
	 * css-multicol-1 §3.1は「{@code column-width:0}は指定値・計算値としては
	 * 正当だが、<b>使用値が1pxを下回ることはない</b>」と定めています。
	 * 実ブラウザも同じです。
	 * </p>
	 *
	 * <p>
	 * これは体裁の問題ではなく<b>停止性の問題</b>です。0を通すと
	 * {@link #getColumnCount}の除算が0除算になり、{@code (int)Infinity} =
	 * 2,147,483,647段を作ろうとして事実上停止しません
	 * (WPT {@code css-multicol/zero-column-width-layout.html})。
	 * </p>
	 */
	private static final double MIN_COLUMN_WIDTH = 0.75;

	public static int getColumnCount(final AbstractContainerBox box) {
		final BlockParams params = box.getBlockParams();
		if (LayoutUtils.isNone(params.columns.width)) {
			return params.columns.count;
		}
		final double lineSize = LayoutUtils.getMaxAdvance(box);
		// 使用値の下限を効かせてから割る({@link #MIN_COLUMN_WIDTH}参照)。
		// gapは負になりえないので、これで除数は必ず正になる
		final double width = Math.max(params.columns.width, MIN_COLUMN_WIDTH);
		if (width >= lineSize) {
			return 1;
		}
		final int count = (int) Math.floor((lineSize + params.columns.gap) / (width + params.columns.gap));
		// 上の分岐で width < lineSize なので count >= 1 のはずだが、
		// lineSize が NaN/巨大値のときに 0 や負に落ちないことを保証する
		// ——段数0は呼び出し側が「段組でない」と読むので、意味が変わる
		return Math.max(1, count);
	}


}
