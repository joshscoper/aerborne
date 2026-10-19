package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class AerbornePresentationTest {
    @Test void tenBrandColorsAreStablePresentationTokens() {
        assertEquals("#77C8F5",BrandPalette.SKY);assertEquals("#F4F9FF",BrandPalette.CLOUD);
        assertEquals("#8CE8ED",BrandPalette.BREEZE);assertEquals("#70DBC3",BrandPalette.MINT);
        assertEquals("#C59AFA",BrandPalette.LAVENDER);assertEquals("#F4C76B",BrandPalette.GOLD);
        assertEquals("#FFBBA0",BrandPalette.PEACH);assertEquals("#283752",BrandPalette.TWILIGHT);
        assertEquals("#17243B",BrandPalette.MIDNIGHT);assertEquals("#8E9AAF",BrandPalette.STONE);
        assertTrue(BrandPalette.title("<red>literal</red>").contains("\\<red>literal\\</red>"));
    }
    @Test void allPaperFallbackMotdsMatchThePublicComponentWithoutNetworkChanges() throws Exception {
        for(String target:new String[]{"release","paper","test","skyblock"}) {
            var properties=new Properties();
            try(var reader=Files.newBufferedReader(Path.of("../deploy",target,"server.properties"))) { properties.load(reader); }
            assertEquals("AERBORNE » A Skyblock RPG Adventure\nBuild your island. Explore the skies. Discover magic.",properties.getProperty("motd"),target);
        }
    }
    @Test void panelHasBrandingButNoNewAssetsOrBrokenResourceRoutes() throws Exception {
        Path panel=Path.of("src/main/resources/panel");String html=Files.readString(panel.resolve("index.html"));
        assertTrue(html.contains("<title>Aerborne | Staff Console</title>"));
        assertTrue(html.contains("<span>Aerborne<small>Staff Console</small></span>"));
        for(String asset:new String[]{"/favicon.svg","/panel.css","/panel.js"}) assertTrue(html.contains('"'+asset+'"'));
        String svg=Files.readString(panel.resolve("favicon.svg"));
        assertTrue(svg.contains(BrandPalette.SKY)&&svg.contains(BrandPalette.BREEZE)&&svg.contains(BrandPalette.MIDNIGHT));
        String script=Files.readString(panel.resolve("panel.js"));
        assertTrue(script.contains("plugins/NerdSMP/geoip/"),"A visible file-system hint must still point to the real data folder");
        assertTrue(script.contains("['Gameplay plugin', srv.plugin]"));
    }
    @Test void mainPanelCopyAndBrandAccentsKeepReadableDarkThemeContrast() throws Exception {
        String css=Files.readString(Path.of("src/main/resources/panel/panel.css"));
        for(String token:new String[]{BrandPalette.SKY,BrandPalette.CLOUD,BrandPalette.BREEZE,BrandPalette.MINT,
            BrandPalette.LAVENDER,BrandPalette.GOLD,BrandPalette.PEACH,BrandPalette.TWILIGHT,BrandPalette.MIDNIGHT,BrandPalette.STONE})
            assertTrue(css.contains(token),token);
        // Muted copy uses a contrast-adjusted derivative rather than Soft Stone on Twilight (only 4.2:1).
        for(String text:new String[]{BrandPalette.CLOUD,"#aab9cd",BrandPalette.SKY,BrandPalette.MINT,BrandPalette.LAVENDER,BrandPalette.GOLD})
            assertTrue(contrast(text,BrandPalette.TWILIGHT)>=4.5,text);
        assertTrue(css.contains("--red: #ef6461"),"Existing danger hue remains distinct from decorative branding");
        assertTrue(contrast("#17243B","#F4F9FF")>=4.5);
        assertTrue(contrast("#435773","#F4F9FF")>=4.5);
    }
    private static double contrast(String a,String b) {
        double first=luminance(a),second=luminance(b);return (Math.max(first,second)+.05)/(Math.min(first,second)+.05);
    }
    private static double luminance(String hex) {
        double result=0;double[] weight={.2126,.7152,.0722};
        for(int index=0;index<3;index++) {double value=Integer.parseInt(hex.substring(1+index*2,3+index*2),16)/255.0;
            result+=weight[index]*(value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4));}
        return result;
    }
}
