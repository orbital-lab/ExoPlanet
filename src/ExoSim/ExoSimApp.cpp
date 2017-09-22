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

#include "ExoSimApp.h"
#include "ExoSimTerrainMat.h"

#include "BatchPage.h"
#include "TreeLoader2D.h"

ExoSimApp* terrainApplication;
float delegateGetTerrainHeight(float x, float z, void *userData) {
	return terrainApplication->getTerrainHeight(x, z);
}
float delegateGetTerrainHeight(float x, float z) {
	return terrainApplication->getTerrainHeight(x, z);
}

//-------------------------------------------------------------------------------------
ExoSimApp::ExoSimApp(bool isPlugin) : ExoSimBase(isPlugin),
	mTerrainGlobals(0),
    mTerrainGroup(0),
	mRocks(0),
	mSoil(0)
{
	terrainApplication = this;
}
//-------------------------------------------------------------------------------------
ExoSimApp::~ExoSimApp()
{
	if (mRocks && mRocks->getPageLoader()) delete mRocks->getPageLoader();
	if (mSoil && mSoil->getPageLoader()) delete mSoil->getPageLoader();
	if (mRocks) delete mRocks;
	if (mSoil) delete mSoil;

    delete mTerrainGroup;
    delete mTerrainGlobals;
}
//-------------------------------------------------------------------------------------
void ExoSimApp::defineTerrain(Ogre::String dtmName, long x, long y, int dtmImgSize)
{
	mTerrainGroup->setFilenameConvention(dtmName, Ogre::String("page"));
	Ogre::String dtmPageFile = mTerrainGroup->generateFilename(x, y);
	if (Ogre::ResourceGroupManager::getSingleton().resourceExists(mTerrainGroup->getResourceGroup(), dtmPageFile))
	{
		mTerrainGroup->defineTerrain(x, y);
	}
	else
	{
		Ogre::Image dtmImg;

		Ogre::StringUtil::StrStreamType dtmFilePrefix;
		dtmFilePrefix << dtmName + "_" << std::setw(8) << std::setfill('0') << std::hex 
			<< mTerrainGroup->packIndex(x, y);

		Ogre::DataStreamPtr imgStream = Ogre::ResourceGroupManager::getSingleton().openResource(
			dtmFilePrefix.str() + ".f32", Ogre::ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME);

		dtmImg.loadRawData(imgStream, dtmImgSize, dtmImgSize, Ogre::PF_FLOAT32_R);

		Ogre::Terrain::LayerInstanceList layerList;
		Ogre::Terrain::LayerInstance layer;

		layer = Ogre::Terrain::LayerInstance();
		layer.textureNames.push_back("mars_diffusespecular.dds");
		layer.textureNames.push_back("mars_normalheight.dds");
		layer.worldSize = 8;
		layerList.push_back(layer);

		layer = Ogre::Terrain::LayerInstance();
		layer.textureNames.push_back("mars-dark_diffusespecular.dds");
		layer.textureNames.push_back("mars_normalheight.dds");
		layer.worldSize = 8;
		layerList.push_back(layer);

		layer = Ogre::Terrain::LayerInstance();
		layer.textureNames.push_back(dtmFilePrefix.str() + "_ortho.png");
		layer.worldSize = mTerrainGroup->getTerrainWorldSize();
		layerList.push_back(layer);

		mTerrainGroup->defineTerrain(x, y, &dtmImg, &layerList);

		mTerrainGroup->loadTerrain(x, y, true);
		Ogre::Terrain* terrain = mTerrainGroup->getTerrain(x, y);

		// blending
		Ogre::Image blendImg;
		blendImg.load(dtmFilePrefix.str() + "_mask.png", mTerrainGroup->getResourceGroup());

		Ogre::TerrainLayerBlendMap* blendMap0 = terrain->getLayerBlendMap(1);
		float* pBlend0 = blendMap0->getBlendPointer();

		for (Ogre::uint16 i = 0; i < terrain->getLayerBlendMapSize(); ++i) {
			for (Ogre::uint16 j = 0; j < terrain->getLayerBlendMapSize(); ++j) {
				Ogre::ColourValue colour = blendImg.getColourAt(j, i, 0);
				*pBlend0++ = colour.r;
			}
		}
		blendMap0->dirty();
		blendMap0->update();

		terrain->save(dtmPageFile);
	}
}
//-------------------------------------------------------------------------------------
void ExoSimApp::configureTerrainDefaults()
{
	mTerrainGlobals = new Ogre::TerrainGlobalOptions();

    // Configure global
    mTerrainGlobals->setMaxPixelError(5);
	
	// Custom Material Generator
	mTerrainGlobals->setDefaultMaterialGenerator(
		Ogre::TerrainMaterialGeneratorPtr(new ExoSimTerrainMat()));
 
    // Configure default import settings for if we use imported image
    Ogre::Terrain::ImportData& defaultimp = mTerrainGroup->getDefaultImportSettings();
	defaultimp.inputBias = 1954.4;
    defaultimp.minBatchSize = 33; //17
    defaultimp.maxBatchSize = 65;
}
//-------------------------------------------------------------------------------------
void ExoSimApp::createScene()
{
	Ogre::String dtmName = "mars-map-part";
	float terrainSize = 1034.8; // in m
	int dtmSize = 1025;         // in pixel
	int pages = 1;

	Ogre::Vector2 roverPos(0, 0);
	float cameraHeight = 1.4;
	float cameraFOV = 45;

	mMapDim.left = -terrainSize/2;  // originX
	mMapDim.top = -(terrainSize/2 + (pages-1)*terrainSize); // originZ
	mMapDim.bottom = -mMapDim.left;
	mMapDim.right = -mMapDim.top;

	Ogre::Vector3 lightdir(0.5, -0.3, 0.35);
	lightdir.normalise();
	
	Ogre::Light* light = mSceneMgr->createLight("TerrainLight");
	light->setType(Ogre::Light::LT_DIRECTIONAL);
	light->setDirection(lightdir);
	light->setDiffuseColour(Ogre::ColourValue(0.75, 0.5, 0.25));
	light->setSpecularColour(Ogre::ColourValue(0.75, 0.5, 0.25));

    mSceneMgr->setAmbientLight(Ogre::ColourValue(0.4, 0.4, 0.4));
 
	mTerrainGroup = new Ogre::TerrainGroup(mSceneMgr, Ogre::Terrain::ALIGN_X_Z, dtmSize, terrainSize);
    mTerrainGroup->setOrigin(Ogre::Vector3::ZERO);
 
    configureTerrainDefaults();
 
    for (long x = 0; x < pages; ++x)
        for (long y = 0; y < pages; ++y)
            defineTerrain(dtmName, x, y, dtmSize);
 
    // sync load since we want everything in place when we start
    mTerrainGroup->loadAllTerrains(true);
    mTerrainGroup->freeTemporaryResources();

	mControl->setHeightFunction(&delegateGetTerrainHeight);
	mControl->setPosition(roverPos);

	Ogre::Entity *roverEntity = mSceneMgr->createEntity("Rover", "rover.mesh");
	Ogre::SceneNode* roverNode = mPosition->createChildSceneNode("RoverNode");
	roverNode->attachObject(roverEntity);
	roverNode->setPosition(Ogre::Vector3(0, 0, 0));

	roverNode->attachObject(mRoverCam);
	mRoverCam->setPosition(Ogre::Vector3(0, cameraHeight, -0.78));
	mRoverCam->setFOVy(Ogre::Degree(cameraFOV));

	Ogre::ColourValue fadeColour(0.9, 0.75, 0.5);
	mSceneMgr->setFog(Ogre::FOG_LINEAR, fadeColour, 0.0, 1000, 1600);
    mWindow->getViewport(0)->setBackgroundColour(fadeColour);

	// paged geometry
	mRocks = new Forests::PagedGeometry();
	mRocks->setCamera(mRoverCam);
	mRocks->setPageSize(80);	//Set the size of each page of geometry
	mRocks->setInfinite();		//Use infinite paging mode
	mRocks->addDetailLevel<Forests::BatchPage>(600); //Use batches up to 600 units away

	Forests::TreeLoader2D *rockLoader = new Forests::TreeLoader2D(mRocks, mMapDim);
	mRocks->setPageLoader(rockLoader);
	rockLoader->setHeightFunction(&delegateGetTerrainHeight);
	rockLoader->setMaximumScale(0.7f);
	rockLoader->setMinimumScale(0.3f);

	mSoil = new Forests::PagedGeometry();
	mSoil->setCamera(mRoverCam);
	mSoil->setPageSize(20);
	mSoil->setInfinite();
	mSoil->addDetailLevel<Forests::BatchPage>(100);

	Forests::TreeLoader2D *soilLoader = new Forests::TreeLoader2D(mSoil, mMapDim);
	mSoil->setPageLoader(soilLoader);
	soilLoader->setHeightFunction(&delegateGetTerrainHeight);
	soilLoader->setMaximumScale(0.12f);
	soilLoader->setMinimumScale(0.05f);

    Ogre::Entity *rrbEntity = mSceneMgr->createEntity("rock-big.mesh");
	Ogre::Entity *rdbEntity = mSceneMgr->createEntity("rock-big.mesh");
	rrbEntity->setMaterial(Ogre::MaterialManager::getSingleton().getByName("rock-red-big"));
	rdbEntity->setMaterial(Ogre::MaterialManager::getSingleton().getByName("rock-dark-big"));

	Ogre::Entity *rrsEntity = mSceneMgr->createEntity("rock-small.mesh");
	Ogre::Entity *rdsEntity = mSceneMgr->createEntity("rock-small.mesh");
	rrsEntity->setMaterial(Ogre::MaterialManager::getSingleton().getByName("rock-red-small"));
	rdsEntity->setMaterial(Ogre::MaterialManager::getSingleton().getByName("rock-dark-small"));
	
	Ogre::Vector3 position = Ogre::Vector3();
	Ogre::Radian yaw;
	Ogre::Real scale;

	for (int i = 0; i < pages * terrainSize * 4 /*5*/; ++i)
	{
		position.x = Ogre::Math::RangeRandom(mMapDim.left, mMapDim.right);
		position.z = Ogre::Math::RangeRandom(mMapDim.top, mMapDim.bottom);

		yaw = Ogre::Degree(Ogre::Math::RangeRandom(0, 360));
		scale = Ogre::Math::RangeRandom(0.3f, 0.7f);

		if (i%2 == 0)
			rockLoader->addTree(rrbEntity, position, yaw, scale);
		else
			rockLoader->addTree(rdbEntity, position, yaw, scale);
	}

	position = Ogre::Vector3::ZERO;
	for (int i = 0; i < pages * terrainSize * 200 /*380*/; ++i)
	{
		if (Ogre::Math::RangeRandom(0, 1) <= 0.9f) { // clump together occasionally
			position.x += Ogre::Math::RangeRandom(-5.0f, 5.0f);
			position.z += Ogre::Math::RangeRandom(-5.0f, 5.0f);
			if (position.x < mMapDim.left || position.x > mMapDim.right)
				position.x = Ogre::Math::RangeRandom(mMapDim.left, mMapDim.right);
			if (position.z < mMapDim.top || position.z > mMapDim.bottom)
				position.z = Ogre::Math::RangeRandom(mMapDim.top, mMapDim.bottom);
		} else {
			position.x = Ogre::Math::RangeRandom(mMapDim.left, mMapDim.right);
			position.z = Ogre::Math::RangeRandom(mMapDim.top, mMapDim.bottom);
		}

		yaw = Ogre::Degree(Ogre::Math::RangeRandom(0, 360));
		scale = Ogre::Math::RangeRandom(0.05f, 0.12f);

		if (i%3 == 0)
			soilLoader->addTree(rdsEntity, position, yaw, scale);
		else
			soilLoader->addTree(rrsEntity, position, yaw, scale);
	}

	mSceneMgr->destroyEntity("rock-big.mesh");
	mSceneMgr->destroyEntity("rock-small.mesh");
}
//-------------------------------------------------------------------------------------
bool ExoSimApp::frameRenderingQueued(const Ogre::FrameEvent& evt)
{
	mRocks->update();
	mSoil->update();

    return ExoSimBase::frameRenderingQueued(evt);
}
