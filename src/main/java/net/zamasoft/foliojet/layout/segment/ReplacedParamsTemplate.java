package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.ObjectFitMode;
import net.zamasoft.foliojet.layout.box.params.Offset;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@link ReplacedParams}(置換要素のparams、{@link ReplacedRecipe}の
 * 4variantすべてが使う)の内容をfreezeし、呼び出しごとに独立した新品の
 * {@code ReplacedParams}をmaterializeするテンプレートです(2026-07-22
 * 新設、M6d-A Replaced要素対応)。
 *
 * <p>
 * {@code ReplacedParams}は{@code AbstractTextParams}を直接継承する
 * ({@code InlineParams}と同型)ため、祖先フィールドは
 * {@link TextParamsFields}に委譲する。固有フィールドの{@code size}/
 * {@code minSize}/{@code maxSize}({@code Dimension})・{@code boxSizing}
 * (enum)・{@code frame}({@code RectFrame})は{@link BlockParamsFields}と
 * 同じ理由(既存実装がfinalフィールドのみの実質不変値クラス)で
 * コピー不要、{@code lineHeight}はプリミティブでそのままコピーする。
 * </p>
 *
 * <p>
 * {@code image}({@code Image})だけは性質が異なる。通常の(URL等から
 * 読み込んだ)画像は不変・再入可能な共有リソースだが、
 * {@link ReplacedBoxImage}実装(現状{@code BarcodeImage}のみ)は
 * {@code AbstractReplacedBox.calculateSize()}が呼ぶ
 * {@code setReplacedBox(box, width, height)}で自分自身にlive boxへの
 * back-referenceを書き込む——レイアウトのたびに変異する共有不可な状態を
 * 持つ。旧実装(〜E-6増分3b-5)はこれを検出するとfail closedで
 * {@code Optional.empty()}を返しBarrier化していたが、E-6増分3b-6で
 * {@link ReplacedBoxImage#duplicate()}(独立複製)ベースの総関数へ変更
 * した: {@link #freeze}は記録時に複製を凍結してliveとの共有状態を切り、
 * {@link #materialize}は呼び出しごとにさらに複製を配って再生同士の
 * 共有状態も切る(frozen templateから複数回materializeした結果は互いに
 * 独立、というM6d-Aの最重要契約を保つ)——これによりBarrier化の理由が
 * 消え、置換要素のfreezeは失敗variantを持たない。
 * </p>
 *
 * <p>
 * Stage2(2026-07-22、他のTemplate/Fields群を不変recordへ置換)の
 * 唯一の例外として、この型だけは通常のfinalクラスのまま残す——
 * {@code public record}の正準コンストラクタはrecord自身と同じ
 * public可視性を強制されるため、record化すると上記のimage正規化
 * ({@code freeze()}のみが構築経路——凍結値にliveの
 * {@code ReplacedBoxImage}をそのまま格納できない保証)を誰でも
 * バイパスできてしまう(JLS 8.10.4.2)。これはM6d-Aの安全上重要な
 * 契約であり、record化の均一性より優先する。
 * </p>
 */
public final class ReplacedParamsTemplate {
	private final TextParamsFields common;
	private final Dimension size;
	private final Dimension minSize;
	private final Dimension maxSize;
	private final BoxSizingMode boxSizing;
	private final ObjectFitMode objectFit;
	private final Offset objectPosition;
	private final RectFrame frame;
	private final double lineHeight;
	private final Image image;

	private ReplacedParamsTemplate(final TextParamsFields common, final Dimension size, final Dimension minSize,
			final Dimension maxSize, final BoxSizingMode boxSizing, final ObjectFitMode objectFit,
			final Offset objectPosition, final RectFrame frame, final double lineHeight, final Image image) {
		this.common = common;
		this.size = size;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.boxSizing = boxSizing;
		this.objectFit = objectFit;
		this.objectPosition = objectPosition;
		this.frame = frame;
		this.lineHeight = lineHeight;
		this.image = image;
	}

	/**
	 * live paramsから凍結します(E-6増分3b-6で総関数化——失敗variantは
	 * ない)。{@code source.image}が{@link ReplacedBoxImage}(back-reference
	 * を持つ共有不可の画像)の場合は{@link ReplacedBoxImage#duplicate()}の
	 * 独立複製を凍結する——liveボックスの画像状態とテンプレートの間に
	 * 共有状態が残らない。
	 */
	public static ReplacedParamsTemplate freeze(final ReplacedParams source) {
		final Image image = source.image instanceof ReplacedBoxImage unsafe ? unsafe.duplicate() : source.image;
		return new ReplacedParamsTemplate(TextParamsFields.freeze(source), source.size, source.minSize,
				source.maxSize, source.boxSizing, source.objectFit, source.objectPosition, source.frame,
				source.lineHeight, image);
	}

	/** 呼び出しごとに新品の{@code ReplacedParams}を返す(複数回呼んでも互いに影響しない)。 */
	public ReplacedParams materialize() {
		final ReplacedParams params = new ReplacedParams();
		this.common.materializeInto(params);
		params.size = this.size;
		params.minSize = this.minSize;
		params.maxSize = this.maxSize;
		params.boxSizing = this.boxSizing;
		params.objectFit = this.objectFit;
		params.objectPosition = this.objectPosition;
		params.frame = this.frame;
		params.lineHeight = this.lineHeight;
		// ReplacedBoxImageはmaterializeごとに複製を配る——凍結済み複製自体を
		// 共有すると、複数の再生ボックスが
		// setReplacedBoxのback-referenceを取り合い「materialize結果は互いに
		// 独立」の契約が壊れる(E-6増分3b-6)
		params.image = this.image instanceof ReplacedBoxImage frozen ? frozen.duplicate() : this.image;
		return params;
	}
}
