package net.zamasoft.foliojet.layout.fragment;

/**
 * 継続末尾の開き形です(M3b Phase 3c: 旧 int depth 規約の型化)。
 *
 * <p>
 * moved-open ボックス連鎖(write-once 断片の運搬 — ARCHITECTURE §5.5
 * 是認)を OpenChain の入れ子で、開きテキスト(スライス handoff)を
 * OpenText で表す。旧規約の「深さ n」は Chain^(n-1)(Text) に対応し、
 * 負の深さのような不正状態は表現不能。トレース・水位歩行の互換の
 * ため depth() で旧値を導出できる。
 * </p>
 */
public sealed interface OpenShape {
	/** すべて閉じている(旧 depth=0)。 */
	record Closed() implements OpenShape {
	}

	/** 末尾は開きテキスト(旧 depth=1)。 */
	record OpenText() implements OpenShape {
	}

	/** 末尾は moved-open ボックス。inner はその内側の形(旧 depth-1)。 */
	record OpenChain(OpenShape inner) implements OpenShape {
	}

	OpenShape CLOSED = new Closed();
	OpenShape TEXT = new OpenText();

	/** 旧 depth 規約からの変換です(0=Closed, 1=Text, n=Chain^(n-1)(Text))。 */
	static OpenShape of(final int depth) {
		OpenShape shape = depth <= 0 ? CLOSED : TEXT;
		for (int i = 1; i < depth; ++i) {
			shape = new OpenChain(shape);
		}
		return shape;
	}

	/** 旧 depth 規約の値です(トレース表示・水位歩行の互換)。 */
	default int depth() {
		return switch (this) {
		case Closed closed -> 0;
		case OpenText text -> 1;
		case OpenChain(final OpenShape inner) -> 1 + inner.depth();
		};
	}
}
