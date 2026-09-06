package net.zamasoft.foliojet.layout;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.builder.PageGenerator;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 測定用のページ生成器です(M2c)。
 *
 * <p>
 * 指定寸法の scratch ページを生成し、描画は何もしません。
 * ソースイベントの範囲を {@link SourceReplayer} でこの生成器へ再生する
 * ことで、実レイアウトによる計測(min/max-content、収まりのプローブ)を
 * ライブの状態に一切触れずに行えます。scratch 再生は新品のボックスを
 * 作るため、旧2パス計測が抱えていた「可変ボックスの共有・一回消費」の
 * 制約(M2 再ステージの原因)はここには存在しません。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class MeasurePageGenerator implements PageGenerator {
	private final UserAgent ua;

	private final BlockParams pageParams;
	private final LayoutSource layoutSource;

	private PageBox lastPage;

	private int pageCount = 0;

	/**
	 * 測定用ページ生成器を作ります。
	 *
	 * @param ua       ユーザーエージェント
	 * @param template 書体・書字方向等を引き継ぐ計算済みパラメータ
	 * @param width    scratch ページの幅
	 * @param height   scratch ページの高さ
	 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height) {
		this(ua, template, width, height, null);
	}

	/** 再生元を借用します。scratch側から追記・compact・closeはしません。 */
	public MeasurePageGenerator(final UserAgent ua, final BlockParams template, final double width,
			final double height, final LayoutSource layoutSource) {
		this.ua = ua;
		this.layoutSource = layoutSource;
		final BlockParams params = new BlockParams();
		params.fontStyle = template.fontStyle;
		params.fontManager = template.fontManager;
		params.lineBreakRules = template.lineBreakRules;
		params.flow = template.flow;
		params.writingModeVariant = template.writingModeVariant;
		params.direction = template.direction;
		params.unicodeBidi = template.unicodeBidi;
		params.paragraphBidi = template.paragraphBidi;
		params.bidiSemanticAlias = template.bidiSemanticAlias;
		params.size = Dimension.create(width, height, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
		this.pageParams = params;
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	@Override
	public LayoutSource getLayoutSource() {
		return this.layoutSource;
	}

	public PageBreakMode getPageSide() {
		return PageBreakMode.AUTO;
	}

	public PageBox nextPage() {
		++this.pageCount;
		this.lastPage = new PageBox(this.pageParams, this.ua);
		return this.lastPage;
	}

	public boolean drawPage(final PageBox page, final boolean lastPage, final boolean closedByForcedBreak) {
		// 測定のみ: 何も出力しない。ページを落とすこともないので true
		return true;
	}

	/**
	 * 生成されたページ数を返します(収まりのプローブ用)。
	 */
	public int getPageCount() {
		return this.pageCount;
	}

	/**
	 * 最後に生成されたページを返します(内容の実測用)。
	 */
	public PageBox getLastPage() {
		return this.lastPage;
	}
}
