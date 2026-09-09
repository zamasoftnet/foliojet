<?xml version="1.0" encoding="UTF-8"?>
	<!--+
    | ドキュメントを結合して１つのHTMLにします。
    +-->
<xsl:stylesheet version="1.0" xmlns="http://www.w3.org/1999/xhtml"
	xmlns:html="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="processing-instruction()" />

	<xsl:template match="@*|node()" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>

	<xsl:template match="/html:html/html:body">
		<xsl:copy>
			<xsl:apply-templates select="*" mode="include" />
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="html:head">
		<xsl:copy>
		    <base href="contents/" />
		    <link rel="StyleSheet" type="text/css" href="style/book.css" />
			<xsl:apply-templates select="*" mode="include" />
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="html:ul[@class='cssj-toc']" mode="include" priority="2">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="html:ul|html:ol" mode="include" priority="1">
		<xsl:apply-templates select="*" mode="include" />
	</xsl:template>

	<xsl:template match="html:li" mode="include">
		<xsl:variable name="href" select="text()" />
		<xsl:apply-templates
			select="document(concat('../', $href))/html:html/html:body/*" mode="contents" />
	</xsl:template>

	<xsl:template match="@*|node()" mode="include"
		priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" mode="include" />
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="html:span[@class='ioprop']" mode="contents">
		<html:a href="#appx-ioprop-{text()}" class="ioprop">
			<xsl:copy-of select="node()" />
		</html:a>
	</xsl:template>

	<xsl:template match="@*|node()" mode="contents" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" mode="contents" />
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
