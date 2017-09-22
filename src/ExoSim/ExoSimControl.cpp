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

#include "ExoSimControl.h"

bool orbview = false;

//-------------------------------------------------------------------------------------
ExoSimControl::ExoSimControl(Ogre::Root* root, Ogre::RenderWindow* window, bool isPlugin) :
		mPlugin(isPlugin),
		mRoot(root),
		mWindow(window),
		mPosition(root->getSceneManager("TerrainScene")->getSceneNode("TerrainPosition")),
		mRoverCam(root->getSceneManager("TerrainScene")->getCamera("RoverCam")),
		mShutDown(false)
{
	Ogre::SceneManager* sceneMgr = root->getSceneManager("TerrainScene");

	mWorldCam = sceneMgr->createCamera("WorldCam");
	mWorldCam->setNearClipDistance(0.1);
	mWorldCam->setFarClipDistance(mRoverCam->getFarClipDistance());
	mWorldCam->setAspectRatio(mRoverCam->getAspectRatio());
	mWorldCam->setFOVy(Ogre::Degree(60));

	mPosition->attachObject(mWorldCam);

	mDepthCam = sceneMgr->createCamera("DepthCam");
	mDepthCam->setFarClipDistance(mRoverCam->getFarClipDistance());
	mDepthCam->setNearClipDistance(mRoverCam->getNearClipDistance());
	mDepthCam->setAspectRatio(1);
	mDepthCam->setFOVy(Ogre::Degree(90));

	mCameraMan = new ExoSimCamMgr(mWorldCam, mPosition);
	if (mPlugin)
	{
		if (orbview)
		{
			mCameraMan->setStyle(CS_ORBIT);
		}
		else
		{
			mCameraMan->setStyle(CS_FIXED);
			mWorldCam->setPosition(Ogre::Vector3(0, 50, 0));
			mWorldCam->setOrientation(Ogre::Quaternion::IDENTITY);
			mWorldCam->rotate(Ogre::Quaternion(Ogre::Degree(-90), mWorldCam->getRight()));
		}
	}

	mRoverTexture = Ogre::TextureManager::getSingleton().createManual(
			"RoverCamTexture", Ogre::ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, Ogre::TEX_TYPE_2D, 
			mWindow->getWidth(), mWindow->getHeight(), 0, Ogre::PF_R8G8B8, Ogre::TU_RENDERTARGET); 
    Ogre::RenderTexture *roverRenderTarget = mRoverTexture->getBuffer()->getRenderTarget();
	roverRenderTarget->addViewport(mRoverCam);
	roverRenderTarget->getViewport(0)->setOverlaysEnabled(false);
	roverRenderTarget->setAutoUpdated(false);
	
	if (orbview && isPlugin)
	{
		mWorldTexture = Ogre::TextureManager::getSingleton().createManual(
				"WorldCamTexture", Ogre::ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, Ogre::TEX_TYPE_2D, 
				640, 480, 0, Ogre::PF_R8G8B8, Ogre::TU_RENDERTARGET);
		mWorldCam->setAspectRatio(4.0/3.0);
	} 
	else
	{
		mWorldTexture = Ogre::TextureManager::getSingleton().createManual(
				"WorldCamTexture", Ogre::ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, Ogre::TEX_TYPE_2D, 
				mWindow->getWidth(), mWindow->getHeight(), 0, Ogre::PF_R8G8B8, Ogre::TU_RENDERTARGET);
	}
    Ogre::RenderTexture *worldRenderTarget = mWorldTexture->getBuffer()->getRenderTarget();
    worldRenderTarget->addViewport(mWorldCam);
    worldRenderTarget->getViewport(0)->setOverlaysEnabled(false);
	worldRenderTarget->setAutoUpdated(false);

	mDepthTexture = Ogre::TextureManager::getSingleton().createManual(
			"DepthTex", Ogre::ResourceGroupManager::DEFAULT_RESOURCE_GROUP_NAME, Ogre::TEX_TYPE_2D, 
			1024, 1024, 0, Ogre::PF_FLOAT32_RGBA, Ogre::TU_RENDERTARGET); 
    Ogre::RenderTexture *depthRenderTarget = mDepthTexture->getBuffer()->getRenderTarget();
    depthRenderTarget->addViewport(mDepthCam);
	depthRenderTarget->getViewport(0)->setMaterialScheme("Depth");
    depthRenderTarget->getViewport(0)->setOverlaysEnabled(false);
	depthRenderTarget->setAutoUpdated(false);

	// Initializing OIS
	OIS::ParamList pl;

	size_t windowHnd = 0;
	mWindow->getCustomAttribute("WINDOW", &windowHnd);
	std::ostringstream windowHndStr;
	windowHndStr << windowHnd;
	pl.insert(std::make_pair("WINDOW", windowHndStr.str()));

	if (mPlugin)
	{
		pl.insert(std::make_pair("w32_mouse", "DISCL_FOREGROUND"));
		pl.insert(std::make_pair("w32_mouse", "DISCL_NONEXCLUSIVE"));
	}

	mInputManager = OIS::InputManager::createInputSystem(pl);

	mKeyboard = static_cast<OIS::Keyboard*>(mInputManager->createInputObject(OIS::OISKeyboard, true));
	mMouse = static_cast<OIS::Mouse*>(mInputManager->createInputObject(OIS::OISMouse, true));

	mMouse->setEventCallback(this);
	mKeyboard->setEventCallback(this);

	mTrayMgr = new OgreBites::SdkTrayManager("TerrainInterface", mWindow, mMouse, this);
	mTrayMgr->showFrameStats(OgreBites::TL_BOTTOMLEFT);
	mTrayMgr->toggleAdvancedFrameStats();
	mTrayMgr->showLogo(OgreBites::TL_BOTTOMRIGHT);
	mTrayMgr->hideCursor();

	// create a params panel for displaying sample details
	Ogre::StringVector items;
	items.push_back("cam.pX");
	items.push_back("cam.pY");
	items.push_back("cam.pZ");
	items.push_back("");
	items.push_back("cam.oW");
	items.push_back("cam.oX");
	items.push_back("cam.oY");
	items.push_back("cam.oZ");

	mDetailsPanel = mTrayMgr->createParamsPanel(OgreBites::TL_NONE, "DetailsPanel", 200, items);
	mDetailsPanel->hide();
}
//-------------------------------------------------------------------------------------
ExoSimControl::~ExoSimControl()
{
    if (mTrayMgr) delete mTrayMgr;
	if (mCameraMan) delete mCameraMan;
}
//-------------------------------------------------------------------------------------
void ExoSimControl::destroyControl()
{
    if(mInputManager)
    {
        mInputManager->destroyInputObject(mMouse);
        mInputManager->destroyInputObject(mKeyboard);

        OIS::InputManager::destroyInputSystem(mInputManager);
		mInputManager = 0;
    }
}
//-------------------------------------------------------------------------------------
void ExoSimControl::sceneReady()
{
	Ogre::ColourValue bgColor = mWindow->getViewport(0)->getBackgroundColour();
	mRoverTexture->getBuffer()->getRenderTarget()->getViewport(0)->setBackgroundColour(bgColor);
	mWorldTexture->getBuffer()->getRenderTarget()->getViewport(0)->setBackgroundColour(bgColor);
}
//-------------------------------------------------------------------------------------
void ExoSimControl::adjustMouseClipping(const unsigned int width, const unsigned int height)
{
    const OIS::MouseState &ms = mMouse->getMouseState();
    ms.width = width;
    ms.height = height;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::keyPressed( const OIS::KeyEvent &arg )
{
    if (arg.key == OIS::KC_F)   // toggle visibility of advanced frame stats
    {
        mTrayMgr->toggleAdvancedFrameStats();
    }
    else if (arg.key == OIS::KC_G)   // toggle visibility of even rarer debugging details
    {
        if (mDetailsPanel->getTrayLocation() == OgreBites::TL_NONE)
        {
            mTrayMgr->moveWidgetToTray(mDetailsPanel, OgreBites::TL_TOPRIGHT, 0);
            mDetailsPanel->show();
        }
        else
        {
            mTrayMgr->removeWidgetFromTray(mDetailsPanel);
            mDetailsPanel->hide();
        }
    }
    else if (arg.key == OIS::KC_T)   // change polygon rendering mode
    {
		Ogre::MaterialManager* matMgr = Ogre::MaterialManager::getSingletonPtr();
		bool isAnisotropic = matMgr->getDefaultTextureFiltering(Ogre::FT_MIN) == Ogre::TFO_ANISOTROPIC;
		matMgr->setDefaultTextureFiltering(isAnisotropic ? Ogre::TFO_NONE : Ogre::TFO_ANISOTROPIC);
    }
    else if (arg.key == OIS::KC_R)   // change polygon rendering mode
    {
		if (mCameraMan->getStyle() == CS_FREE)
		{
			bool isRenderModeSolid = mWorldCam->getPolygonMode() == Ogre::PM_SOLID;
			mWorldCam->setPolygonMode(isRenderModeSolid ? Ogre::PM_WIREFRAME : Ogre::PM_SOLID);
		}
    }
    else if (arg.key == OIS::KC_SYSRQ)   // take a screenshot
    {
		mWindow->getViewport(0)->setOverlaysEnabled(false);
		mWindow->update(true);
        mWindow->writeContentsToTimestampedFile("img", ".png");
		mWindow->getViewport(0)->setOverlaysEnabled(true);
    }
    else if (arg.key == OIS::KC_ESCAPE)
    {
        mShutDown = true;
    }
	else if (arg.key == OIS::KC_C) // switch camera mode fixed/orbit/free
    {
		switch (mCameraMan->getStyle())
        {
        case CS_FIXED:
			mWindow->getViewport(0)->setCamera(mWorldCam);
			mCameraMan->setStyle(CS_ORBIT);
			break;
		case CS_ORBIT:
			mCameraMan->setStyle(CS_FREE);
			break;
		default:
			mWindow->getViewport(0)->setCamera(mRoverCam);
			mCameraMan->setStyle(CS_FIXED);
		}
    }

	mCameraMan->injectKeyDown(arg);
    return true;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::keyReleased(const OIS::KeyEvent &evt)
{
    mCameraMan->injectKeyUp(evt);
    return true;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::mouseMoved(const OIS::MouseEvent &evt)
{
	if (mCameraMan->getStyle() == CS_FIXED)
	{
		mRoverCam->yaw(Ogre::Degree(-0.15 * evt.state.X.rel));
		mRoverCam->pitch(Ogre::Degree(-0.15 * evt.state.Y.rel));
	}

	mCameraMan->injectMouseMove(evt);
    return true;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::mousePressed(const OIS::MouseEvent &evt, OIS::MouseButtonID id)
{
	mCameraMan->injectMouseDown(evt, id);
    return true;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::mouseReleased(const OIS::MouseEvent &evt, OIS::MouseButtonID id)
{
	mCameraMan->injectMouseUp(evt, id);
    return true;
}
//-------------------------------------------------------------------------------------
bool ExoSimControl::frameRenderingQueued(const Ogre::FrameEvent &evt)
{
	if (mShutDown)
        return false;

	Ogre::Vector3 pos = mPosition->getPosition();

	// capture/update each device
    mKeyboard->capture();
    mMouse->capture();
	
    mCameraMan->frameRenderingQueued(evt);

	if (pos != mPosition->getPosition())
	{
		pos = mPosition->getPosition();
		mPosition->setPosition(pos.x, height(pos.x, pos.z), pos.z);
	}

	mTrayMgr->frameRenderingQueued(evt);

    if (mDetailsPanel->isVisible())   // if details panel is visible, then update its contents
    {
        mDetailsPanel->setParamValue(0, Ogre::StringConverter::toString(mRoverCam->getDerivedPosition().x));
        mDetailsPanel->setParamValue(1, Ogre::StringConverter::toString(mRoverCam->getDerivedPosition().y));
        mDetailsPanel->setParamValue(2, Ogre::StringConverter::toString(mRoverCam->getDerivedPosition().z));
        mDetailsPanel->setParamValue(4, Ogre::StringConverter::toString(mRoverCam->getDerivedOrientation().w));
        mDetailsPanel->setParamValue(5, Ogre::StringConverter::toString(mRoverCam->getDerivedOrientation().x));
        mDetailsPanel->setParamValue(6, Ogre::StringConverter::toString(mRoverCam->getDerivedOrientation().y));
        mDetailsPanel->setParamValue(7, Ogre::StringConverter::toString(mRoverCam->getDerivedOrientation().z));
    }

	return true;
}
//-------------------------------------------------------------------------------------
void ExoSimControl::setPosition(Ogre::Vector2 pos)
{
	mPosition->setPosition(pos.x, height(pos.x, pos.y), pos.y);
}
//-------------------------------------------------------------------------------------
Ogre::Vector2 ExoSimControl::getPosition() 
{
	return Ogre::Vector2(mPosition->getPosition().x, mPosition->getPosition().z);
}
//-------------------------------------------------------------------------------------
void ExoSimControl::setRotation(Ogre::Real rot)
{
	mPosition->setOrientation(Ogre::Quaternion(Ogre::Radian(rot), Ogre::Vector3::UNIT_Y));
}
//-------------------------------------------------------------------------------------
void ExoSimControl::setRoverRotation(Ogre::Real rot)
{
	Ogre::SceneNode* roverNode = mRoot->getSceneManager("TerrainScene")->getSceneNode("RoverNode");
	roverNode->setOrientation(Ogre::Quaternion(Ogre::Radian(rot), Ogre::Vector3::UNIT_Y));
}
//-------------------------------------------------------------------------------------
Ogre::Real ExoSimControl::getRotation()
{
	return mPosition->getOrientation().getYaw().valueRadians();
}
//-------------------------------------------------------------------------------------
Ogre::Image* ExoSimControl::getImage(CameraType camera) 
{
	Ogre::RenderTarget* renderTarget;
	if (camera == CAM_ROVER)
		renderTarget = mRoverTexture->getBuffer()->getRenderTarget();
	else
		renderTarget = mWorldTexture->getBuffer()->getRenderTarget();

	renderTarget->update();

	size_t width = renderTarget->getWidth();
	size_t height = renderTarget->getHeight();

	Ogre::PixelFormat texPf = renderTarget->suggestPixelFormat();
	size_t texBpp = Ogre::PixelUtil::getNumElemBytes(texPf);
	
	Ogre::uchar *texData = OGRE_ALLOC_T(Ogre::uchar, width * height * texBpp, Ogre::MEMCATEGORY_RENDERSYS);
	Ogre::PixelBox texPb(width, height, 1, texPf, texData);
	renderTarget->copyContentsToMemory(texPb, Ogre::RenderTarget::FB_AUTO);

	Ogre::PixelFormat outPf = Ogre::PF_R8G8B8;
	size_t outBpp = Ogre::PixelUtil::getNumElemBytes(outPf);

	Ogre::uchar *outData = OGRE_ALLOC_T(Ogre::uchar, width * height * outBpp, Ogre::MEMCATEGORY_GENERAL);
	Ogre::PixelBox outPb(width, height, 1, outPf, outData);
	Ogre::PixelUtil::bulkPixelConversion(texPb, outPb);

	Ogre::Image* img = new Ogre::Image();
	img->loadDynamicImage(outData, width, height, 1, outPf, true, 1, 0);
	OGRE_FREE(texData, Ogre::MEMCATEGORY_RENDERSYS);

	return img;
}
//-------------------------------------------------------------------------------------
Ogre::Image* ExoSimControl::getDepth() 
{
	Ogre::Image* img = new Ogre::Image[4];

	size_t width = mDepthTexture->getWidth();
	size_t height = mDepthTexture->getHeight();

	Ogre::PixelFormat texPf = mDepthTexture->getFormat();
	size_t texBpp = Ogre::PixelUtil::getNumElemBytes(texPf);

	Ogre::SceneNode* roverNode = mRoot->getSceneManager("TerrainScene")->getSceneNode("RoverNode");
	roverNode->flipVisibility();
	mDepthCam->setPosition(mRoverCam->getDerivedPosition());
	for (int i = 0; i < 4; i++)
	{
		mDepthCam->setOrientation(Ogre::Quaternion(Ogre::Degree(i * 90), Ogre::Vector3::UNIT_Y));
		mDepthTexture->getBuffer()->getRenderTarget()->update();
		
		Ogre::uchar *texData = OGRE_ALLOC_T(Ogre::uchar, width * height * texBpp, Ogre::MEMCATEGORY_RENDERSYS);
		Ogre::PixelBox texPb(width, height, 1, texPf, texData);
		mDepthTexture->getBuffer()->getRenderTarget()->copyContentsToMemory(texPb, Ogre::RenderTarget::FB_AUTO);

		img[i] = Ogre::Image().loadDynamicImage(texData, width, height, 1, texPf, true, 1, 0);
	}
	roverNode->flipVisibility();

	return img;
}
