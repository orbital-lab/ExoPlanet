/* . . . . . . . . . . . . . . . . . . . . . . . . . . . .
 * (c) Stefan Kral 2011 (http://www.redfibre.net/orbital)
 *                     _   _ _       _
 *             ___ ___| |_|_| |_ ___| |
 * _______    | . |  _| . | |  _| .'| |     _____________
 *       /____|___|_| |___|_|_| |__,|_|____/
 *
 * This program is free software and you are welcome to
 * modify and/or redistribute it under the terms of the
 * GNU General Public License http://www.gnu.org/licenses.
 * . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

#include "ExoSimTerrainMat.h"

#include "OgreTerrain.h"
#include "OgreTechnique.h"
#include "OgreHighLevelGpuProgramManager.h"

using namespace Ogre;

//-------------------------------------------------------------------------------------
ExoSimTerrainMat::ExoSimTerrainMat() : TerrainMaterialGeneratorA()
{
		mProfiles.push_back(OGRE_NEW ExoSimTerrainMat::ShaderProfile(this, "ShaderProfile"));
		setActiveProfile("ShaderProfile");
}
//-------------------------------------------------------------------------------------
ExoSimTerrainMat::ShaderProfile::ShaderProfile(TerrainMaterialGenerator* parent, const String& name)
	: SM2Profile(parent, name, "ShaderProfile based on Shader Model 2")
{
	mLayerParallaxMappingEnabled = false;
	mLayerSpecularMappingEnabled = false;
	mGlobalColourMapEnabled = false;
	mLightmapEnabled = false;
	mCompositeMapEnabled = false;
	mReceiveDynamicShadows = false;
}
//-------------------------------------------------------------------------------------
MaterialPtr ExoSimTerrainMat::ShaderProfile::generate(const Terrain* terrain)
{
	mShaderGen = OGRE_NEW ShaderHelperCg();

	MaterialPtr mat = SM2Profile::generate(terrain);
	addDepthTechnique(mat, terrain);

	return mat;
}
//-------------------------------------------------------------------------------------
void ExoSimTerrainMat::ShaderProfile::addDepthTechnique(const MaterialPtr& mat, const Terrain* terrain)
{
	Technique* tech = mat->createTechnique();
	tech->setSchemeName("Depth");

	// Only supporting one pass
	Pass* pass = tech->createPass();
	
	ShaderHelperCg* shaderGen = static_cast<ShaderHelperCg*>(mShaderGen);
	HighLevelGpuProgramPtr vprog = shaderGen->generateDepthVertexProgram(this, terrain);
	HighLevelGpuProgramPtr fprog = shaderGen->generateDepthFragmentProgram(this, terrain);
	pass->setVertexProgram(vprog->getName());
	pass->setFragmentProgram(fprog->getName());

	// global normal map
	TextureUnitState* tu = pass->createTextureUnitState();
	tu->setTextureName(terrain->getTerrainNormalMap()->getName());
	tu->setTextureAddressingMode(TextureUnitState::TAM_CLAMP);

	// blend maps
	for (uint i = 0; i < terrain->getBlendTextureCount(); ++i)
	{
		tu = pass->createTextureUnitState(terrain->getBlendTextureName(i));
		tu->setTextureAddressingMode(TextureUnitState::TAM_CLAMP);
	}
}
//-------------------------------------------------------------------------------------
HighLevelGpuProgramPtr ExoSimTerrainMat::ShaderProfile::ShaderHelperCg::generateDepthVertexProgram(
	const SM2Profile* prof, const Terrain* terrain)
{
	HighLevelGpuProgramManager& mgr = HighLevelGpuProgramManager::getSingleton();

	String progName = terrain->getMaterialName() + "/sm2/vp/depth";
	HighLevelGpuProgramPtr prog = mgr.createProgram(
		progName, ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, "cg", GPT_VERTEX_PROGRAM);

	prog->setParameter("profiles", "vs_3_0 vs_2_0 arbvp1");
	prog->setParameter("entry_point", "main_vp");

	StringUtil::StrStreamType sourceStr;
	sourceStr <<
		"void main_vp(\n"
		"float4 pos : POSITION,\n"
		"float2 uv  : TEXCOORD0,\n"
		"uniform float4x4 worldMatrix,\n"
		"uniform float4x4 viewProjMatrix,\n"
		"out float4 oPos : POSITION,\n"
		"out float4 oLoc : TEXCOORD0,\n"
		"out float2 oUV : TEXCOORD1\n"
		")\n"
		"{\n"
		"	float4 worldPos = mul(worldMatrix, pos);\n"
		"	oPos = mul(viewProjMatrix, worldPos);\n"
		"	oLoc = float4(worldPos.x, worldPos.y, worldPos.z, oPos.z);\n"
		"	oUV = uv;\n"
		"}\n";

	prog->setSource(sourceStr.str());
	prog->load();

	GpuProgramParametersSharedPtr params = prog->getDefaultParameters();
	params->setNamedAutoConstant("worldMatrix", GpuProgramParameters::ACT_WORLD_MATRIX);
	params->setNamedAutoConstant("viewProjMatrix", GpuProgramParameters::ACT_VIEWPROJ_MATRIX);

	//LogManager::getSingleton().stream(LML_NORMAL) 
	//	<< "*** Terrain Vertex Program: " << prog->getName() << " ***\n" << prog->getSource() << "\n***   ***";

	return prog;
}
//-------------------------------------------------------------------------------------
HighLevelGpuProgramPtr ExoSimTerrainMat::ShaderProfile::ShaderHelperCg::generateDepthFragmentProgram(
	const SM2Profile* prof, const Terrain* terrain)
{
	HighLevelGpuProgramManager& mgr = HighLevelGpuProgramManager::getSingleton();

	String progName = terrain->getMaterialName() + "/sm2/fp/depth";
	HighLevelGpuProgramPtr prog = mgr.createProgram(
		progName, ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, "cg", GPT_FRAGMENT_PROGRAM);

	prog->setParameter("profiles", "ps_3_0 ps_2_0 fp30");
	prog->setParameter("entry_point", "main_fp");

	StringUtil::StrStreamType sourceStr;
	sourceStr << 
		"float4 main_fp(\n"
		"float4 loc : TEXCOORD0,\n"
		"float2 uv : TEXCOORD1,\n"
		"uniform sampler2D globalNormal : register(s0),\n"
		"uniform sampler2D blendTex : register(s1)\n"
		") : COLOR\n"
		"{\n"
		"   float4 normal = tex2D(globalNormal, uv) * 2 - 1;\n"
		"   int slp = acos(normal.g) / 3.14 * 180;\n"
		"	float4 blendTexVal = tex2D(blendTex, uv);\n"
		"	int zval = clamp(loc.a, 1, 255);\n"
		"	int grd = blendTexVal.r * 255;\n"
		"	int misc = slp * pow(2, 18) + grd * pow(2, 8) + zval;\n"
		"	return float4(loc.xyz, misc);\n"
		"}";

	prog->setSource(sourceStr.str());
	prog->load();

	//LogManager::getSingleton().stream(LML_NORMAL) 
	//	<< "*** Terrain Fragment Program: " << prog->getName() << " ***\n" << prog->getSource() << "\n***   ***";

	return prog;
}
//---------------------------------------------------------------------
void ExoSimTerrainMat::ShaderProfile::ShaderHelperCg::generateFpLayer(
	const SM2Profile* prof, const Terrain* terrain, TechniqueType tt, uint layer, StringUtil::StrStreamType& outStream)
{
	uint uvIdx = layer / 2;
	String uvChannels = layer % 2 ? ".zw" : ".xy";
	uint blendIdx = (layer-1) / 4;
	String blendChannel = getChannel(layer-1);
	String blendWeightStr = String("blendTexVal") + StringConverter::toString(blendIdx) + "." + blendChannel;

	// generate UV
	outStream << "	float2 uv" << layer << " = layerUV" << uvIdx << uvChannels << ";\n";

	// calculate lighting here for normal mapping (only based on first layer)
	if (!layer)
	{
		outStream << "	TSnormal = expand(tex2D(normtex0, uv0)).rgb;\n";
		outStream << "	TShalfAngle = normalize(TSlightDir + TSeyeDir);\n";
		outStream << "	litRes = lit(dot(TSlightDir, TSnormal), dot(TShalfAngle, TSnormal), scaleBiasSpecular.z);\n";
	}

	// sample diffuse texture
	outStream << "	float4 diffuseSpecTex" << layer << " = tex2D(difftex" << layer << ", uv" << layer << ");\n";

	// apply to common
	if (!layer)
		outStream << "	diffuse = diffuseSpecTex0.rgb;\n";
	else if (layer == terrain->getLayerCount()-1)
		outStream << "	diffuse = diffuse *  saturate(diffuseSpecTex" << layer << ".r * 0.7 + 0.5);\n"; //<< ".r + 0.25);\n";
	else
		outStream << "	diffuse = lerp(diffuse, diffuseSpecTex" << layer << ".rgb, " << blendWeightStr << ");\n";
}
//---------------------------------------------------------------------
void ExoSimTerrainMat::ShaderProfile::ShaderHelperCg::generateFpFooter(
	const SM2Profile* prof, const Terrain* terrain, TechniqueType tt, StringUtil::StrStreamType& outStream)
{
	// diffuse lighting
	//outStream << "	outputCol.rgb += ambient/2 * diffuse + litRes.y * lightDiffuseColour * diffuse * shadow;\n";
	//outStream << "	outputCol.rgb += ambient * diffuse + 0.25 * litRes.y * lightDiffuseColour * diffuse * shadow;\n";
	outStream << "	outputCol.rgb += ambient * 1.2 * diffuse + litRes.y * lightDiffuseColour * diffuse * shadow;\n";
			
	// fog
	if (terrain->getSceneManager()->getFogMode() != FOG_NONE)
		outStream << "	outputCol.rgb = lerp(outputCol.rgb, fogColour, fogVal);\n";

	// final return
	outStream << "	return outputCol;\n" << "}\n";
}
