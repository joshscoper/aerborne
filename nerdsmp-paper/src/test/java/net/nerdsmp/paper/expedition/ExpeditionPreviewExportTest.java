package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Opt-in developer geometry export from the real generator, never a live-world read/write. */
@EnabledIfEnvironmentVariable(named="NERDSMP_BUILD_PREVIEW",matches=".+")
class ExpeditionPreviewExportTest {
    @Test void exportCityAndGrove() throws Exception {
        Path directory=Path.of(System.getenv("NERDSMP_BUILD_PREVIEW")); Files.createDirectories(directory);
        for(Zone zone:List.of(Zone.CITY,Zone.GROVE)) export(directory,zone);
    }
    private void export(Path directory,Zone zone) throws Exception {
        int radius=zone==Zone.CITY?110:96,width=radius*2+1,height=240;
        int cx=ExpeditionPlanV2.centerX(zone),cz=ExpeditionPlanV2.centerZ(zone);
        int[] volume=new int[width*width*height]; List<BuildCanvas.BlockSpec> palette=new ArrayList<>();
        Map<BuildCanvas.BlockSpec,Integer> ids=new HashMap<>(); palette.add(new BuildCanvas.BlockSpec(Material.AIR,""));
        for(int chunkX=(cx-radius)>>4;chunkX<=(cx+radius)>>4;chunkX++) for(int chunkZ=(cz-radius)>>4;chunkZ<=(cz+radius)>>4;chunkZ++)
            ExpeditionGeneratorV2.emitChunk(chunkX,chunkZ,(x,y,z,material,properties)-> {
                int a=x-cx+radius,c=z-cz+radius;
                if(a<0 || c<0 || a>=width || c>=width || y<0 || y>=height) return;
                int id=0;
                if(material!=Material.AIR) { var spec=new BuildCanvas.BlockSpec(material,properties); id=ids.computeIfAbsent(spec,key->{palette.add(key);return palette.size()-1;}); }
                volume[(a*width+c)*height+y]=id;
            });
        try(var out=Files.newBufferedWriter(directory.resolve(zone.id+".tsv"))) {
            for(int i=1;i<palette.size();i++) out.write("P\t"+i+"\t"+palette.get(i).material()+"\t"+palette.get(i).properties()+"\n");
            for(int x=0;x<width;x++) for(int z=0;z<width;z++) for(int y=0;y<height;y++) {
                int at=(x*width+z)*height+y,id=volume[at]; if(id==0) continue;
                int mask=0;
                if(x==width-1 || volume[at+width*height]==0) mask|=1;
                if(z==width-1 || volume[at+height]==0) mask|=2;
                if(y==height-1 || volume[at+1]==0) mask|=4;
                if(x==0 || volume[at-width*height]==0) mask|=8;
                if(z==0 || volume[at-height]==0) mask|=16;
                if(y==0 || volume[at-1]==0) mask|=32;
                // Partial blocks expose their shaped geometry even when neighboring cells are occupied.
                String material=palette.get(id).material().name();
                if(material.endsWith("STAIRS") || material.endsWith("SLAB") || material.endsWith("WALL") || material.endsWith("FENCE")) mask=63;
                if(mask!=0) out.write((x-radius)+"\t"+y+"\t"+(z-radius)+"\t"+id+"\t"+mask+"\n");
            }
        }
    }
}
