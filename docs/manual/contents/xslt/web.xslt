<?xml version="1.0" encoding="UTF-8"?>
	<!--+
    | ウェブ公開用の1ページHTMLを生成します。
    |
    | join.xslt(PDF用の結合)を読み込み、head と body の枠だけを差し替えます。
    | 章の取り込み・ioprop の自動リンクといった本体の規則は join.xslt を
    | そのまま使うので、PDF版とウェブ版で内容がずれません。
    |
    | クライアント側のXSLTには依存しません(ビルド時にSaxonで変換します)。
    +-->
<xsl:stylesheet version="1.0" xmlns="http://www.w3.org/1999/xhtml"
	xmlns:html="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="join.xslt" />

	<xsl:output method="xml" encoding="UTF-8" indent="no"
		doctype-public="-//W3C//DTD XHTML 1.0 Transitional//EN"
		doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd" />

	<xsl:template match="html:head">
		<xsl:copy>
			<base href="contents/" />
			<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<meta name="viewport" content="width=device-width, initial-scale=1" />
			<link rel="StyleSheet" type="text/css" href="style/web.css" />
			<xsl:copy-of select="html:title" />
		</xsl:copy>
	</xsl:template>

	<xsl:template match="/html:html/html:body">
		<xsl:copy>
			<div class="site">
				<nav class="toc">
					<div class="toc-title">
						<xsl:value-of select="../html:head/html:title/text()" />
					</div>
					<ul>
						<xsl:apply-templates select="//html:li" mode="toc" />
					</ul>
				</nav>
				<main class="doc">
					<xsl:apply-templates select="*" mode="include" />
				</main>
			</div>
		</xsl:copy>
	</xsl:template>

	<!--+ 目次: 章ファイルの見出しを拾う +-->
	<xsl:template match="html:li" mode="toc">
		<xsl:variable name="href" select="text()" />
		<xsl:for-each
			select="document(concat('../', $href))/html:html/html:body//html:h2
				| document(concat('../', $href))/html:html/html:body//html:h3">
			<li>
				<xsl:attribute name="class">
					<xsl:choose>
						<xsl:when test="local-name() = 'h2'">toc-h2</xsl:when>
						<xsl:otherwise>toc-h3</xsl:otherwise>
					</xsl:choose>
				</xsl:attribute>
				<a>
					<xsl:attribute name="href">
						<xsl:text>#</xsl:text>
						<xsl:call-template name="heading-id">
							<xsl:with-param name="h" select="." />
						</xsl:call-template>
					</xsl:attribute>
					<xsl:value-of select="normalize-space(.)" />
				</a>
			</li>
		</xsl:for-each>
	</xsl:template>

	<!--+
	    | 見出しの参照先。h2 自身の id、無ければ中の a/@id、
	    | それも無ければ generate-id()。generate-id() は同じ変換の中で
	    | 安定するので、本文側(heading-anchor)と必ず一致する。
	    +-->
	<xsl:template name="heading-id">
		<xsl:param name="h" />
		<xsl:choose>
			<xsl:when test="$h/@id">
				<xsl:value-of select="$h/@id" />
			</xsl:when>
			<xsl:when test="$h/html:a/@id">
				<xsl:value-of select="$h/html:a/@id" />
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="generate-id($h)" />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<!--+ 本文側: id の無い見出しに目次から辿れる id を与える +-->
	<xsl:template
		match="html:h2[not(@id) and not(html:a/@id)] | html:h3[not(@id) and not(html:a/@id)]"
		mode="contents">
		<xsl:copy>
			<xsl:attribute name="id">
				<xsl:value-of select="generate-id(.)" />
			</xsl:attribute>
			<xsl:apply-templates select="@*|node()" mode="contents" />
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>
