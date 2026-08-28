package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code clip-path}です(css-shapes-1/css-masking-1、2026-08-22新設)。
 *
 * <p>
 * {@code none | [<basic-shape> || <geometry-box>]}。basic-shapeは
 * {@code inset()}(round・角半径x=y)・{@code circle()}・{@code ellipse()}・
 * {@code polygon()}に対応する。{@code path()}と{@code url()}参照は未対応
 * (宣言ごと無視)。描画は{@code AbstractContainerBox.clip()}が参照
 * ボックスの実寸で形状を解決し、既存のクリップ伝播(overflow:hidden・
 * mask-image近似と同じ経路)へ流す。
 * </p>
 *
 * <p>
 * {@code <basic-shape>}の解析・絶対化・形状化は{@code shape-outside}と
 * 共有するため{@link BasicShapes}へ移した(2026-08-29)。ここに残るのは
 * 値型と参照ボックスの既定(border-box)だけ。
 * </p>
 */
public class ClipPath extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ClipPath();

	/**
	 * パース済みの形状指定です。
	 *
	 * @param shape    形状(nullなら参照ボックスのみの指定)
	 * @param box      参照ボックス(未指定はborder-box)
	 */
	public record ClipPathValue(ShapeSpec shape, ClipPathShape.ReferenceBox box) implements Value {
	}

	public static Value get(final CSSStyle style) {
		return style.get(INFO);
	}

	/** computed valueからレイアウト用の形状を作ります(noneはnull)。 */
	public static ClipPathShape toShape(final Value value) {
		if (!(value instanceof ClipPathValue v)) {
			return null;
		}
		return BasicShapes.toShape(v.shape(), v.box());
	}

	protected ClipPath() {
		super("clip-path");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (!(value instanceof ClipPathValue v) || v.shape() == null) {
			return value;
		}
		// em等のフォント相対長をここで絶対化する(%はそのまま)
		return new ClipPathValue(BasicShapes.absolutize(v.shape(), style), v.box());
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		ShapeSpec shape = null;
		ClipPathShape.ReferenceBox box = null;
		while (tokens.hasNext()) {
			final CssToken lu = tokens.next();
			if (lu instanceof CssToken.Ident ident) {
				if (ident.is("none")) {
					if (shape != null || box != null || tokens.hasNext()) {
						throw new PropertyException();
					}
					return KeywordValue.NONE;
				}
				final ClipPathShape.ReferenceBox rb = BasicShapes.toReferenceBox(ident);
				if (rb == null || box != null) {
					throw new PropertyException();
				}
				box = rb;
				continue;
			}
			if (!(lu instanceof CssToken.Func func) || shape != null) {
				throw new PropertyException();
			}
			shape = BasicShapes.parseFunction(func, ua);
		}
		if (shape == null && box == null) {
			throw new PropertyException();
		}
		return new ClipPathValue(shape, box == null ? ClipPathShape.ReferenceBox.BORDER_BOX : box);
	}
}
