package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class Display extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Display();

	public static byte get(CSSStyle style) {
		DisplayValue value = (DisplayValue) style.get(INFO);
		return value.getDisplay();
	}

	protected Display() {
		super("display");
	}

	/**
	 * display:contentsの祖先を飛ばした、箱の木の上での実質の親displayを
	 * 返します(2026-08-07)。contents要素は箱を作らないため、匿名箱の補完や
	 * flex/gridアイテム化の判定は最も近い非contents祖先を親と見なす必要が
	 * あります。親が無ければNONEを返します(呼び出し側の既定分岐に落ちる)。
	 */
	public static byte getFlattenedParentDisplay(CSSStyle style) {
		for (CSSStyle p = style.getParentStyle(); p != null; p = p.getParentStyle()) {
			final byte d = Display.get(p);
			if (d != DisplayValue.CONTENTS) {
				return d;
			}
		}
		return DisplayValue.NONE;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		byte display = ((DisplayValue) value).getDisplay();

		// display:contents(CSS Display 3 §2.5)。要素自身の箱を作らない。
		// 置換要素(img等)のcontentsは「中身」が置換内容そのものなので
		// noneと同じ振る舞いになる(仕様どおり)。float/positionは箱が
		// 無いので適用されない——以下の変換は全て通らない
		if (display == DisplayValue.CONTENTS) {
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.NONE_VALUE;
			}
			return DisplayValue.CONTENTS_VALUE;
		}

		// **ページ単位のfloat(脚注・ページフロート)はブロック化する**
		// (2026-08-02、掃過で発覚)。これらは版面から切り離して置くため
		// 常にブロック箱として作られる。displayがtable系のまま残ると、
		// 子のtbody/trが「表の箱」を要求して構築に失敗していた
		// ——絶対配置のdisplayブロック化と同じ理由(CSS Display 3 §2.7)
		if (display != DisplayValue.NONE && CSSFloatValue.isPageLevel(CSSFloat.get(style))) {
			return DisplayValue.BLOCK_VALUE;
		}

		// 浮動体のための変換
		switch (display) {
		case DisplayValue.INLINE_TABLE: {
			final byte position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.TABLE_VALUE;
				display = DisplayValue.TABLE;
			}
		}
			break;
		case DisplayValue.INLINE: {
			final short position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				value = DisplayValue.INLINE_BLOCK_VALUE;
				display = DisplayValue.INLINE_BLOCK;
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;
		case DisplayValue.INLINE_BLOCK: {
			final short position = CSSPosition.get(style);
			if (position == PositionValue.ABSOLUTE || position == PositionValue.FIXED) {
				break;
			}
			if (CSSFloat.get(style) != CSSFloatValue.NONE) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;
		case DisplayValue.TABLE_CAPTION:
			// テーブル外のキャプションはブロック扱い
			final CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null) {
				switch (Display.getFlattenedParentDisplay(style)) {
				case DisplayValue.TABLE:
				case DisplayValue.TABLE_COLUMN_GROUP:
				case DisplayValue.TABLE_COLUMN:
				case DisplayValue.TABLE_ROW_GROUP:
				case DisplayValue.TABLE_HEADER_GROUP:
				case DisplayValue.TABLE_FOOTER_GROUP:
				case DisplayValue.TABLE_ROW:
					break;
				default:
					value = DisplayValue.BLOCK_VALUE;
					display = DisplayValue.BLOCK;
					break;
				}
			}
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL: {
			final short position = CSSPosition.get(style);
			if (CSSFloat.get(style) != CSSFloatValue.NONE
					|| (position != PositionValue.STATIC && position != PositionValue.RELATIVE
							&& position != PositionValue.STICKY)) {
				value = DisplayValue.BLOCK_VALUE;
				display = DisplayValue.BLOCK;
			}
		}
			break;

		case DisplayValue.NONE:
		case DisplayValue.BLOCK:
		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE:
		case DisplayValue.GRID:
		case DisplayValue.FLEX:
			break;
		default:
			throw new IllegalStateException();
		}

		// Grid/Flex直接子のblock化(Grid G0——css-grid-1 §6、Flex F0a——
		// css-flexbox-1 §4「flex itemはblockify」。inline系の子は
		// 匿名itemではなくブロックへ昇格させる)
		if (display == DisplayValue.INLINE || display == DisplayValue.INLINE_BLOCK) {
			final CSSStyle flexParent = style.getParentStyle();
			if (flexParent != null) {
				// contents祖先は飛ばす——contentsの子はflex/gridの直接の
				// アイテムになる(CSS Display 3 §2.5)
				final byte parentDisplay = Display.getFlattenedParentDisplay(style);
				if (parentDisplay == DisplayValue.GRID || parentDisplay == DisplayValue.FLEX) {
					value = DisplayValue.BLOCK_VALUE;
					display = DisplayValue.BLOCK;
				}
			}
		}

		// 置換ボックスのための変換
		switch (display) {
		case DisplayValue.INLINE_TABLE:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.INLINE_VALUE;
			}
			break;

		case DisplayValue.LIST_ITEM:
		case DisplayValue.TABLE:
		case DisplayValue.TABLE_ROW_GROUP:
		case DisplayValue.TABLE_HEADER_GROUP:
		case DisplayValue.TABLE_FOOTER_GROUP:
		case DisplayValue.TABLE_ROW:
		case DisplayValue.TABLE_CELL:
		case DisplayValue.TABLE_CAPTION:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.BLOCK_VALUE;
			}
			break;

		case DisplayValue.TABLE_COLUMN:
		case DisplayValue.TABLE_COLUMN_GROUP:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.NONE_VALUE;
			}
			break;

		case DisplayValue.GRID:
		case DisplayValue.FLEX:
			if (CSSJInternalImage.getImage(style) != null) {
				return DisplayValue.BLOCK_VALUE;
			}
			break;

		case DisplayValue.INLINE:
		case DisplayValue.NONE:
		case DisplayValue.BLOCK:
		case DisplayValue.INLINE_BLOCK:
			break;
		default:
			throw new IllegalStateException();
		}

		// 縦中横/横中縦のための変換
		if (display == DisplayValue.INLINE) {
			CSSStyle parentStyle = style.getParentStyle();
			if (parentStyle != null && BlockFlow.get(parentStyle).isVertical() != BlockFlow.get(style).isVertical()) {
				return DisplayValue.INLINE_BLOCK_VALUE;
			}
		}

		return value;
	}

	public Value getDefault(CSSStyle style) {
		return DisplayValue.INLINE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return DisplayValue.NONE_VALUE;
			} else if (ident.equals("block")) {
				return DisplayValue.BLOCK_VALUE;
			} else if (ident.equals("inline")) {
				return DisplayValue.INLINE_VALUE;
			} else if (ident.equals("inline-block")) {
				return DisplayValue.INLINE_BLOCK_VALUE;
			} else if (ident.equals("list-item")) {
				return DisplayValue.LIST_ITEM_VALUE;
			} else if (ident.equals("contents")) {
				return DisplayValue.CONTENTS_VALUE;
			} else if (ident.equals("flow-root")) {
				return DisplayValue.FLOW_ROOT_VALUE;
				// run-in は非対応(4で廃止。CSS Display 3 でも at-risk)。
				// 未対応値として宣言ごと無効にする(モダンブラウザと同じ)
			} else {
				if (ident.equals("table")) {
					return DisplayValue.TABLE_VALUE;
				} else if (ident.equals("inline-table")) {
					return DisplayValue.INLINE_TABLE_VALUE;
				} else if (ident.equals("table-row-group")) {
					return DisplayValue.TABLE_ROW_GROUP_VALUE;
				} else if (ident.equals("table-column")) {
					return DisplayValue.TABLE_COLUMN_VALUE;
				} else if (ident.equals("table-column-group")) {
					return DisplayValue.TABLE_COLUMN_GROUP_VALUE;
				} else if (ident.equals("table-header-group")) {
					return DisplayValue.TABLE_HEADER_GROUP_VALUE;
				} else if (ident.equals("table-footer-group")) {
					return DisplayValue.TABLE_FOOTER_GROUP_VALUE;
				} else if (ident.equals("table-row")) {
					return DisplayValue.TABLE_ROW_VALUE;
				} else if (ident.equals("table-cell")) {
					return DisplayValue.TABLE_CELL_VALUE;
				} else if (ident.equals("grid")) {
					return DisplayValue.GRID_VALUE;
				} else if (ident.equals("flex")) {
					// Flex F0a(consult-codex-2026-08-02-flexbox.txt)
					return DisplayValue.FLEX_VALUE;
				} else if (ident.equals("inline-flex")) {
					// **インラインレベルのflex/gridはブロックレベルで近似**
					// (2026-08-11)。真のinline-flexは「行の中に置ける原子箱の
					// 中身をflexで組む」もので、外=inline-block・内=flexの
					// 二重箱が要る。それまでは宣言ごと捨てる旧挙動より近い
					// ——捨てるとflexコンテナがただのブロックに戻り、横に
					// 並ぶはずのナビ13項目が縦に積まれて本文の上へ507pt
					// 覆いかぶさっていた(sankei.comのグローバルナビで実測)。
					// 行の中に置かれた小さなinline-flex(バッジ等)は本来より
					// 行が分かれる——どちらの誤差を採るかはimageTestの実測で
					// 決めた
					return DisplayValue.FLEX_VALUE;
				} else if (ident.equals("inline-grid")) {
					// inline-flexと同じ近似(上のコメント参照)
					return DisplayValue.GRID_VALUE;
				} else if (ident.equals("table-caption")) {
					return DisplayValue.TABLE_CAPTION_VALUE;
				}
				// 接頭辞つきの別名(2026-08-29)。実サイト50件中33件・4777回
				// と、未対応値の中で群を抜いて多かった
				switch (ident) {
				case "-webkit-flex":
				case "-moz-flex":
				case "-ms-flexbox":
				case "-webkit-inline-flex":
				case "-ms-inline-flexbox":
					// 2012年版flexbox。現行のflexと同じ(inline-*は上の
					// inline-flexと同じ近似)
					return DisplayValue.FLEX_VALUE;
				case "-ms-grid":
					return DisplayValue.GRID_VALUE;
				case "-webkit-box":
				case "-moz-box":
				case "-webkit-inline-box":
					// 2009年版flexbox。**flexへは写さない**——
					// `display:-webkit-box; -webkit-line-clamp:N` の行数
					// 切り詰め慣用句は箱がブロックであることに依存し、
					// flexにすると中身が1行に並ぶ。実サイトは必ず直後に
					// 現行の `display:flex` を重ねて書くので、flexが要る
					// 場面ではカスケード順でそちらが勝つ
					return DisplayValue.BLOCK_VALUE;
				default:
					break;
				}
			}
		}
		throw new PropertyException();
	}

}
