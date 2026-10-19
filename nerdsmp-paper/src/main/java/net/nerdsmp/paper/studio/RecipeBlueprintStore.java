package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.Skill;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Strict save-before-publish recipes, separate from the read-only built-in recipe catalog. */
public final class RecipeBlueprintStore {
    public static final int MAX_RECIPES=256;
    @FunctionalInterface interface Writer {void write(Path path,String text)throws IOException;}
    private final Path file;
    private final Consumer<RecipeBlueprint> validator;
    private final Writer writer;
    private Map<String,RecipeBlueprint> recipes;
    public RecipeBlueprintStore(File file,Consumer<RecipeBlueprint> validator)throws IOException {this(file,validator,RecipeBlueprintStore::atomicWrite);}
    RecipeBlueprintStore(File file,Consumer<RecipeBlueprint> validator,Writer writer)throws IOException {
        this.file=file.toPath().toAbsolutePath().normalize();this.validator=Objects.requireNonNull(validator);this.writer=Objects.requireNonNull(writer); recipes=read();
    }
    public synchronized List<RecipeBlueprint> all(){return recipes.values().stream().sorted(java.util.Comparator.comparing(RecipeBlueprint::id)).toList();}
    public synchronized Optional<RecipeBlueprint> find(String id){return Optional.ofNullable(recipes.get(id));}
    public synchronized void validate(RecipeBlueprint candidate,RecipeBlueprint expected){
        if(!Objects.equals(recipes.get(candidate.id()),expected))throw new IllegalStateException("This recipe changed. Reopen its editor.");
        if(expected==null&&recipes.size()>=MAX_RECIPES)throw new IllegalArgumentException("The recipe engine supports 256 recipes.");
        validator.accept(candidate);
    }
    public synchronized void put(RecipeBlueprint candidate,RecipeBlueprint expected)throws IOException {
        validate(candidate,expected);var next=new TreeMap<>(recipes);next.put(candidate.id(),candidate);commit(next);
    }
    public synchronized void remove(String id,RecipeBlueprint expected)throws IOException {
        if(expected==null||!Objects.equals(recipes.get(id),expected))throw new IllegalStateException("This recipe changed. Reopen its editor.");
        var next=new TreeMap<>(recipes);next.remove(id);commit(next);
    }
    private void commit(Map<String,RecipeBlueprint> next)throws IOException {writer.write(file,serialize(next));recipes=Map.copyOf(next);}
    private Map<String,RecipeBlueprint> read()throws IOException {
        if(!Files.exists(file))return Map.of();
        if(!Files.isRegularFile(file)||Files.size(file)>2_097_152)throw new IOException("Recipe engine file is not regular or exceeds 2 MiB.");
        try {
            String text=Files.readString(file,StandardCharsets.UTF_8);LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setNestingDepthLimit(12);options.setCodePointLimit(2_097_152);new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(text);exact(yaml,Set.of("schema-version","recipes"));
            if(integer(yaml.get("schema-version"))!=1)throw new IllegalArgumentException("Unsupported recipe engine schema.");
            ConfigurationSection root=Objects.requireNonNull(yaml.getConfigurationSection("recipes"),"Missing recipes mapping");
            if(root.getKeys(false).size()>MAX_RECIPES)throw new IllegalArgumentException("The recipe engine supports 256 recipes.");
            TreeMap<String,RecipeBlueprint> result=new TreeMap<>();
            for(String id:root.getKeys(false)) {
                ConfigurationSection entry=Objects.requireNonNull(root.getConfigurationSection(id),"Invalid recipe mapping");exact(entry,Set.of("output","amount","skill","level","enabled","grid"));
                if(!(entry.get("enabled") instanceof Boolean enabled)||!(entry.get("grid") instanceof List<?> raw))throw new IllegalArgumentException("Invalid recipe flags or grid.");
                List<Material> grid=new ArrayList<>();for(Object cell:raw)grid.add(Material.valueOf(string(cell)));
                RecipeBlueprint blueprint=new RecipeBlueprint(id,string(entry.get("output")),integer(entry.get("amount")),Skill.valueOf(string(entry.get("skill"))),integer(entry.get("level")),enabled,grid);validator.accept(blueprint);result.put(id,blueprint);
            }
            return Map.copyOf(result);
        }catch(InvalidConfigurationException|IllegalArgumentException|NullPointerException|YAMLException invalid){throw new IOException("Invalid recipe engine file; content was preserved: "+invalid.getMessage(),invalid);}
    }
    private static void exact(ConfigurationSection section,Set<String> keys){if(!section.getKeys(false).equals(keys))throw new IllegalArgumentException("Unexpected or missing recipe fields.");}
    private static String string(Object value){if(!(value instanceof String text))throw new IllegalArgumentException("Recipe field must be text.");return text;}
    private static int integer(Object value){if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Recipe field must be a whole number.");long number=((Number)value).longValue();if(number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)throw new IllegalArgumentException("Recipe number is out of range.");return(int)number;}
    private static String serialize(Map<String,RecipeBlueprint> recipes){
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("schema-version",1);ConfigurationSection root=yaml.createSection("recipes");new TreeMap<>(recipes).forEach((id,r)->{
            var node=root.createSection(id);node.set("output",r.output());node.set("amount",r.amount());node.set("skill",r.skill().name());node.set("level",r.level());node.set("enabled",r.enabled());node.set("grid",r.grid().stream().map(Enum::name).toList());
        });return yaml.saveToString();
    }
    private static void atomicWrite(Path path,String text)throws IOException {
        Files.createDirectories(path.getParent());Path temporary=Files.createTempFile(path.getParent(),"engine-recipe-",".tmp");
        try {try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer bytes=ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary);}
    }
}
