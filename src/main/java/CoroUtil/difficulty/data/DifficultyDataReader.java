package CoroUtil.difficulty.data;

import CoroUtil.difficulty.data.cmods.*;
import CoroUtil.difficulty.data.conditions.*;
import CoroUtil.forge.CoroUtil;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

/**
 * Created by Corosus on 2/1/2017.
 *
 */
public class DifficultyDataReader {

    //using Class.class because we dont need a return type
    private static final Gson GSONBuffInventory = (new GsonBuilder()).registerTypeAdapter(DifficultyData.class, new DeserializerAllJson()).create();
    //private static final Gson GSON_INSTANCE = (new GsonBuilder()).registerTypeAdapter(RandomValueRange.class, new RandomValueRange.Serializer()).registerTypeAdapter(LootPool.class, new LootPool.Serializer()).registerTypeAdapter(LootTable.class, new LootTable.Serializer()).registerTypeHierarchyAdapter(LootEntry.class, new LootEntry.Serializer()).registerTypeHierarchyAdapter(LootFunction.class, new LootFunctionManager.Serializer()).registerTypeHierarchyAdapter(LootCondition.class, new LootConditionManager.Serializer()).registerTypeHierarchyAdapter(LootContext.EntityTarget.class, new LootContext.EntityTarget.Serializer()).create();

    private static DifficultyData data;

    public static String lootTablesFolder = "loot_tables";

    public static HashMap<String, Class> lookupJsonNameToCmodDeserializer = new HashMap<>();
    public static HashMap<String, Class> lookupJsonNameToConditionDeserializer = new HashMap<>();

    public DifficultyDataReader() {
        data = new DifficultyData();

        lookupJsonNameToCmodDeserializer.clear();
        lookupJsonNameToCmodDeserializer.put("inventory", CmodInventory.class);
        lookupJsonNameToCmodDeserializer.put("mob_drops", CmodMobDrops.class);
        lookupJsonNameToCmodDeserializer.put("attribute_health", CmodAttributeHealth.class);
        lookupJsonNameToCmodDeserializer.put("attribute_speed", CmodAttributeSpeed.class);
        lookupJsonNameToCmodDeserializer.put("xp", CmodXP.class);
        lookupJsonNameToCmodDeserializer.put("ai_antiair", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_mining", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_counterattack", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_lunge", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_infernal", CmodAIInfernal.class);
        lookupJsonNameToCmodDeserializer.put("template", CmodTemplate.class);

        lookupJsonNameToConditionDeserializer.clear();
        lookupJsonNameToConditionDeserializer.put("context", ConditionContext.class);
        lookupJsonNameToConditionDeserializer.put("difficulty", ConditionDifficulty.class);
        lookupJsonNameToConditionDeserializer.put("invasion_number", ConditionInvasionNumber.class);
        lookupJsonNameToConditionDeserializer.put("random", ConditionRandom.class);
        lookupJsonNameToConditionDeserializer.put("filter_mobs", ConditionFilterMobs.class);
        lookupJsonNameToConditionDeserializer.put("template", ConditionTemplate.class);
    }

    public static DifficultyData getData() {
        return data;
    }

    public void loadFiles() {

        data.reset();

        CoroUtil.dbg("start reading difficulty files");



        /*File folderLoot = new File("I:\\newdev\\git\\CoroUtil_1.10.2\\src\\main\\resources\\assets\\coroutil\\config\\" + lootTablesFolder + "\\");

        String fileContents;

        if (folderLoot.exists()) {
            if (folderLoot.isFile()) {
                try {
                    fileContents = Files.toString(folderLoot, Charsets.UTF_8);
                    LootTable lootTable = net.minecraftforge.common.ForgeHooks.loadLootTable(GSON_INSTANCE, test, fileContents, true);
                    System.out.println(lootTable);
                } catch (Exception ex) {

                }
            }
        }*/



        //temp
        File dataFolder = new File("I:\\newdev\\git\\CoroUtil_1.10.2\\src\\main\\resources\\assets\\coroutil\\config\\");

        try {

            processFolder(dataFolder);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        CoroUtil.dbg("done");
    }

    public static void processFile(File file) {
        try {

            CoroUtil.dbg("processing: " + file.toString());
            String lastPath = file.getParent().toLowerCase();
            if (lastPath.endsWith(lootTablesFolder)) {
                String fileContents = Files.toString(file, Charsets.UTF_8);
                String fileName = file.getName().replace(".json", "");
                ResourceLocation resName = new ResourceLocation(CoroUtil.modID + ":loot_tables." + fileName);
                LootTable lootTable = net.minecraftforge.common.ForgeHooks.loadLootTable(LootTableManager.GSON_INSTANCE, resName, fileContents, true);
                data.lookupLootTables.put(fileName, lootTable);
            } else {
                GSONBuffInventory.fromJson(new BufferedReader(new FileReader(file)), DifficultyData.class);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void processFolder(File path) {
        for (File child : path.listFiles()) {
            if (child.isFile()) {
                try {
                    if (child.toString().endsWith(".json")) {
                        processFile(child);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                processFolder(child);
            }
        }
    }

}
