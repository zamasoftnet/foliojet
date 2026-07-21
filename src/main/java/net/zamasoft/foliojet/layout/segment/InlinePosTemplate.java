package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.content.VerticalAlignPolicy;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * {@link InlinePos}({@link BoxKind#INLINE}が使う配置パラメータ)の
 * 内容をfreezeし、呼び出しごとに独立した新品の{@code InlinePos}を
 * materializeするテンプレートです(2026-07-22新設、M6d-A3b Stage1)。
 *
 * <p>
 * {@code offset}(祖先{@code AbstractStaticPos}、既に確認済みの不変
 * 値クラス)・{@code lineHeight}(プリミティブ)はそのまま保持する。
 * {@code verticalAlign}({@code VerticalAlignPolicy})は振る舞いを表す
 * 状態を持たないpolicyオブジェクト(実装{@code CSSVerticalAlignPolicy}
 * は`BASELINE_POLICY`等の共有singletonとして使われている)——
 * {@code FontManager}等と同じ「共有可能な不変サービス」として扱い、
 * コピーせず参照をそのまま保持する。
 * </p>
 */
public final class InlinePosTemplate {
	private final Offset offset;
	private final VerticalAlignPolicy verticalAlign;
	private final double lineHeight;

	private InlinePosTemplate(final Offset offset, final VerticalAlignPolicy verticalAlign,
			final double lineHeight) {
		this.offset = offset;
		this.verticalAlign = verticalAlign;
		this.lineHeight = lineHeight;
	}

	public static InlinePosTemplate freeze(final InlinePos source) {
		return new InlinePosTemplate(source.offset, source.verticalAlign, source.lineHeight);
	}

	/** 呼び出しごとに新品の{@code InlinePos}を返す(複数回呼んでも互いに影響しない)。 */
	public InlinePos materialize() {
		final InlinePos pos = new InlinePos();
		pos.offset = this.offset;
		pos.verticalAlign = this.verticalAlign;
		pos.lineHeight = this.lineHeight;
		return pos;
	}
}
