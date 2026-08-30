package net.zamasoft.foliojet.css.impl.property.image;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * {@code image-orientation}(css-images-3 §5.3、2026-08-30)。
 *
 * <p>
 * ラスタ画像のEXIFの向きを尊重するか({@code from-image}、初期値)、
 * 無視して素の画素の並びで描くか({@code none})。継承する。
 *
 * <p>
 * この製品は読み込みの時点でEXIFの向きを適用しているので、{@code none}は
 * <b>適用済みの向きを外す</b>操作になる。{@link net.zamasoft.foliojet.ua.impl.image.RasterImageLoader}
 * が向きの包みに専用の型を付けているので、それだけを取り除けばよい
 * ({@code withoutOrientation})。固有寸法もこれで元へ戻る。
 *
 * <p>
 * <b>角度指定は受け付けない。</b>css-images-3の初期の草案には
 * {@code 90deg}等があったが、現行仕様で落ちている(実ブラウザも解釈しない)。
 */
public class ImageOrientation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ImageOrientation();

	/**
	 * EXIFの向きを尊重するかを返します。
	 */
	public static boolean isFromImage(final CSSStyle style) {
		return style.get(INFO) != KeywordValue.NONE;
	}

	/**
	 * このスタイルの{@code image-orientation}に従って画像を整えます。
	 * {@code from-image}(初期値)なら同じ実体をそのまま返します。
	 */
	public static Image apply(final CSSStyle style, final Image image) {
		if (image == null || isFromImage(style)) {
			return image;
		}
		return net.zamasoft.foliojet.ua.impl.image.RasterImageLoader.withoutOrientation(image);
	}

	protected ImageOrientation() {
		super("image-orientation");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return KeywordValue.FROM_IMAGE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext() || !(token instanceof CssToken.Ident ident)) {
			throw new PropertyException();
		}
		if (ident.is("from-image")) {
			return KeywordValue.FROM_IMAGE;
		}
		if (ident.is("none")) {
			return KeywordValue.NONE;
		}
		throw new PropertyException();
	}
}
