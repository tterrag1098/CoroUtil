package extendedrenderer.shader;

import extendedrenderer.particle.ShaderManager;

public class ShaderProgramFoliage extends ShaderProgram {

    private int vertexShaderAttributeIndexPosition = 0;
    private int vertexShaderAttributeTexCoord = 1;
    //private int vertexShaderAttributeVertexNormal = 2;
    private int vertexShaderAttributeAlpha = InstancedMeshParticle.vboSizeMesh;
    private int vertexShaderAttributeBrightness = InstancedMeshParticle.vboSizeMesh + 1;
    private int vertexShaderAttributeModelMatrix = InstancedMeshParticle.vboSizeMesh + 2;
    private int vertexShaderAttributeRGBATest = InstancedMeshParticle.vboSizeMesh + 6;

    public ShaderProgramFoliage(String name) throws Exception {
        super(name);
    }

    @Override
    public void setupAttribLocations() {
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeIndexPosition, "position");
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeTexCoord, "texCoord");
        //ShaderManager.glBindAttribLocation(programId, vertexShaderAttributeVertexNormal, "vertexNormal");
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeModelMatrix, "modelMatrix");
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeRGBATest, "rgba");
        //ShaderManager.glBindAttribLocation(programId, vertexShaderAttributeRGBA, "rgba");
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeAlpha, "alpha");
        ShaderManager.glBindAttribLocation(getProgramId(), vertexShaderAttributeBrightness, "brightness");
    }
}