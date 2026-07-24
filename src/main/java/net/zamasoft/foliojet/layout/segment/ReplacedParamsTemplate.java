package net.zamasoft.foliojet.layout.segment;

import java.util.Optional;

import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Dimension;
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
 * 持つ。frozen templateから複数回{@code materialize()}した結果は互いに
 * 独立しているべき(M6d-Aの最重要契約)だが、その全てが同じ
 * {@code ReplacedBoxImage}インスタンスを指すと back-reference を
 * 取り合ってこの契約が壊れる。よって{@code freeze()}は
 * {@code ReplacedBoxImage}を検出すると{@code Optional.empty()}を返し
 * fail closedにする(「共有可能な不変サービスか再生成対象かを分類し、
 * 未知/不安全な型はBarrierとして扱う」という既存方針——A3a設計相談で
 * {@code FontManager}/{@code Image}等について確立済み——を{@code Image}
 * の具体的なケースへ適用したもの)。呼び出し側(変換アダプタ配線時)は
 * 空の場合{@link SegmentEvent.Barrier}(理由
 * {@link BarrierReason#NOT_YET_SUPPORTED})にfall backすること。
 * </p>
 *
 * <p>
 * Stage2(2026-07-22、他のTemplate/Fields群を不変recordへ置換)の
 * 唯一の例外として、この型だけは通常のfinalクラスのまま残す——
 * {@code public record}の正準コンストラクタはrecord自身と同じ
 * public可視性を強制されるため、record化すると上記のfail closed
 * 検証({@code freeze()}のみが構築経路)を誰でもバイパスできてしまう
 * (JLS 8.10.4.2)。これはM6d-Aの安全上重要な契約であり、record化の
 * 均一性より優先する。
 * </p>
 */
public final class ReplacedParamsTemplate {
	private final TextParamsFields common;
	private final Dimension size;
	private final Dimension minSize;
	private final Dimension maxSize;
	private final BoxSizingMode boxSizing;
	private final RectFrame frame;
	private final double lineHeight;
	private final Image image;

	private ReplacedParamsTemplate(final TextParamsFields common, final Dimension size, final Dimension minSize,
			final Dimension maxSize, final BoxSizingMode boxSizing, final RectFrame frame, final double lineHeight,
			final Image image) {
		this.common = common;
		this.size = size;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.boxSizing = boxSizing;
		this.frame = frame;
		this.lineHeight = lineHeight;
		this.image = image;
	}

	/**
	 * {@code source.image}が{@link ReplacedBoxImage}(共有不可な
	 * back-referenceを持つ)の場合は{@code Optional.empty()}を返す
	 * (fail closed)。
	 */
	public static Optional<ReplacedParamsTemplate> freeze(final ReplacedParams source) {
		if (source.image instanceof ReplacedBoxImage) {
			return Optional.empty();
		}
		return Optional.of(new ReplacedParamsTemplate(TextParamsFields.freeze(source), source.size, source.minSize,
				source.maxSize, source.boxSizing, source.frame, source.lineHeight, source.image));
	}

	/**
	 * replay隔離専用のfreezeです(E-6増分3b-3、2026-07-24)。
	 * {@link #freeze}のfail closed検証({@link ReplacedBoxImage}の拒否)を
	 * バイパスし、{@code source.image}の代わりに{@code freshImage}を
	 * 格納する。呼び出し側({@code SegmentExecutor}のReplacedLive再生駆動)
	 * は{@code freshImage}に{@link ReplacedBoxImage#duplicate()}が返した
	 * <b>独立した複製</b>を渡す契約——これによりmaterialize結果の
	 * back-reference書き込み先がライブ側の画像から隔離される。
	 * それ以外の用途で呼んではならない(package-private+契約コメントで
	 * fail closed設計を維持する)。
	 */
	static ReplacedParamsTemplate freezeForReplayIsolation(final ReplacedParams source, final Image freshImage) {
		return new ReplacedParamsTemplate(TextParamsFields.freeze(source), source.size, source.minSize, source.maxSize,
				source.boxSizing, source.frame, source.lineHeight, freshImage);
	}

	/** 呼び出しごとに新品の{@code ReplacedParams}を返す(複数回呼んでも互いに影響しない)。 */
	public ReplacedParams materialize() {
		final ReplacedParams params = new ReplacedParams();
		this.common.materializeInto(params);
		params.size = this.size;
		params.minSize = this.minSize;
		params.maxSize = this.maxSize;
		params.boxSizing = this.boxSizing;
		params.frame = this.frame;
		params.lineHeight = this.lineHeight;
		params.image = this.image;
		return params;
	}
}
