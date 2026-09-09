# -*- coding: utf-8 -*-
"""説明書の機械的検査。観点ごとに件数を出す。"""
import io, re, glob, os, sys, collections, json
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

D = "contents"
BACKTICK_SPAN = re.compile(chr(96) + '[^' + chr(96) + chr(10) + ']*' + chr(96))
files = sorted(glob.glob(D + "/*.md"))
src = {f: io.open(f, encoding="utf-8").read() for f in files}
allmd = "".join(src.values())
F = collections.defaultdict(list)   # 観点 -> [(file, 内容)]


def strip_code(s):
    """コード例を空行に置き換えます。行番号は変えません。

    例示の中の `id="a"` や `<img src="kappa.png">` は**文書の中身ではなく
    利用者に見せる原稿**です。これを混ぜると、実在しない重複idやリンク切れが
    大量に出て、本物の指摘が埋もれます(実際に埋もれていた)。
    """
    out, infence = [], False
    for l in s.split("\n"):
        if l.lstrip().startswith("```"):
            infence = not infence
            out.append("")
            continue
        out.append("" if infence else BACKTICK_SPAN.sub("符", l))
    return "\n".join(out)


prose = {f: strip_code(s) for f, s in src.items()}


def add(lens, f, msg):
    F[lens].append((os.path.basename(f), msg))


# --- 1. アンカー整合 -------------------------------------------------
ids = set()
for f, s in prose.items():
    ids |= set(re.findall(r'id="([^"]+)"', s))
for f, s in prose.items():
    for m in re.finditer(r'href="#([^"]+)"', s):
        if m.group(1) not in ids:
            add("リンク切れ", f, m.group(1))
    for m in re.finditer(r'\]\(#([^)]+)\)', s):
        if m.group(1) not in ids:
            add("リンク切れ", f, m.group(1))
    for m in re.finditer(r'href="([0-9]{4}_[a-z_-]*\.html[^"]*)"', s):
        add("ファイル間リンク", f, m.group(1))

# --- 2. ioprop 自動リンク(join.xslt が #appx-ioprop-X を作る) --------
props = set(re.findall(r'id="appx-ioprop-([a-zA-Z0-9._-]+)"', allmd))
for f, s in src.items():
    for m in re.finditer(r'<span class="ioprop">([^<]+)</span>', s):
        n = m.group(1).strip()
        if n not in props:
            add("ioprop自動リンク切れ", f, n)
    # 入れ子のマークアップ自体は問題ない。join.xslt は最初のテキスト節点だけを
    # 参照先にするので、`input.http.header.<i>n</i>.name` は
    # `#appx-ioprop-input.http.header.` へ繋がる(末尾のドットで終わるidが
    # そのために用意されている)。繋がらないときだけ指摘する
    for m in re.finditer(r'<span class="ioprop">([^<]*)<(?!/span>)', s):
        if m.group(1) not in props:
            add("iopropの入れ子が繋がらない", f, m.group(0)[:50])

# --- 3. 重複id -------------------------------------------------------
c = collections.Counter()
for f, s in prose.items():
    for i in re.findall(r'\sid="([^"]+)"', s):
        c[i] += 1
for i, n in c.items():
    if n > 1:
        add("重複id", "(全体)", "%s x%d" % (i, n))

# --- 3b. 画像 --------------------------------------------------------
# 参照先が無い画像は、PDFでは代替テキストの箱、HTMLでは壊れた印になる。
# どちらも生成物を開くまで気づけないので、ここで見る。
used = set()
# 表紙・奥付は .md ではないので prose に入っていない。画像はここから参照される
extra = {x: io.open(x, encoding="utf-8").read() for x in glob.glob(D + "/misc/*.html")}
# join.xslt が <base href="contents/"> を置くので、章がどこにあっても
# 画像の参照は contents/ から解決される(misc/ の表紙・奥付も同じ)
IMG = re.compile(r'(?:src|data)="([^"]+\.(?:png|jpe?g|gif|svgz?))"')
# 「無い」は本文だけを見る。コード例の中の src は利用者に見せる原稿であって、
# ここに実物がある必要はない(4700/4800 の kappa.png がこれ)
for f, s in list(prose.items()) + list(extra.items()):
    for m in IMG.finditer(s):
        if not os.path.exists(os.path.join(D, m.group(1))):
            add("画像が無い", f, m.group(1))
# 「使われていない」はコード例も数える。写して使う素材は残す必要がある
for f, s in list(src.items()) + list(extra.items()):
    for m in IMG.finditer(s):
        used.add(os.path.normpath(os.path.join(D, m.group(1))).replace(os.sep, "/"))
        used.add(os.path.normpath(os.path.join(D, "images",
                 os.path.basename(m.group(1)))).replace(os.sep, "/"))
for p in sorted(glob.glob(D + "/images/*")):
    q = os.path.normpath(p).replace(os.sep, "/")
    if os.path.isfile(p) and q not in used:
        # スタイルシートが背景に使う画像は本文から参照されない
        if os.path.basename(p) not in "".join(
                io.open(x, encoding="utf-8").read() for x in glob.glob(D + "/style/*.css")):
            add("どこからも使われない画像", "(images)", os.path.basename(p))

# --- 4. タグの釣り合い -----------------------------------------------
TAGS = ("span", "td", "tr", "table", "div", "dl", "dt", "dd", "a", "tbody",
        "thead", "p", "li", "ul", "ol", "b", "tt", "caption", "th", "code", "em", "i")
for f, s in src.items():
    t = re.sub(r'```.*?```', '', s, flags=re.S)
    t = re.sub(BACKTICK_SPAN, '', t)   # インラインコードも除く
    for tag in TAGS:
        op = len(re.findall(r'<%s(?=[\s>/])' % tag, t))
        cl = len(re.findall(r'</%s>' % tag, t))
        sc = len(re.findall(r'<%s(?=[\s>])[^>]*/>' % tag, t))
        if op - sc != cl:
            add("タグ不釣合", f, "<%s> %d/%d/%d" % (tag, op, sc, cl))

# --- 4b. 字下げでコードブロック化するブロック要素 -----------------------
BLOCK_START = re.compile(r'^(	+| {4,})(<(?:table|div|p|ul|ol|dl|h[1-6]|blockquote))')
for f, s in src.items():
    lines = s.splitlines()
    infence = False
    for i, l in enumerate(lines):
        if l.lstrip().startswith("```"):
            infence = not infence
            continue
        if infence:
            continue
        if i > 0 and lines[i - 1].strip() != "":
            continue          # 直前が空行のときだけコードブロックになる
        if BLOCK_START.match(l):
            add("字下げでコード扱いになる", f, "%d行 %s" % (i + 1, l.strip()[:40]))

# --- 5. 版数の古さ ---------------------------------------------------
for f, s in prose.items():
    # 動作環境の章は、旧版から移る人のために版数を比べる。ここだけは残す
    if os.path.basename(f) == "1020_requirements.md":
        continue
    for m in re.finditer(r'Copper PDF (3\.[0-9]+(\.[0-9]+)?)', s):
        add("旧版数の言及", f, m.group(0))

# --- 6. 表記ゆれ -----------------------------------------------------
PAIRS = [("下さい", "ください"), ("出来る", "できる"), ("行なう", "行う"),
         ("ディレクトリー", "ディレクトリ"), ("サーバ[^ー]", "サーバー"),
         ("ユーザ[^ー]", "ユーザー"), ("プロパティー", "プロパティ")]
for f, s in prose.items():
    for a, b in PAIRS:
        for m in re.finditer(a, s):
            add("表記ゆれ", f, "%s (→%s)" % (m.group(0), b))

# --- 7. 句読点・括弧 -------------------------------------------------
for f, s in prose.items():
    inpre = False
    for i, l in enumerate(s.split("\n"), 1):
        # <pre> の中は原稿。禁則文字の一覧のように、対にならない括弧が正しい
        if "<pre" in l:
            inpre = True
        if "</pre>" in l:
            inpre = False
            continue
        if inpre:
            continue
        if re.search(r'。。|、、|！！|？？', l):
            add("句読点重複", f, "%d行" % i)
        for a, b in [("「", "」"), ("（", "）"), ("『", "』")]:
            if l.count(a) != l.count(b) and "<rp>" not in l:
                add("括弧不一致", f, "%d行 %s" % (i, l.strip()[:40]))
        if re.search(r'[Ａ-Ｚａ-ｚ]', l.replace("ＭＳ", "")):
            add("全角英字", f, "%d行" % i)

# --- 8. 実装に無い -cssj- プロパティ ---------------------------------
impl = set(io.open(sys.argv[1], encoding="utf-8").read().split()) if len(sys.argv) > 1 else set()
if impl:
    for f, s in src.items():
        t = re.sub(r'```.*?```', '', s, flags=re.S)
        for m in re.finditer(r'-cssj-[a-z0-9-]+', t):
            if m.group(0) not in impl and os.path.basename(f) != "5600_removed.md":
                add("実装に無いCSS拡張", f, m.group(0))

for lens in sorted(F, key=lambda k: -len(F[k])):
    v = F[lens]
    print("== %s : %d件" % (lens, len(v)))
    seen = set()
    for fn, msg in v:
        k = (fn, msg)
        if k in seen:
            continue
        seen.add(k)
        if len(seen) <= 8:
            print("   %-26s %s" % (fn, msg))
    if len(seen) > 8:
        print("   ... 他 %d種" % (len(seen) - 8))
print("\n観点数:", len(F), " 総指摘:", sum(len(v) for v in F.values()))
