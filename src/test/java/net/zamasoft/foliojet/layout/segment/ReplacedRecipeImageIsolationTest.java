package net.zamasoft.foliojet.layout.segment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.ReplacedParams;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * E-6増分3b-3で新設・3b-6で全面改稿: {@link ReplacedBoxImage}
 * (back-referenceを持つ共有不可の画像)を参照する置換要素の
 * duplicateベースfreeze({@code ReplacedParamsTemplate}経由の
 * {@link ReplacedRecipe#freeze})が、live・再生間および再生同士の
 * 画像状態を隔離することの単体テストです。
 *
 * <p>
 * 現存する唯一のproduction実装({@code BarcodeImage})は
 * {@code setReplacedBox}がno-opのため、この隔離の欠如はgolden比較では
 * 観測できない(潜在欠陥)。back-referenceを実際に保存するスタブで
 * 「再生ボックスのcalculateSizeがlive側の画像状態を破壊しうる」構造
 * そのものを固定する。
 * </p>
 */
public class ReplacedRecipeImageIsolationTest extends TestCase {

	/** back-referenceを実際に保存する(BarcodeImageと違いno-opでない)スタブ。 */
	private static final class RecordingReplacedBoxImage implements Image, ReplacedBoxImage {
		AbstractReplacedBox registeredBox;

		public double getWidth() {
			return 10;
		}

		public double getHeight() {
			return 5;
		}

		public void drawTo(GC gc) {
		}

		public String getAltString() {
			return null;
		}

		public void setReplacedBox(AbstractReplacedBox box, double width, double height) {
			this.registeredBox = box;
		}

		public Image duplicate() {
			return new RecordingReplacedBoxImage();
		}
	}

	/**
	 * {@link ReplacedBoxImage}参照の置換要素はfreeze時に複製画像を凍結し、
	 * materializeごとにさらに複製を配る——再生ボックス(scratch計測・
	 * 複数の再生ボックス)のcalculateSizeがliveのimageへ
	 * back-referenceを書き込まない(奪わない)し、再生同士も取り合わない。
	 */
	public void testMaterializedReplayBoxesDoNotStealBackReference() {
		final RecordingReplacedBoxImage liveImage = new RecordingReplacedBoxImage();
		final ReplacedParams params = new ReplacedParams();
		params.image = liveImage;
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		// liveのレイアウト(calculateSize)がback-referenceを登録する
		live.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);

		// 記録時freeze(3b-6: ReplacedBoxImageでも総関数)
		final ReplacedRecipe recipe = ReplacedRecipe.freeze(live).orElseThrow();
		assertTrue(recipe instanceof ReplacedRecipe.Inline);

		// materialize×2 → 互いに独立した新品のreplayボックス
		final AbstractReplacedBox replay1 = BoxRecipeBoxFactory.createReplaced(recipe);
		final AbstractReplacedBox replay2 = BoxRecipeBoxFactory.createReplaced(recipe);
		assertNotSame(live, replay1);
		assertNotSame(params, replay1.getReplacedParams());
		assertNotSame(liveImage, replay1.getReplacedParams().image);
		assertNotSame(replay1.getReplacedParams().image, replay2.getReplacedParams().image);
		assertTrue(replay1.getReplacedParams().image instanceof ReplacedBoxImage);
		assertSame(liveImage, params.image);

		// replay側(scratch計測相当)のcalculateSizeはそれぞれ自分の複製へ
		// 登録し、liveのback-referenceは奪われない——是正対象の潜在欠陥
		// そのもの。複数再生(プローブ最大20試行)でも取り合いは起きない
		replay1.calculateSize(100, 100, 100, 100);
		replay2.calculateSize(100, 100, 100, 100);
		assertSame(live, liveImage.registeredBox);
		assertSame(replay1, ((RecordingReplacedBoxImage) replay1.getReplacedParams().image).registeredBox);
		assertSame(replay2, ((RecordingReplacedBoxImage) replay2.getReplacedParams().image).registeredBox);

		// 値としては同等のparamsで再生される(隔離は内容を変えない)
		assertEquals(params.lineHeight, replay1.getReplacedParams().lineHeight);
		assertEquals(params.size, replay1.getReplacedParams().size);
	}

	/** 通常の(共有可能な)imageでは従来どおりimageを共有する(paramsは新品)。 */
	public void testPlainImageKeepsSharedImage() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new Image() {
			public double getWidth() {
				return 10;
			}

			public double getHeight() {
				return 5;
			}

			public void drawTo(GC gc) {
			}

			public String getAltString() {
				return null;
			}
		};
		final InlineReplacedBox live = new InlineReplacedBox(params, new InlinePos());

		final ReplacedRecipe recipe = ReplacedRecipe.freeze(live).orElseThrow();
		final AbstractReplacedBox replay = BoxRecipeBoxFactory.createReplaced(recipe);
		assertNotSame(live, replay);
		assertNotSame(params, replay.getReplacedParams());
		assertSame(params.image, replay.getReplacedParams().image);
	}

	/** 未確定包含幅に対する% min-widthを番兵の実寸へ変換しない。 */
	public void testCyclicPercentageMinimumFallsBackToZero() {
		final ReplacedParams params = new ReplacedParams();
		params.image = new RecordingReplacedBoxImage();
		params.size = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
		params.minSize = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.ABSOLUTE);
		params.maxSize = Dimension.create(1, 0, LengthType.RELATIVE, LengthType.AUTO);
		final InlineReplacedBox box = new InlineReplacedBox(params, new InlinePos());

		box.calculateSize(LayoutUtils.NONE, LayoutUtils.NONE, LayoutUtils.NONE, LayoutUtils.NONE);

		assertEquals(10.0, box.getInnerWidth(), 0);
		assertEquals(5.0, box.getInnerHeight(), 0);
		assertTrue(LayoutUtils.isDrawable(box.getWidth()));
		assertTrue(LayoutUtils.isDrawable(box.getHeight()));
	}
}
