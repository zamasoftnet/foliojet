package net.zamasoft.foliojet.layout.box;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.params.Offset;

public abstract class AbstractBox implements IBox {
	/**
	 * この内容を生んだ LayoutSource のイベントIDです(SourceAnchor。
	 * P0: provenance の style params からの分離 — 外部レビュー指摘)。
	 * 記録時に一度だけ付与され、以後不変。断片・未記録の内容は -1 のまま
	 * — レシピ構築の断片は最初からアンカーを持たないため、旧
	 * params.sourceEventId の「-1 書き込みによる無効化」プロトコルは
	 * 不要になった。再生インスタンスにはドライバがイベントIDから
	 * 再付与する(SourceReplayer.drive)。
	 */
	private long sourceAnchor = -1;

	/**
	 * この箱が既に切断され、内容の一部を継続断片へ渡したかどうかです
	 * (2026-07-28新設)。{@link #getSourceAnchor()}は「この箱を生んだ
	 * 要素の開始イベント」であり、<b>切断後も前断片側に残り続ける</b>
	 * ——継続断片はレシピ構築でアンカーを持たないので、切断で無効化
	 * されるのは<b>後ろ半分だけ</b>だった。前断片をソースから再生すると
	 * <b>要素全体</b>(=継続断片が持っている残りを含む)が組み直され、
	 * 継続断片の再開と二重になる。
	 */
	private boolean fragmented = false;

	/**
	 * ソース区間からの再構築を禁じるか(2026-08-23)。断片化(fragmented)
	 * とは独立——断片化は表フッタ反復などの意味も持つため流用しない。
	 * 表全体のMOVEで立てる: 表の字句的ソース区間には、HTMLのfoster
	 * parentingで既に表の**外**へ確定した内容(表直下の裸テキスト)が
	 * 含まれうるため、MOVE後にその区間を再生すると確定済み内容が複製される
	 * (v2生成器 seed 30の縮小で発見)。
	 */
	private boolean sourceReplayInvalidated = false;

	public final long getSourceAnchor() {
		return this.sourceAnchor;
	}

	public final void setSourceAnchor(final long id) {
		assert this.sourceAnchor == -1 : "アンカーは付与後不変: " + this.sourceAnchor + " -> " + id;
		this.sourceAnchor = id;
	}

	public final boolean isSourceReplayable() {
		return this.sourceAnchor >= 0 && !this.fragmented && !this.sourceReplayInvalidated;
	}

	/** 構築済みの内容を保持したまま運ぶため、ソース再生だけを無効化します。 */
	public final void invalidateSourceReplay() {
		this.sourceReplayInvalidated = true;
	}

	public final void markFragmented() {
		this.fragmented = true;
	}

	/**
	 * この箱が切断済み(前断片側)かを返します(タグ付きPDF欠陥②の修正、
	 * 2026-07-30——表の反復フッタ判定「切断された断片のフッタは反復表示」
	 * が使う)。
	 */
	public final boolean isFragmented() {
		return this.fragmented;
	}

	/**
	 * エンジン内部の水平圧縮率です(縦中横の1em収めなど。既定1=なし)。
	 * 作者のCSS {@code transform}とは別物で、こちらが内側に掛かる。
	 */
	protected double internalScaleX() {
		return 1;
	}

	/** 内部圧縮後に内容を中央へ寄せる物理Xのずれです(既定0)。 */
	/**
	 * この箱を内容として保持している{@code FlowContainer}(2026-08-29)。
	 * {@code hasNonDecorationContent}のメモを、変更のあった箱の祖先だけ
	 * 無効化するために使う。保持先が変わるたびに付け直される。
	 */
	private net.zamasoft.foliojet.layout.box.content.FlowContainer contentParent;

	public final net.zamasoft.foliojet.layout.box.content.FlowContainer getContentParent() {
		return this.contentParent;
	}

	public final void setContentParent(final net.zamasoft.foliojet.layout.box.content.FlowContainer parent) {
		this.contentParent = parent;
	}

	protected double internalOffsetX() {
		return 0;
	}

	/**
	 * {@code transform-origin}・割合の{@code translate()}の基準箱(border box)を
	 * 得るための margin です。margin を持たない箱は null(=(x, y) と
	 * getWidth()/getHeight() がそのまま基準箱)。
	 */
	protected net.zamasoft.foliojet.layout.part.AbsoluteInsets transformReferenceMargin() {
		return null;
	}

	protected final AffineTransform transform(AffineTransform transform, double x, double y) {
		AffineTransform ct = this.getParams().transform;
		final double txRatio = this.getParams().transformTxRatio;
		final double tyRatio = this.getParams().transformTyRatio;
		final double txRatioH = this.getParams().transformTxRatioH;
		final double tyRatioW = this.getParams().transformTyRatioW;
		final double isx = this.internalScaleX();
		final double iox = this.internalOffsetX();
		final double zoom = this.getParams().zoom;
		if (ct.isIdentity() && txRatio == 0 && tyRatio == 0 && txRatioH == 0 && tyRatioW == 0 && isx == 1
				&& iox == 0 && zoom == 1) {
			return transform;
		}
		transform = new AffineTransform(transform);
		// transform-origin と translate() の割合の基準箱は border box
		// (css-transforms-1 §3: reference box は border-box)。(x, y) は margin box
		// の原点、getWidth()/getHeight() は margin 込みなので、margin を除いて
		// 基準箱を取る(2026-09-03。margin 付きの箱で原点が margin box 基準に
		// ずれていた——filter 層の配置試験で発見)
		final net.zamasoft.foliojet.layout.part.AbsoluteInsets margin = this.transformReferenceMargin();
		final double bx = margin == null ? x : x + margin.left;
		final double by = margin == null ? y : y + margin.top;
		final double bw = margin == null ? this.getWidth() : this.getWidth() - margin.getFrameWidth();
		final double bh = margin == null ? this.getHeight() : this.getHeight() - margin.getFrameHeight();
		if (zoom != 1) {
			// zoom(2026-08-29)は境界箱の左上を原点に、作者のtransformの外側で
			// 拡大する(Zoomのjavadoc: レイアウトには効かない近似)
			transform.translate(x, y);
			transform.scale(zoom, zoom);
			transform.translate(-x, -y);
		}
		double ax = bx;
		double ay = by;
		Offset offset = this.getParams().transformOrigin;
		switch (offset.getXType()) {
		case ABSOLUTE:
			ax += offset.getX();
			break;
		case RELATIVE:
			ax += bw * offset.getX();
			break;
		case MIXED:
			ax += offset.getX() + bw * offset.getXRatio();
			break;
		default:
			throw new IllegalStateException();
		}
		// 注: 以下のswitchはoffset.getXType()を条件にしているが本体はY成分を
		// 計算している(既存コードの不整合。MIXED追加のスコープ外のため
		// 挙動は変えず、既存条件式のまま維持する)。
		switch (offset.getXType()) {
		case ABSOLUTE:
			ay += offset.getY();
			break;
		case RELATIVE:
			ay += bh * offset.getY();
			break;
		case MIXED:
			ay += offset.getY() + bh * offset.getYRatio();
			break;
		default:
			throw new IllegalStateException();
		}

		transform.translate(ax, ay);
		if (txRatio != 0 || tyRatio != 0 || txRatioH != 0 || tyRatioW != 0) {
			// **割合の平行移動はここで解く**(2026-08-03)。基準はこの箱自身の
			// 寸法。関数列の中の位置は解析時に線形分解して係数へ畳んであり
			// (TransformValue、2026-08-29)、その結果は合成行列の**外側**
			// (concatenateの前)に足す1回の平行移動になる
			final double w = bw;
			final double h = bh;
			transform.translate(w * txRatio + h * txRatioH, w * tyRatioW + h * tyRatio);
		}
		transform.concatenate(ct);
		transform.translate(-ax, -ay);
		if (isx != 1 || iox != 0) {
			// 内部圧縮は箱の左端(x)を基準に掛ける。内容は自然幅で組まれて
			// いるので、これで [x, x+セル幅] へちょうど収まる
			transform.translate(x + iox, 0);
			transform.scale(isx, 1);
			transform.translate(-x, 0);
		}
		return transform;
	}

	public String toString() {
		return super.toString() + "[width=" + this.getWidth() + ",height=" + this.getHeight() + ",params="
				+ this.getParams() + ",pos=" + this.getPos() + "]";
	}
}
