package net.zamasoft.foliojet.layout.segment;

import java.util.Arrays;

import net.zamasoft.foliojet.layout.DocumentBuilder;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.INonReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * 共有Segment executorです(2026-07-24新設、E-6増分3b-1——
 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
 * §2.3増分1)。
 *
 * <p>
 * {@code SourceReplayer.drive}({@code LayoutSource.Event}直接駆動)と
 * {@code BalanceProbeSession}({@link SegmentEvent}駆動)に重複していた
 * replay switchの「駆動部分」——{@code DocumentBuilder}への駆動・
 * ordinal({@code EventId})→{@code SourceAnchor}再付与・テキストの
 * fresh copy化——をここへ一元化する。入力はstreamingカーソル
 * (1イベントずつの呼び出し)で、Listを要求しない。
 * </p>
 *
 * <p>
 * <b>段階的統合の設計</b>: 完全な単一switch化はまだしない。
 * {@code LayoutSource.Event}にはSegmentEventへ変換すると
 * {@link SegmentEvent.Barrier}になるもの(unsafeな置換要素・
 * {@code Opaque})が残っており、live再生経路はそれらを従来どおり
 * 直接駆動する必要がある——イベント単位で「変換できるものだけ共有
 * executor・Barrierは従来駆動」と分けると二重経路が固定化して
 * 本末転倒になる。そこで本増分では駆動プリミティブの共有に留め、
 * IRのswitchは{@link #execute(SegmentEvent)}(正規・恒久)と
 * {@link #executeLive(LayoutSource.Event)}(過渡)の2本を併置する。
 * Replacedのrecipe化は3b-3で完了(記録時freeze。freezeできないものは
 * {@code LayoutSource.ReplacedLive}として残り、back-reference隔離
 * ({@link #isolatedReplayInstance})つきで従来駆動)。残るBarrier源は
 * Start(3b-4のappend時freeze対象)・Opaque・ReplacedLiveで、ゼロに
 * なった後、liveの過渡switchを撤去(3b-6)して
 * {@link #execute(SegmentEvent)}だけが残る。
 * </p>
 *
 * <p>
 * <b>Charsのfresh copy化(3b-1で是正)</b>: 記録済み{@code char[]}を
 * そのまま{@code doc.characters}へ渡すと、
 * {@code StyledTextUnitizer.characters}が配列内容をその場で書き換える
 * ため、同一範囲の再replayで変換済みテキストへの再変換が起きうる
 * (記録時は防御コピー済みだがreplay時は生渡しだった)。replay駆動は
 * 毎回freshなコピーを渡す({@link SegmentEvent.Text}は
 * {@code String#toCharArray()}が毎回freshなので元より安全)。
 * </p>
 */
public final class SegmentExecutor {

	private final DocumentBuilder doc;

	/**
	 * 次に駆動するイベントのordinal(= EventId)。再生インスタンスへ
	 * {@code SourceAnchor}として再付与する(P0: アンカーはボックス個体に
	 * 属する——次の破断で再び再生可能になるための系譜。保存すべきは
	 * 「ordinal→SourceAnchor」の対応のみで、元params/pos/boxの
	 * identityではない——codex設計§1.3)。
	 */
	private long eventId;

	/**
	 * @param doc    駆動先(新品の{@code DocumentBuilder})
	 * @param fromId 範囲先頭のEventId(sliceのordinalと1:1)
	 */
	public SegmentExecutor(final DocumentBuilder doc, final long fromId) {
		this.doc = doc;
		this.eventId = fromId;
	}

	/**
	 * 正規イベント({@link SegmentEvent})を1件駆動します(恒久経路)。
	 * {@link SegmentEvent.Barrier}は駆動不能——範囲の適格性は呼び出し側が
	 * 事前に検証している契約のため、ここでは失敗にする。
	 */
	public void execute(final SegmentEvent event) {
		switch (event) {
		case SegmentEvent.BeginBox(final BoxRecipe recipe) -> {
			final INonReplacedBox box = BoxRecipeBoxFactory.create(recipe);
			box.setSourceAnchor(this.eventId);
			this.doc.startBox(box);
		}
		case SegmentEvent.EndBox end -> this.doc.endBox();
		case SegmentEvent.Text(final int sourceOffset, final String text, final boolean fixed) -> {
			// toCharArray()は毎回freshな配列(下流のin-place変換に安全)
			final char[] ch = text.toCharArray();
			this.doc.characters(sourceOffset, ch, 0, ch.length, fixed);
		}
		case SegmentEvent.Replaced(final ReplacedRecipe recipe) -> {
			final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
			box.setSourceAnchor(this.eventId);
			this.doc.addReplacedBox(box);
		}
		case SegmentEvent.Barrier barrier -> throw new IllegalStateException("barrier event in replay range: " + barrier);
		}
		++this.eventId;
	}

	/**
	 * liveログのイベント({@code LayoutSource.Event})を1件駆動します
	 * (過渡経路——クラスjavadoc「段階的統合の設計」参照。Barrierゼロ化
	 * (3b-3/3b-4)後の3b-6で撤去予定)。ボックス構築は
	 * {@link BoxRecipeBoxFactory}のkind別カーネルと共有し、Charsは
	 * freshなコピーで駆動する。
	 */
	public void executeLive(final LayoutSource.Event event) {
		switch (event) {
		case LayoutSource.Start start -> {
			final INonReplacedBox box = BoxRecipeBoxFactory.create(start.kind(), start.params(), start.pos());
			box.setSourceAnchor(this.eventId);
			this.doc.startBox(box);
		}
		case LayoutSource.Replaced(final ReplacedRecipe recipe) -> {
			// E-6増分3b-3: 記録時にfreeze済み——正規経路と同じmaterialize
			// (execute()のSegmentEvent.Replaced armと同一の駆動)
			final AbstractReplacedBox box = BoxRecipeBoxFactory.createReplaced(recipe);
			box.setSourceAnchor(this.eventId);
			this.doc.addReplacedBox(box);
		}
		case LayoutSource.ReplacedLive(final AbstractReplacedBox recorded) -> {
			final AbstractReplacedBox fresh = isolatedReplayInstance(recorded);
			fresh.setSourceAnchor(this.eventId);
			this.doc.addReplacedBox(fresh);
		}
		case LayoutSource.Chars(final int charOffset, final LayoutSource.TextPayload payload, final boolean fixed) -> {
			// 保持配列を直接渡さない(クラスjavadoc「fresh copy化」。
			// freshChars()はInline=clone、Spilled=storeからのdecodeで、
			// どちらも毎回freshな配列を返す——E-6増分3b-2)
			final char[] fresh = payload.freshChars();
			this.doc.characters(charOffset, fresh, 0, fresh.length, fixed);
		}
		case LayoutSource.EndBlock end -> this.doc.endBox();
		case LayoutSource.Opaque opaque -> throw new IllegalStateException("opaque event in replay range");
		}
		++this.eventId;
	}

	/**
	 * {@code ReplacedLive}(記録時にfreezeできなかった置換要素)の再生用
	 * インスタンスを作ります(E-6増分3b-3——live経路の潜在欠陥の是正)。
	 *
	 * <p>
	 * {@code newReplayInstance()}はparams(=image含む)を共有するため、
	 * imageが{@link ReplacedBoxImage}の場合、再生ボックスの
	 * {@code calculateSize}が呼ぶ{@code image.setReplacedBox(this, ...)}が
	 * back-referenceをlive側から奪い、scratch計測再生がliveの画像状態を
	 * 破壊しうる(凍結経路はBarrierで防御済みだったが、live再生経路は
	 * 無防備だった)。ここでは{@link ReplacedBoxImage#duplicate()}の複製を
	 * 持つ独立params({@link ReplacedParamsTemplate#freezeForReplayIsolation}
	 * →materialize)で再生ボックスを作る——scratch計測は複製へ登録して
	 * 捨て、実再生(次ページへ移動する部分木)は自分が描画する複製へ
	 * 正しく登録する。どちらの場合もlive側の画像は触られない。
	 * (現存する唯一の実装{@code BarcodeImage}の{@code setReplacedBox}は
	 * no-opのため、この隔離は出力に影響しない=挙動不変。)
	 * </p>
	 */
	static AbstractReplacedBox isolatedReplayInstance(final AbstractReplacedBox recorded) {
		final net.zamasoft.foliojet.layout.box.params.ReplacedParams params = recorded.getReplacedParams();
		if (params.image instanceof ReplacedBoxImage unsafe) {
			return recorded.newReplayInstance(
					ReplacedParamsTemplate.freezeForReplayIsolation(params, unsafe.duplicate()).materialize());
		}
		return recorded.newReplayInstance();
	}

	/**
	 * liveのCharsイベントを部分範囲だけ駆動します(切断段落の尾部再生
	 * {@code SourceReplayer.replayTextTail}専用——先頭のskip・配達済み
	 * 終端での打ち切りで範囲が空になることがあり、その場合も
	 * ordinalは1イベントぶん進める)。駆動する場合はfreshなコピーを渡す。
	 *
	 * @param charOffset 駆動範囲のソース文字オフセット
	 * @param ch         記録済み配列(変更しない)
	 * @param off        配列内の開始位置
	 * @param len        駆動する文字数(0以下なら駆動なしでordinalだけ進める)
	 * @param fixed      docプロトコルの固定テキストフラグ
	 */
	public void executeLiveCharsRange(final int charOffset, final char[] ch, final int off, final int len,
			final boolean fixed) {
		if (len > 0) {
			final char[] fresh = Arrays.copyOfRange(ch, off, off + len);
			this.doc.characters(charOffset, fresh, 0, len, fixed);
		}
		++this.eventId;
	}
}
