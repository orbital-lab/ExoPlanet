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

#ifndef __ExoSimTerrainMat_h_
#define __ExoSimTerrainMat_h_

#include "Terrain/OgreTerrainMaterialGeneratorA.h"

using namespace Ogre;

/** This is a custom terrain material generator extended from the default TerrainMaterialGeneratorA
to provide shader scripts to compute deph maps and to modify the visual rendering results (disable specular
reflections or use the ortho image for extra shading). We have to overwrite some methods to get access to 
the shader script creation methods, so some parts are added by copy and paste from the parent class. 
The main modifications are done in the nested shader script helper class. */
class ExoSimTerrainMat : public TerrainMaterialGeneratorA
{
public:
	/** The material generator constructor for initializations. */
	ExoSimTerrainMat();

	/** A custom shader profile to change the used CG shader script generator. */
	class ShaderProfile : public TerrainMaterialGeneratorA::SM2Profile
	{
	public:
		/** The shader profile constructor to setup some shading properties and features. */
		ShaderProfile(TerrainMaterialGenerator* parent, const String& name);

		/** Creates the (modified) CG helper object and causes to generate the material based on it.
		The addDepthTechnique() method is called to setup render properties and shader scripts for
		the created material descriptor to create depth maps.
		@param terrain the terrain instance for which a material should be created
		@returns a material descriptor for the given terrain instance*/
		MaterialPtr generate(const Terrain* terrain);

	protected:

		/** Define a new render technique to distinguish between normal visual rendering and 
		deph map rendering and causes to create the appropiate shader scripts.
		@param mat the material descriptor to add the new rendering properties
		@param terrain the terrain reference for the shader script creation */
		void addDepthTechnique(const MaterialPtr& mat, const Terrain* terrain);

		/** The overwritten helper class for the CG shader script creation. */
		class ShaderHelperCg : public TerrainMaterialGeneratorA::SM2Profile::ShaderHelperCg
		{
		public:
			/** Generate the vertex shader script
			@param prof the shader profile (not used)
			@param terrain the terrain instance to get access to the texture or height maps
			@returns a custom vertex shader script */
			HighLevelGpuProgramPtr generateDepthVertexProgram(const SM2Profile* prof, const Terrain* terrain);

			/** Generate the pixel shader script
			@param prof the shader profile (not used)
			@param terrain the terrain instance to get access to the texture or height maps
			@returns a custom vertex shader script */
			HighLevelGpuProgramPtr generateDepthFragmentProgram(const SM2Profile* prof, const Terrain* terrain);
			
			/** Handle the texture layer list for the pixel shader script generation. The terrain instance 
			is used to check which layer is the current one and how to render it. E.g. the ortho image of the
			surface is just for slightly modifying the brightness of the ground texture.
			@param prof the shader profile (not used)
			@param terrain the terrain instance
			@param tt the active render technique
			@param layer the current layer
			@param outStream the string stream to append script lines */
			void generateFpLayer(const SM2Profile* prof, const Terrain* terrain, TechniqueType tt, uint layer, 
				StringUtil::StrStreamType& outStream);
			/** Add a finishing part (footer) to the generated pixel shader script. The terrain instance is used to
			get the fog creation mode to render it accordingly.
			@param prof the shader profile (not used)
			@param terrain the terrain instance 
			@param tt the active render technique
			@param outStream the string stream to append script lines */
			void generateFpFooter(const SM2Profile* prof, const Terrain* terrain, TechniqueType tt, 
				StringUtil::StrStreamType& outStream);
		};
	};
};

#endif // #ifndef __ExoSimTerrainMat_h_
