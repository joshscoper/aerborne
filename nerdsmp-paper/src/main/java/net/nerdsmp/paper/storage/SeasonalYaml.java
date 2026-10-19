package net.nerdsmp.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

/** Keeps legacy state as a read-only snapshot and imports it into exactly one season. */
public final class SeasonalYaml {
    private SeasonalYaml() { }
    public static YamlConfiguration load(File file,String season,String legacyKey) {
        if(!season.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid season");
        YamlConfiguration yaml=new YamlConfiguration();
        try { if(file.exists()) yaml.load(file); }
        catch(Exception failure) { throw new IllegalStateException("Cannot read seasonal state: "+file,failure); }
        if(yaml.contains("schema-version") && yaml.getInt("schema-version")!=2)
            throw new IllegalStateException("Unsupported seasonal schema: "+file);
        if(!yaml.contains("schema-version")) {
            if(yaml.contains("seasons")) throw new IllegalStateException("Unversioned seasonal state: "+file);
            yaml.set("schema-version",2); yaml.set("legacy-season",season);
            Object legacy=yaml.get(legacyKey);
            if(legacy instanceof org.bukkit.configuration.ConfigurationSection section) copy(section,yaml,"seasons."+season+"."+legacyKey);
            else if(legacy!=null) yaml.set("seasons."+season+"."+legacyKey,legacy);
        }
        return yaml;
    }
    private static void copy(org.bukkit.configuration.ConfigurationSection source,YamlConfiguration target,String path) {
        source.getValues(false).forEach((key,value)-> {
            if(value instanceof org.bukkit.configuration.ConfigurationSection section) copy(section,target,path+"."+key);
            else target.set(path+"."+key,value);
        });
    }
}
