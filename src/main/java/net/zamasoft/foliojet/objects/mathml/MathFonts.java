package net.zamasoft.foliojet.objects.mathml;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.jeuclid.font.FontFactory;
import net.zamasoft.pdfg2d.font.FontSource;
import net.zamasoft.pdfg2d.font.FontSourceWrapper;
import net.zamasoft.pdfg2d.font.otf.OpenTypeFontSource;
import net.zamasoft.pdfg2d.gc.font.FontFamily;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * CSS の font-family を JEuclid の書体の候補へ写します(2026-10-04)。
 *
 * <p>
 * JEuclid は字の幅を AWT の書体で測り、輪郭として描く。CSS の書体名を
 * そのまま渡しても、AWT が知らない書体(Copper の書体設定・書体パックに
 * だけある書体)は使われず、JEuclid の既定の書体で組まれていた。ここでは
 * 名前ごとに Copper の書体管理から OpenType のファイルを引き、JEuclid の
 * 書体置き場へ登録して、その AWT 上の名前を候補の先頭に置く。正体と斜体
 * (JEuclid は書体名の italic で斜体の枠へ入れる)の 2 つを登録する。
 * 総称ファミリ(serif など)は JEuclid の既定の候補に任せる。
 * </p>
 *
 * <p>
 * 登録は JVM 全体で共有する JEuclid の置き場へ入るので、同じファイルは
 * 1 度だけ登録する。TTC の 2 番目以降の書体は AWT の
 * {@code Font.createFont} で選べないので登録せず、名前だけを渡す。
 * </p>
 */
final class MathFonts {
	private static final Logger LOG = Logger.getLogger(MathFonts.class.getName());

	/** ファイル→AWT の書体名(登録できなかったものは空文字)。 */
	private static final Map<String, String> REGISTERED = new ConcurrentHashMap<String, String>();

	/** ファイルを引くときの書体方針。ファイルを持つ書体を先に選ばせる。 */
	private static final FontPolicyList FILE_POLICY = new FontPolicyList(new FontPolicyList.FontPolicy[] {
			FontPolicyList.FontPolicy.EMBEDDED, FontPolicyList.FontPolicy.CID_IDENTITY,
			FontPolicyList.FontPolicy.CID_KEYED });

	private MathFonts() {
		// static only
	}

	/**
	 * CSS の書体の並びを、JEuclid の候補(AWT の書体名)にして{@code defaults}の
	 * 前に置いた並びを返します。
	 */
	static List<String> families(final FontManager fontManager, final FontStyle base, final List<String> defaults) {
		final List<String> list = new ArrayList<String>();
		final FontFamilyList families = base.getFamily();
		for (int i = 0; i < families.getLength(); ++i) {
			final FontFamily family = families.get(i);
			if (family.isGenericFamily()) {
				continue;
			}
			final String name = family.getName();
			final String awtName = fontManager == null ? null : register(fontManager, base, name);
			final String candidate = awtName != null ? awtName : name;
			if (!list.contains(candidate)) {
				list.add(candidate);
			}
		}
		for (final String name : defaults) {
			if (!list.contains(name)) {
				list.add(name);
			}
		}
		return list;
	}

	/** 正体と斜体を登録し、AWT の書体名を返します。引けなければ null。 */
	private static String register(final FontManager fontManager, final FontStyle base, final String name) {
		String awtName = null;
		for (final FontStyle.Style style : new FontStyle.Style[] { FontStyle.Style.NORMAL, FontStyle.Style.ITALIC }) {
			final OpenTypeFontSource source = lookup(fontManager, base, name, style);
			if (source == null) {
				continue;
			}
			final String registered = registerFile(source.getFile(), source.getIndex());
			if (awtName == null && registered != null && !registered.isEmpty()) {
				awtName = registered;
			}
		}
		return awtName;
	}

	private static OpenTypeFontSource lookup(final FontManager fontManager, final FontStyle base, final String name,
			final FontStyle.Style style) {
		final FontStyle fs = new FontStyleImpl(new FontFamilyList(new FontFamily(name)), base.getSize(), style,
				base.getWeight(), FontStyle.Direction.LTR, FILE_POLICY, null, false, false, null,
				FontSource.NORMAL_WIDTH_CLASS);
		final FontListMetrics flm = fontManager.getFontListMetrics(fs);
		if (flm == null || flm.getLength() == 0) {
			return null;
		}
		// 書体設定の別名は包み(FontSourceWrapper)の側にあることがあるので、
		// 包みのどの段で名前が合ってもよい
		FontSource source = flm.getFontMetrics(0).getFontSource();
		boolean named = matches(source, name);
		while (source instanceof FontSourceWrapper wrapper) {
			source = wrapper.getSource();
			named |= matches(source, name);
		}
		if (!named || !(source instanceof OpenTypeFontSource ots)) {
			// 名前が合わない=その名前の書体が無く、代わりの書体が選ばれた
			return null;
		}
		// 斜体を頼んで正体が返る(斜体の面が無い)なら、正体の登録で足りる
		return style == FontStyle.Style.NORMAL || ots.isItalic() ? ots : null;
	}

	private static boolean matches(final FontSource source, final String name) {
		if (name.equalsIgnoreCase(source.getFontName())) {
			return true;
		}
		final String[] aliases = source.getAliases();
		if (aliases != null) {
			for (final String alias : aliases) {
				if (name.equalsIgnoreCase(alias)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String registerFile(final File file, final int index) {
		if (file == null || index != 0) {
			return null;
		}
		final String key = file.getAbsolutePath().toLowerCase(Locale.ROOT);
		return REGISTERED.computeIfAbsent(key, k -> {
			try {
				// 置き場が空のまま登録すると、JEuclid が最初の取得で行う AWT の
				// 書体の読み込みが起きなくなる(空のときだけ読む)。先に起こす
				FontFactory.getInstance().getFont(Font.SERIF, Font.PLAIN, 12f);
				final Font font = FontFactory.getInstance().registerFont(Font.TRUETYPE_FONT, file);
				return font.getFamily();
			} catch (final Exception e) {
				LOG.log(Level.FINE, "MathML: " + file, e);
				return "";
			}
		});
	}
}
