package net.nerdsmp.paper.death;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SanctuaryStore {
    private final File file;
    private Map<String,Sanctuary> entries=new LinkedHashMap<>();
    public SanctuaryStore(File file) {
        this.file=file;if(!file.exists())return;
        try {
            var yaml=new YamlConfiguration();yaml.load(file);
            if(yaml.getInt("version")!=1)throw new IllegalArgumentException("Unknown sanctuary schema");
            var root=SpiritRepository.required(yaml,"sanctuaries");
            for(String id:root.getKeys(false)) {var s=SpiritRepository.required(root,id);entries.put(id,new Sanctuary(id,s.getString("name"),SpiritRepository.point(SpiritRepository.required(s,"location"))));}
            if(entries.size()>128)throw new IllegalArgumentException("Too many sanctuaries");
        } catch(Exception failure) {throw new IllegalStateException("Cannot load spirit sanctuaries; refusing to replace "+file,failure);}
    }
    public List<Sanctuary> all(){return List.copyOf(entries.values());}
    public void put(Sanctuary sanctuary) {var changed=new LinkedHashMap<>(entries);changed.put(sanctuary.id(),sanctuary);if(changed.size()>128)throw new IllegalArgumentException("At most 128 sanctuaries are supported");save(changed);}
    public boolean remove(String id){var changed=new LinkedHashMap<>(entries);if(changed.remove(id)==null)return false;save(changed);return true;}
    private void save(Map<String,Sanctuary> changed) {
        var yaml=new YamlConfiguration();yaml.set("version",1);var root=yaml.createSection("sanctuaries");
        changed.values().forEach(s->{var row=root.createSection(s.id());row.set("name",s.name());SpiritRepository.write(row.createSection("location"),s.location());});
        try{AtomicYamlFiles.save(yaml,file);}catch(java.io.IOException failure){throw new IllegalStateException("Cannot save spirit sanctuaries",failure);}
        entries=changed;
    }
    public record Sanctuary(String id,String name,SpiritState.Point location) {
        public Sanctuary {if(id==null||!id.matches("[a-z0-9][a-z0-9_-]{0,31}")||name==null||name.isBlank()||name.length()>80||name.codePoints().anyMatch(Character::isISOControl)||location==null)throw new IllegalArgumentException("Invalid sanctuary");}
    }
}
