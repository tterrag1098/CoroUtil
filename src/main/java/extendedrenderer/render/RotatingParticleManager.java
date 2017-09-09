package extendedrenderer.render;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;

import CoroUtil.config.ConfigCoroAI;
import CoroUtil.util.CoroUtilBlockLightCache;
import CoroUtil.util.CoroUtilMath;
import CoroUtil.util.CoroUtilParticle;
import extendedrenderer.EventHandler;
import extendedrenderer.ExtendedRenderer;
import extendedrenderer.particle.ParticleMeshBufferManager;
import extendedrenderer.particle.ParticleRegistry;
import extendedrenderer.particle.ShaderManager;
import extendedrenderer.particle.entity.EntityRotFX;
import extendedrenderer.particle.entity.ParticleTexExtraRender;
import extendedrenderer.shadertest.Renderer;
import extendedrenderer.shadertest.ShaderProgram;
import extendedrenderer.shadertest.gametest.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEmitter;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ICrashReportDetail;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector4f;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;

@SideOnly(Side.CLIENT)
public class RotatingParticleManager
{
    private static final ResourceLocation PARTICLE_TEXTURES = new ResourceLocation("textures/particle/particles.png");
    /** Reference to the World object. */
    protected World worldObj;
    /**
     * Second dimension: 0 = GlStateManager.depthMask false aka transparent textures, 1 = true
     */
    public final HashMap<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> fxLayers = new HashMap<>();
    private final Queue<ParticleEmitter> particleEmitters = Queues.<ParticleEmitter>newArrayDeque();
    private final TextureManager renderer;
    private final Map<Integer, IParticleFactory> particleTypes = Maps.<Integer, IParticleFactory>newHashMap();
    private final Queue<Particle> queueEntityFX = Queues.<Particle>newArrayDeque();
    
    //ExtendedRenderer Additions
    
    private final FloatBuffer fogColorBuffer = GLAllocation.createDirectFloatBuffer(16);

    //a hack to enable fog for particles when weather2 sandstorm is active
    public static float sandstormFogAmount = 0F;

    public static int debugParticleRenderCount;

    public static int lastAmountToRender;

    public static boolean useShaders;

    public RotatingParticleManager(World worldIn, TextureManager rendererIn)
    {
        this.worldObj = worldIn;
        this.renderer = rendererIn;



        /*shaderTest = new Renderer();
        try {
            shaderTest.init();
        } catch (Exception ex) {
            ex.printStackTrace();
        }*/


        //this.registerVanillaParticles();
    }

    public void initNewArrayData(TextureAtlasSprite sprite) {
        List<ArrayDeque<Particle>[][]> list = new ArrayList<>();

        //main default layer
        list.add(0, new ArrayDeque[4][]);

        //layer for tornado funnel
        list.add(1, new ArrayDeque[4][]);

        //close up stuff like precipitation
        list.add(2, new ArrayDeque[4][]);

        for (ArrayDeque<Particle>[][] entry : list) {
            for (int i = 0; i < 4; ++i)
            {
                entry[i] = new ArrayDeque[2];

                for (int j = 0; j < 2; ++j)
                {
                    entry[i][j] = Queues.newArrayDeque();
                }
            }
        }

        fxLayers.put(sprite, list);
    }

    public void registerParticle(int id, IParticleFactory particleFactory)
    {
        this.particleTypes.put(Integer.valueOf(id), particleFactory);
    }

    public void emitParticleAtEntity(Entity entityIn, EnumParticleTypes particleTypes)
    {
        this.particleEmitters.add(new ParticleEmitter(this.worldObj, entityIn, particleTypes));
    }

    /**
     * Spawns the relevant particle according to the particle id.
     */
    @Nullable
    public Particle spawnEffectParticle(int particleId, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters)
    {
        IParticleFactory iparticlefactory = (IParticleFactory)this.particleTypes.get(Integer.valueOf(particleId));

        if (iparticlefactory != null)
        {
            Particle particle = iparticlefactory.getEntityFX(particleId, this.worldObj, xCoord, yCoord, zCoord, xSpeed, ySpeed, zSpeed, parameters);

            if (particle != null)
            {
                this.addEffect(particle);
                return particle;
            }
        }

        return null;
    }

    public void addEffect(Particle effect)
    {
        if (effect == null) return; //Forge: Prevent modders from being bad and adding nulls causing untraceable NPEs.
        this.queueEntityFX.add(effect);
    }

    public void updateEffects()
    {
        for (int i = 0; i < 4; ++i)
        {
            this.updateEffectLayer(i);
        }

        if (!this.particleEmitters.isEmpty())
        {
            List<ParticleEmitter> list = Lists.<ParticleEmitter>newArrayList();

            for (ParticleEmitter particleemitter : this.particleEmitters)
            {
                particleemitter.onUpdate();

                if (!particleemitter.isAlive())
                {
                    list.add(particleemitter);
                }
            }

            this.particleEmitters.removeAll(list);
        }

        if (!this.queueEntityFX.isEmpty())
        {
            for (Particle particle = (Particle)this.queueEntityFX.poll(); particle != null; particle = (Particle)this.queueEntityFX.poll())
            {
                int j = particle.getFXLayer();
                int k = particle.isTransparent() ? 0 : 1;

                int renderOrder = 0;
                if (particle instanceof EntityRotFX) {
                    renderOrder = ((EntityRotFX) particle).renderOrder;
                }

                if (!fxLayers.containsKey(particle.particleTexture)) {
                    initNewArrayData(particle.particleTexture);
                }

                ArrayDeque<Particle>[][] entry = fxLayers.get(particle.particleTexture).get(renderOrder);

                if (entry[j][k].size() >= 16384) {
                    entry[j][k].removeFirst();
                }

                entry[j][k].add(particle);

                //for (ArrayDeque<Particle>[][] entry : fxLayers) {

                //}
            }
        }
    }

    private void updateEffectLayer(int layer)
    {
        //this.worldObj.theProfiler.startSection(layer + "");

        for (int i = 0; i < 2; ++i)
        {
            //this.worldObj.theProfiler.startSection(i + "");
            for (Map.Entry<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> entry1 : fxLayers.entrySet()) {
                for (ArrayDeque<Particle>[][] entry2 : entry1.getValue()) {
                    this.tickParticleList(entry2[layer][i]);
                }
            }

            //this.worldObj.theProfiler.endSection();
        }

        //this.worldObj.theProfiler.endSection();
    }

    private void tickParticleList(Queue<Particle> p_187240_1_)
    {
        if (!p_187240_1_.isEmpty())
        {
            Iterator<Particle> iterator = p_187240_1_.iterator();

            while (iterator.hasNext())
            {
                Particle particle = iterator.next();
                this.tickParticle(particle);

                if (!particle.isAlive())
                {
                    iterator.remove();
                }
            }
        }
    }

    private void tickParticle(final Particle particle)
    {
        try
        {
            particle.onUpdate();
        }
        catch (Throwable throwable)
        {
            CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Ticking Rotating Particle");
            CrashReportCategory crashreportcategory = crashreport.makeCategory("Particle being ticked");
            final int i = particle.getFXLayer();
            crashreportcategory.setDetail("Rotating Particle", new ICrashReportDetail<String>()
            {
                public String call() throws Exception
                {
                    return particle.toString();
                }
            });
            crashreportcategory.setDetail("Particle Type", new ICrashReportDetail<String>()
            {
                public String call() throws Exception
                {
                    return i == 0 ? "MISC_TEXTURE" : (i == 1 ? "TERRAIN_TEXTURE" : (i == 3 ? "ENTITY_PARTICLE_TEXTURE" : "Unknown - " + i));
                }
            });
            throw new ReportedException(crashreport);
        }
    }

    /**
     * Renders all current particles. Args player, partialTickTime
     */
    public void renderParticles(Entity entityIn, float partialTicks)
    {


        //if (true) return;


        float f = ActiveRenderInfo.getRotationX();
        float f1 = ActiveRenderInfo.getRotationZ();
        float f2 = ActiveRenderInfo.getRotationYZ();
        float f3 = ActiveRenderInfo.getRotationXY();
        float f4 = ActiveRenderInfo.getRotationXZ();
        Particle.interpPosX = entityIn.lastTickPosX + (entityIn.posX - entityIn.lastTickPosX) * (double)partialTicks;
        Particle.interpPosY = entityIn.lastTickPosY + (entityIn.posY - entityIn.lastTickPosY) * (double)partialTicks;
        Particle.interpPosZ = entityIn.lastTickPosZ + (entityIn.posZ - entityIn.lastTickPosZ) * (double)partialTicks;
        //Particle.field_190016_K = entityIn.getLook(partialTicks);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        //GlStateManager.blendFunc(GlStateManager.SourceFactor.DST_ALPHA, GlStateManager.DestFactor.ONE_MINUS_DST_ALPHA);
        GlStateManager.alphaFunc(516, 0.003921569F);
        //GlStateManager.alphaFunc(GL11.GL_LESS, 0.2F);
        //GlStateManager.alphaFunc(GL11.GL_ALWAYS, 0.0F);
        


        int mip_min = 0;
        int mip_mag = 0;

        //fix mipmapping making low alpha transparency particles dissapear based on distance, window size, particle size
        if (!ConfigCoroAI.disableMipmapFix) {
            mip_min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            mip_mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }


        
        Minecraft mc = Minecraft.getMinecraft();
        EntityRenderer er = mc.entityRenderer;
        
        //TODO: requires AT for EntityRenderer
        boolean testGLUOverride = false;
        if (testGLUOverride) {
	        /*GlStateManager.matrixMode(5889);
	        GlStateManager.loadIdentity();
	        Project.gluPerspective(er.getFOVModifier(partialTicks, true), (float)mc.displayWidth / (float)mc.displayHeight, 0.05F, er.farPlaneDistance * 4.0F);
	        GlStateManager.matrixMode(5888);*/
        }
        
        boolean fog = true;
        if (fog) {
        	boolean ATmode = true;
        	
        	//TODO: make match other fog states
        	
        	if (ATmode) {
        		//TODO: add AT if this will be used

                er.setupFog(0, partialTicks);

                float fogScaleInvert = 1F - sandstormFogAmount;

                //customized
                //GlStateManager.setFogDensity(0F);
                GlStateManager.setFogStart(0F);
                GlStateManager.setFogEnd(Math.max(40F, 1000F * fogScaleInvert));
                //GlStateManager.setFogEnd(30F);
                /**/
        	} else {
        		//incomplete copy
	        	float fogColorRed = ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, "field_175080_Q");
	        	float fogColorGreen = ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, "field_175082_R");
	        	float fogColorBlue = ObfuscationReflectionHelper.getPrivateValue(EntityRenderer.class, Minecraft.getMinecraft().entityRenderer, "field_175081_S");
	        	GlStateManager.glFog(2918, this.setFogColorBuffer(fogColorRed, fogColorGreen, fogColorBlue, 1.0F));
	            GlStateManager.glNormal3f(0.0F, -1.0F, 0.0F);
	            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	            
	            Entity entity = mc.getRenderViewEntity();
	            IBlockState iblockstate = ActiveRenderInfo.getBlockStateAtEntityViewpoint(mc.theWorld, entity, partialTicks);
	            /*float hook = net.minecraftforge.client.ForgeHooksClient.getFogDensity(er, entity, iblockstate, partialTicks, 0.1F);
	            if (hook >= 0) GlStateManager.setFogDensity(hook);*/
	            
	            GlStateManager.setFogDensity(1F);
	            
	            GlStateManager.enableColorMaterial();
	            GlStateManager.enableFog();
	            GlStateManager.colorMaterial(1028, 4608);
        	}
            
            /*GlStateManager.setFogStart(0);
            GlStateManager.setFogEnd(100);*/
        }

        //ArrayDeque<Particle>[][] entry = fxLayers.get(1);

        debugParticleRenderCount = 0;

        //GlStateManager.depthMask(false);

        //testing no blending (so far notice no fps change)
        /*GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);*/



        //screen door transparency
        //GL11.glEnable(GL11.GL_POLYGON_STIPPLE);

        if (Main.gameEngine == null) {
            Main.initUnthreaded();


            ParticleMeshBufferManager.setupMeshForParticle(ParticleRegistry.cloud256_test);
            ParticleMeshBufferManager.setupMeshForParticle(ParticleRegistry.rain_white_trans);
            //ParticleMeshBufferManager.setupMeshForParticle(ParticleRegistry.cloud256);
            /*ParticleMeshBufferManager.setupMeshForParticle(ParticleRegistry.rain_white);

            ParticleMeshBufferManager.setupMeshForParticle(ParticleRegistry.leaf);*/

            //EventHandler.shaderTest = new extendedrenderer.shadertest.Renderer();
            try {
                //EventHandler.shaderTest.init();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        GlStateManager.disableCull();
        //Main.gameLogic.renderer.render(null, Main.gameLogic.camera, Main.gameLogic.gameItems);

        //if (true) return;

        Transformation transformation = null;
        Matrix4fe viewMatrix = null;

        useShaders = ShaderManager.canUseShadersInstancedRendering();

        if (worldObj.getTotalWorldTime() % 20 < 10) {
            //useShaders = false;
        }

        //useShaders = false;

        //useShaders = !useShaders;

        //



        int glCalls = 0;
        int trueRenderCount = 0;
        int bufferSize = 0;
        int particles = 0;

        if (useShaders) {
            ShaderProgram shaderProgram = Main.gameLogic.renderer.shaderProgram;
            transformation = Main.gameLogic.renderer.transformation;
            shaderProgram.bind();
            Matrix4fe mat = new Matrix4fe();
            FloatBuffer buf = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, buf);
            buf.rewind();
            Matrix4fe.get(mat, 0, buf);

            //modify far distance, 4x as far
            boolean distantRendering = true;
            if (distantRendering) {
                float zNear = 0.05F;
                float zFar = (float) (mc.gameSettings.renderDistanceChunks * 16) * 4F;
                mat.m22 = ((zFar + zNear) / (zNear - zFar));
                mat.m32 = ((zFar + zFar) * zNear / (zNear - zFar));
            }

            shaderProgram.setUniform("projectionMatrix", mat);

            boolean alternateCameraCapture = true;
            if (alternateCameraCapture) {
                viewMatrix = new Matrix4fe();
                FloatBuffer buf2 = BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buf2);
                buf2.rewind();
                Matrix4fe.get(viewMatrix, 0, buf2);
            }

            shaderProgram.setUniform("texture_sampler", 0);

            CoroUtilBlockLightCache.brightnessPlayer = CoroUtilBlockLightCache.getBrightnessNonLightmap(worldObj, (float)entityIn.posX, (float)entityIn.posY, (float)entityIn.posZ);
        }

        //do sprite/mesh list
        for (Map.Entry<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> entry1 : fxLayers.entrySet()) {

            InstancedMesh mesh = ParticleMeshBufferManager.getMesh(entry1.getKey());

            //if (entry1.getKey() != ParticleRegistry.test_texture && entry1.getKey() != ParticleRegistry.rain_white_trans) continue;

            //TODO: register if missing, maybe relocate this
            if (mesh == null) {
                ParticleMeshBufferManager.setupMeshForParticle(entry1.getKey());
                mesh = ParticleMeshBufferManager.getMesh(entry1.getKey());
            }

            if (mesh != null) {
                //do cloud layer, then funnel layer
                for (ArrayDeque<Particle>[][] entry : entry1.getValue()) {
                    //do each texture mode, 0 and 1 are the only ones used now
                    for (int i_nf = 0; i_nf < 3; ++i_nf) {
                        final int i = i_nf;

                        //do non depth mask (for transparent ones), then depth mask
                        for (int j = 0; j < 2; ++j) {
                            if (!entry[i][j].isEmpty()) {
                                switch (j) {

                                    /**
                                     * TODO: make sure alpha test toggling doesnt interfere with anything else
                                     * with it on, it speeds up rendering of non transparent particles, does it also allow for full transparent particle pixels?
                                     */

                                    case 0:
                                        GlStateManager.depthMask(false);
                                        //GL11.glDisable(GL11.GL_DEPTH_TEST);
                                        //GL11.glEnable(GL11.GL_DEPTH_TEST);
                                        /*GL11.glEnable(GL11.GL_ALPHA_TEST);
                                        GL11.glEnable(GL11.GL_BLEND);*/
                                        break;
                                    case 1:
                                        GlStateManager.depthMask(true);
                                        //GL11.glEnable(GL11.GL_DEPTH_TEST);
                                        /*GL11.glDisable(GL11.GL_ALPHA_TEST);
                                        GL11.glDisable(GL11.GL_BLEND);*/
                                }

                                switch (i) {
                                    case 0:
                                    default:
                                        this.renderer.bindTexture(PARTICLE_TEXTURES);
                                        break;
                                    case 1:
                                        this.renderer.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                                }

                                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                                if (useShaders) {

                                    mesh.initRender();

                                    mesh.instanceDataBuffer.clear();
                                    mesh.curBufferPos = 0;
                                    particles = entry[i][j].size();

                                    if (true) {
                                        for (final Particle particle : entry[i][j]) {
                                            if (particle instanceof EntityRotFX) {
                                                EntityRotFX part = (EntityRotFX) particle;

                                                part.updateQuaternion(entityIn);

                                                //CoroUtilMath.rotation(part.rotation, (float)Math.toRadians(-part.rotationPitch), (float)Math.toRadians(-part.rotationYaw), 0);
                                                part.renderParticleForShader(mesh, transformation, viewMatrix, entityIn, partialTicks, f, f4, f1, f2, f3);

                                            }
                                        }
                                    }

                                    mesh.instanceDataBuffer.limit(mesh.curBufferPos * mesh.INSTANCE_SIZE_FLOATS);

                                    OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, mesh.instanceDataVBO);
                                    ShaderManager.glBufferData(GL_ARRAY_BUFFER, mesh.instanceDataBuffer, GL_DYNAMIC_DRAW);

                                    ShaderManager.glDrawElementsInstanced(GL_TRIANGLES, mesh.getVertexCount(), GL_UNSIGNED_INT, 0, mesh.curBufferPos);

                                    glCalls++;
                                    trueRenderCount += mesh.curBufferPos;
                                    //bufferSize = mesh.instanceDataBuffer.capacity();

                                    OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, 0);

                                    mesh.endRender();
                                } else {
                                    Tessellator tessellator = Tessellator.getInstance();
                                    VertexBuffer vertexbuffer = tessellator.getBuffer();
                                    vertexbuffer.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);

                                    for (final Particle particle : entry[i][j]) {
                                        //try {

                                        if (particle instanceof EntityRotFX) {
                                            EntityRotFX part = (EntityRotFX) particle;
                                            //part.rotationPitch = 0;
                                            //part.rotationYaw = 45;
                                            //part.rotationPitch = 90;
                                            //part.rotationYaw = 0;
                                        }
                                        particle.renderParticle(vertexbuffer, entityIn, partialTicks, f, f4, f1, f2, f3);
                                        debugParticleRenderCount++;
                                /*} catch (Throwable throwable) {
                                    CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Rendering Particle");
                                    CrashReportCategory crashreportcategory = crashreport.makeCategory("Particle being rendered");
                                    crashreportcategory.setDetail("Particle", new ICrashReportDetail<String>() {
                                        public String call() throws Exception {
                                            return particle.toString();
                                        }
                                    });
                                    crashreportcategory.setDetail("Particle Type", new ICrashReportDetail<String>() {
                                        public String call() throws Exception {
                                            return i == 0 ? "MISC_TEXTURE" : (i == 1 ? "TERRAIN_TEXTURE" : (i == 3 ? "ENTITY_PARTICLE_TEXTURE" : "Unknown - " + i));
                                        }
                                    });
                                    throw new ReportedException(crashreport);
                                }*/
                                    }

                                    tessellator.draw();
                                }




                            }
                        }
                    }
                }
            } else {
                //didnt register all atlas sprites, ok for now
                //System.out.println("MESH NULL, SHOULDNT HAPPEN!");
            }


        }

        if (useShaders) {
            Main.gameLogic.renderer.shaderProgram.unbind();
        }


        if (worldObj.getTotalWorldTime() % 60 == 0) {
            System.out.println("particles: " + particles);
            System.out.println("debugParticleRenderCount: " + debugParticleRenderCount);
            System.out.println("trueRenderCount: " + trueRenderCount);
            System.out.println("glCalls: " + glCalls);
        }
        
        if (fog) {
        	GlStateManager.disableFog();
        }
        
        //restore original mipmap state
        if (!ConfigCoroAI.disableMipmapFix) {
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mip_min);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mip_mag);
        }
        
        GlStateManager.enableCull();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(516, 0.1F);
    }

    public void renderLitParticles(Entity entityIn, float partialTick)
    {
        float f = 0.017453292F;
        float f1 = MathHelper.cos(entityIn.rotationYaw * 0.017453292F);
        float f2 = MathHelper.sin(entityIn.rotationYaw * 0.017453292F);
        float f3 = -f2 * MathHelper.sin(entityIn.rotationPitch * 0.017453292F);
        float f4 = f1 * MathHelper.sin(entityIn.rotationPitch * 0.017453292F);
        float f5 = MathHelper.cos(entityIn.rotationPitch * 0.017453292F);

        for (Map.Entry<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> entry1 : fxLayers.entrySet()) {
            for (ArrayDeque<Particle>[][] entry : entry1.getValue()) {
                for (int i = 0; i < 2; ++i) {
                    Queue<Particle> queue = entry[3][i];

                    if (!queue.isEmpty()) {
                        Tessellator tessellator = Tessellator.getInstance();
                        VertexBuffer vertexbuffer = tessellator.getBuffer();

                        for (Particle particle : queue) {
                            particle.renderParticle(vertexbuffer, entityIn, partialTick, f1, f5, f2, f3, f4);
                        }
                    }
                }
            }
        }
    }

    public void clearEffects(@Nullable World worldIn)
    {
        this.worldObj = worldIn;

        for (Map.Entry<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> entry1 : fxLayers.entrySet()) {
            for (ArrayDeque<Particle>[][] entry : entry1.getValue()) {
                for (int i = 0; i < 4; ++i) {
                    for (int j = 0; j < 2; ++j) {
                        entry[i][j].clear();
                    }
                }
            }
        }

        this.particleEmitters.clear();
    }

    public void clear() {
        //shader way
        for (Map.Entry<TextureAtlasSprite, List<ArrayDeque<Particle>[][]>> entry1 : ExtendedRenderer.rotEffRenderer.fxLayers.entrySet()) {
            for (ArrayDeque<Particle>[][] entry : entry1.getValue()) {
                for (int i = 0; i < entry.length; i++) {
                    for (int j = 0; j < entry[i].length; j++) {
                        if (entry[i][j] != null) {
                            entry[i][j].clear();
                        }
                    }

                }
            }
        }

        //pre shader way
        /*for (ArrayDeque<Particle>[][] entry : ExtendedRenderer.rotEffRenderer.fxLayers) {
            for (int i = 0; i < entry.length; i++) {
                for (int j = 0; j < entry[i].length; j++) {
                    if (entry[i][j] != null) {
                        entry[i][j].clear();
                    }
                }

            }
        }*/
    }
    
    public String getStatistics()
    {
    	int count = 0;
    	/*for (int i = 0; i < layers; i++) {
    		count += fxLayers[i].size();
    	}*/
    	//item sheet seems only one used now
        return "" + count;
    }
    
    private FloatBuffer setFogColorBuffer(float red, float green, float blue, float alpha)
    {
        this.fogColorBuffer.clear();
        this.fogColorBuffer.put(red).put(green).put(blue).put(alpha);
        this.fogColorBuffer.flip();
        return this.fogColorBuffer;
    }
}