package net.zamasoft.foliojet.layout.fragment;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 頁・段の切断の間だけ、破断の時点で開いている箱(ビルダーの flowStack の箱)を持ちます(2026-10-07)。
 *
 * <p>
 * 開いた箱は救済分割しない(2026-09-16、{@code FlowContainer} の {@code RelaxInside})。救済は箱を視覚的に
 * 切って閉じた残余に置き換えるので、まだ内容が届く開いた箱にすると、再開で flowStack が積み直されず継続の
 * 開き段数と食い違う。それまでの判定は改ページ計画({@link BreakPlan})が承認した鎖の箱だけを守り、能力
 * スキャンが障壁で止まった先の開いた箱、段が内側の段組を収集しない経路、{@code vertical-rl} と
 * {@code sideways-rl} の境のような「開いているが計画に選ばれない」箱は守れなかった(計画の無い降下では
 * 計画が null で渡る)。開き状態は計画とは別に、切断の入口でここへ写す。
 * </p>
 *
 * <p>
 * 救済分割の判定と同じく、並行する変換で混線しないよう{@link ThreadLocal}で持ちます。
 * </p>
 */
public final class OpenBoxes {
	private static final ThreadLocal<Set<IBox>> OPEN = new ThreadLocal<>();

	/** 計画に選ばれていない開いた箱の救済を止めた回数(掃過・試験の観測用)。 */
	public static final AtomicLong UNSELECTED_RESCUES_PREVENTED = new AtomicLong();

	/** 切断の範囲です。閉じると前の状態に戻る。 */
	public interface Scope extends AutoCloseable {
		@Override
		void close();
	}

	private OpenBoxes() {
		// unused
	}

	/**
	 * 切断の間、{@code boxes}を開いた箱とします。入れ子の切断(切断の中の段の切断)は外側の箱も引き継ぐ。
	 */
	public static Scope scope(final Collection<? extends IBox> boxes) {
		final Set<IBox> previous = OPEN.get();
		final Set<IBox> open = Collections.newSetFromMap(new IdentityHashMap<>());
		if (previous != null) {
			open.addAll(previous);
		}
		open.addAll(boxes);
		OPEN.set(open);
		return () -> {
			if (previous == null) {
				OPEN.remove();
			} else {
				OPEN.set(previous);
			}
		};
	}

	/** 破断の時点で開いている箱か。切断の外では常に false。 */
	public static boolean isOpen(final IBox box) {
		final Set<IBox> open = OPEN.get();
		return open != null && open.contains(box);
	}
}
