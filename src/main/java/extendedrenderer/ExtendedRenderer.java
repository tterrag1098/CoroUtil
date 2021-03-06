package extendedrenderer;

import java.io.File;

import extendedrenderer.render.FoliageRenderer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import extendedrenderer.render.RotatingParticleManager;

@Mod(modid = "extendedrenderer", name="Extended Renderer", version="v1.0")
public class ExtendedRenderer {
	
	@Mod.Instance( value = "extendedrenderer" )
	public static ExtendedRenderer instance;
	public static String modid = "extendedrenderer";
    
    @SidedProxy(clientSide = "extendedrenderer.ClientProxy", serverSide = "extendedrenderer.CommonProxy")
    public static CommonProxy proxy;

    /*@SideOnly(Side.CLIENT)
    public static RotatingEffectRenderer rotEffRenderer;*/
    
    @SideOnly(Side.CLIENT)
    public static RotatingParticleManager rotEffRenderer;

    @SideOnly(Side.CLIENT)
    public static FoliageRenderer foliageRenderer;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
		MinecraftForge.EVENT_BUS.register(new extendedrenderer.EventHandler());
    }
    
    @Mod.EventHandler
    public void load(FMLInitializationEvent event)
    {
    	proxy.init();

    }
    
    @Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
    	proxy.postInit();
	}

    public ExtendedRenderer() {
    	
    }
	
	public static void dbg(Object obj) {
		if (true) System.out.println(obj);
	}
}
