<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:a="urn:a" xmlns:b="urn:b">
                
  <xsl:template match="/">
    <xsl:apply-templates/>
  </xsl:template>
                
  <xsl:template match="/root">
    <xsl:apply-templates/>
  </xsl:template>
                
  <xsl:template match="p64">
    <h1>
      <xsl:value-of select="text()"/>
    </h1>
  </xsl:template>
                
  <xsl:template match="red">
    <p>
      <xsl:value-of select="text()"/>
    </p>
  </xsl:template>
                
  <xsl:template match="blue">
    <a:p>
      <xsl:value-of select="text()"/>
    </a:p>
  </xsl:template>
                
  <xsl:template match="yellow">
    <b:p>
      <xsl:value-of select="text()"/>
    </b:p>
  </xsl:template>
  
</xsl:stylesheet>
