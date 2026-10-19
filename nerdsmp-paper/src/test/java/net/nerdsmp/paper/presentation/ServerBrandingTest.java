package net.nerdsmp.paper.presentation;

import net.nerdsmp.paper.player.PlayerGuideService;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class ServerBrandingTest {
    private final PlainTextComponentSerializer plain=PlainTextComponentSerializer.plainText();
    @Test void oldConfigsReceiveConsistentTwoLinePublicBrandingWithoutDevelopmentCopy() {
        var brand=ServerBranding.read(new YamlConfiguration(),"Hearthsky",true);
        assertEquals("AERBORNE » A Skyblock RPG Adventure\nBuild your island. Explore the skies. Discover magic.",plain.serialize(brand.motd()));
        assertEquals("Aerborne\nSmall beginnings. Endless skies.",plain.serialize(Chat.render(brand.tabHeaderMarkup())));
        assertEquals("AERBORNE",plain.serialize(Chat.render(brand.sidebarTitleMarkup())));
        assertEquals("Welcome to Aerborne!",plain.serialize(brand.welcome()));
        assertEquals("Your adventure begins above the clouds.",plain.serialize(brand.firstSteps()));
    }
    @Test void configuredNamesAndDisplayCopyStayLiteralInsteadOfExecutingMarkup() {
        var yaml=new YamlConfiguration();yaml.set("branding.tagline","<click:run_command:/op x>Not a command</click>");
        yaml.set("branding.description","<red>Literal description</red>");
        var brand=ServerBranding.read(yaml,"<red>My Island</red>",true);
        assertTrue(plain.serialize(brand.motd()).contains("<red>My Island</red>"));
        assertTrue(plain.serialize(brand.motd()).contains("<red>Literal description</red>"));
        assertTrue(plain.serialize(Chat.render(brand.tabHeaderMarkup())).contains("<click:run_command:/op x>"));
    }
    @Test void blankOversizedOrControlTextFallsBackWithoutChangingLegacyMode() {
        var yaml=new YamlConfiguration();yaml.set("branding.mode-label"," ");yaml.set("branding.tagline","x".repeat(65));
        yaml.set("branding.description","bad\nextra line");
        var brand=ServerBranding.read(yaml,"Existing Name",false);
        assertEquals("Survival RPG",brand.mode());assertEquals("Build a world together.",brand.tagline());
        assertEquals("Build • Explore • Trade",brand.description());
        assertEquals(2,plain.serialize(brand.motd()).split("\n",-1).length);
    }
    @Test void existingStockTipsAreCorrectedWithoutReplacingOperatorMessages() {
        assertEquals("Every two island levels earns a talent point.",PlayerGuideService.publicTip("Every five island levels earns a talent point."));
        assertEquals("My community's own message",PlayerGuideService.publicTip("My community's own message"));
        assertEquals("Explore and fishing.",PlayerGuideService.publicTip("Explore and open-water fishing."));
        assertEquals(600,PlayerGuideService.guidanceTicks(-10,30));
        assertEquals(1_728_000,PlayerGuideService.guidanceTicks(Long.MAX_VALUE,60));
        assertEquals(12_000,PlayerGuideService.guidanceTicks(600,60));
    }
    @Test void publicCandidateIsAuthenticatedWhitelistedAndConservativelySized() throws Exception {
        var properties=new Properties();try(var reader=Files.newBufferedReader(Path.of("../deploy/release/server.properties"))) {properties.load(reader);}
        assertEquals("true",properties.getProperty("online-mode"));assertEquals("true",properties.getProperty("white-list"));
        assertEquals("30",properties.getProperty("max-players"));assertEquals("8",properties.getProperty("view-distance"));
        assertEquals("6",properties.getProperty("simulation-distance"));assertEquals("false",properties.getProperty("enable-rcon"));
        assertEquals("AERBORNE » A Skyblock RPG Adventure\nBuild your island. Explore the skies. Discover magic.",properties.getProperty("motd"));
        var yaml=new YamlConfiguration();yaml.load(Path.of("../deploy/release/config.yml").toFile());
        assertEquals("yaml",yaml.getString("storage.mode"));assertEquals("alert",yaml.getString("anti-cheat.mode"));
        assertFalse(yaml.getBoolean("anti-cheat.ml.autonomous"));assertFalse(yaml.getBoolean("anti-cheat.geolocation.auto-download"));
        String velocity=Files.readString(Path.of("../deploy/velocity/velocity.toml"));
        assertTrue(velocity.contains("show-max-players = 30"));
        String motd=velocity.lines().filter(line->line.startsWith("motd = ")).findFirst().orElseThrow().substring(7);
        assertEquals(plain.serialize(ServerBranding.read(new YamlConfiguration(),"Hearthsky",true).motd()),
            plain.serialize(Chat.render(motd.substring(1,motd.length()-1).replace("\\n","\n"))));
    }
    @Test void compactCandidateReceivesCampaignDefaultsWithoutMergingExplicitMilestones() throws Exception {
        var config=new YamlConfiguration();config.load(Path.of("../deploy/release/config.yml").toFile());
        var defaults=new YamlConfiguration();defaults.load(Path.of("src/main/resources/config.yml").toFile());config.setDefaults(defaults);
        String before=config.saveToString();
        var milestones=net.nerdsmp.paper.storage.ConfigurationDefaults.section(config,"campaign.milestones");
        assertEquals(3,milestones.getKeys(false).size());assertEquals(60_000,milestones.getLong("rift-opened"));
        assertEquals(before,config.saveToString());
        config.set("campaign.milestones.first-ripple",50L);
        var edited=net.nerdsmp.paper.storage.ConfigurationDefaults.section(config,"campaign.milestones");
        assertEquals(java.util.Set.of("first-ripple"),edited.getKeys(false));assertEquals(50,edited.getLong("first-ripple"));
    }
    @Test void exactLegacyStockVariantsAdaptWithoutSavingOrReplacingCustomCopy() {
        var yaml=new YamlConfiguration();
        yaml.set("branding.tagline","Your island. A wider world.");
        yaml.set("branding.description","Co-op islands • Quests • Player economy");
        String original=yaml.saveToString();
        for(String stock:new String[]{"Hearthsky","HearthSky","HEARTHSKY","Hearth Sky","hearth-sky","hearth_sky","  Hearthsky  "}) {
            var brand=ServerBranding.read(yaml,stock,true);
            assertEquals(BrandPalette.NAME,brand.name());assertEquals(BrandPalette.TAGLINE,brand.tagline());
            assertEquals(BrandPalette.DESCRIPTION,brand.description());
        }
        assertEquals(original,yaml.saveToString());
        yaml.set("branding.tagline","Our own adventure.");yaml.set("branding.description","A private world for friends.");
        var custom=ServerBranding.read(yaml,"Hearthsky Coast",true);
        assertEquals("Hearthsky Coast",custom.name());assertEquals("Our own adventure.",custom.tagline());
        assertEquals("A private world for friends.",custom.description());
        assertEquals("Hearthsky Coast",plain.serialize(Chat.render(custom.sidebarTitleMarkup())));
    }
    @Test void invalidNamesUseThePublicBrandAndCustomModeLabelsStayLiteral() {
        for(String invalid:new String[]{null," ","x".repeat(33),"Bad\nName"}) assertEquals("Aerborne",ServerBranding.publicName(invalid));
        var yaml=new YamlConfiguration();yaml.set("branding.mode-label","<red>Friends</red>");
        assertTrue(plain.serialize(ServerBranding.read(yaml,"Our World",true).motd()).contains("<red>Friends</red>"));
    }
}
