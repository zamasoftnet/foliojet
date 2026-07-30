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

	public final long getSourceAnchor() {
		return this.sourceAnchor;
	}

	public final void setSourceAnchor(final long id) {
		assert this.sourceAnchor == -1 : "アンカーは付与後不変: " + this.sourceAnchor + " -> " + id;
		this.sourceAnchor = id;
	}

	public final boolean isSourceReplayable() {
		return this.sourceAnchor >= 0 && !this.fragmented;
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

	protected final AffineTransform transform(AffineTransform transform, double x, double y) {
		AffineTransform ct = this.getParams().transform;
		if (ct.isIdentity()) {
			return transform;
		}
		transform = new AffineTransform(transform);
		double ax = x;
		double ay = y;
		Offset offset = this.getParams().transformOrigin;
		switch (offset.getXType()) {
		case ABSOLUTE:
			ax += offset.getX();
			break;
		case RELATIVE:
			ax += this.getWidth() * offset.getX();
			break;
		case MIXED:
			ax += offset.getX() + this.getWidth() * offset.getXRatio();
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
			ay += this.getHeight() * offset.getY();
			break;
		case MIXED:
			ay += offset.getY() + this.getHeight() * offset.getYRatio();
			break;
		default:
			throw new IllegalStateException();
		}

		transform.translate(ax, ay);
		transform.concatenate(ct);
		transform.translate(-ax, -ay);
		return transform;
	}

	public String toString() {
		return super.toString() + "[width=" + this.getWidth() + ",height=" + this.getHeight() + ",params="
				+ this.getParams() + ",pos=" + this.getPos() + "]";
	}
}
