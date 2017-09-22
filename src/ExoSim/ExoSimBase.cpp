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

#include "ExoSimBase.h"

//-------------------------------------------------------------------------------------
ExoSimBase::ExoSimBase(bool isPlugin) :
	mPlugin(isPlugin),
    mRoot(0),
    mSceneMgr(0),
	mLogMgr(0),
    mWindow(0),
	mControl(0)
{
}
//-------------------------------------------------------------------------------------
ExoSimBase::~ExoSimBase()
{
    //Remove ourself as a Window listener
    Ogre::WindowEventUtilities::removeWindowEventListener(mWindow, this);
    windowClosed(mWindow);
    delete mRoot;
	delete mLogMgr;
}
//-------------------------------------------------------------------------------------
void ExoSimBase::setupResources(Ogre::String resourcesCfg)
{
    // Load resource paths from config file
    Ogre::ConfigFile cf;
    cf.load(resourcesCfg);

    // Go through all sections & settings in the file
    Ogre::ConfigFile::SectionIterator seci = cf.getSectionIterator();

    Ogre::String secName, typeName, archName;
    while (seci.hasMoreElements())
    {
        secName = seci.peekNextKey();
        Ogre::ConfigFile::SettingsMultiMap *settings = seci.getNext();
        Ogre::ConfigFile::SettingsMultiMap::iterator i;
        for (i = settings->begin(); i != settings->end(); ++i)
        {
            typeName = i->first;
            archName = i->second;
            Ogre::ResourceGroupManager::getSingleton().addResourceLocation(
                archName, typeName, secName);
        }
    }
}
//-------------------------------------------------------------------------------------
bool ExoSimBase::createWindow()
{
    // Show the configuration dialog and initialise the system
    // You can skip this and use root.restoreConfig() to load configuration
    // settings if you were sure there are valid ones saved in ogre.cfg
    if(mRoot->showConfigDialog())
    {
        // If returned true, user clicked OK so initialise
        // Here we choose to let the system create a default rendering window by passing 'true'
        //mWindow = mRoot->initialise(true, "ExoSim Terrain");

		mRoot->initialise(false);
		mWindow = mRoot->createRenderWindow(
				"ExoSim Terrain", // window name
				500, 500, // window width and height
				false); // fullscreen or not

		mWindow->setDeactivateOnFocusChange(false);
    }
    else
        return false;

    return true;
}
//-------------------------------------------------------------------------------------
void ExoSimBase::initScene()
{
	Ogre::ResourceGroupManager::getSingleton().initialiseAllResourceGroups();

	// Get the SceneManager, in this case a generic one
    mSceneMgr = mRoot->createSceneManager(Ogre::ST_GENERIC, "TerrainScene");

    // Create the camera
    mRoverCam = mSceneMgr->createCamera("RoverCam");
    mRoverCam->setNearClipDistance(0.3);
    mRoverCam->setFarClipDistance(2000); 
    if (mRoot->getRenderSystem()->getCapabilities()->hasCapability(Ogre::RSC_INFINITE_FAR_PLANE))
        mRoverCam->setFarClipDistance(0);   // enable infinite far clip distance if we can

	// Create one viewport, entire window
    Ogre::Viewport* vp = mWindow->addViewport(mRoverCam);

    // Alter the camera aspect ratio to match the viewport
	Ogre::Real ratio = Ogre::Real(vp->getActualWidth()) / Ogre::Real(vp->getActualHeight());
    mRoverCam->setAspectRatio(ratio);

	mPosition = mSceneMgr->getRootSceneNode()->createChildSceneNode("TerrainPosition");
	mControl = new ExoSimControl(mRoot, mWindow, mPlugin);

    //Set initial mouse clipping size
    windowResized(mWindow);

    //Register as a Window listener
    Ogre::WindowEventUtilities::addWindowEventListener(mWindow, this);
    mRoot->addFrameListener(this);

	// Set default mipmap level (NB some APIs ignore this)
    Ogre::TextureManager::getSingleton().setDefaultNumMipmaps(5);
	Ogre::MaterialManager::getSingleton().setDefaultTextureFiltering(Ogre::TFO_ANISOTROPIC);
    Ogre::MaterialManager::getSingleton().setDefaultAnisotropy(5);
}
//-------------------------------------------------------------------------------------
bool ExoSimBase::setup()
{
	//Ogre::LogManager::getSingletonPtr()->setLogDetail(Ogre::LoggingLevel::LL_BOREME);

	// no output on the console only to file
	mLogMgr = new Ogre::LogManager();
	Ogre::LogManager::getSingletonPtr()->createLog("ExoSim.log", true, false, false);

	Ogre::String resourcesCfg = "resources.cfg";
    Ogre::String pluginsCfg = "plugins.cfg";

    mRoot = new Ogre::Root(pluginsCfg);

    setupResources(resourcesCfg);

    if (!createWindow()) 
		return false;

	initScene();
    createScene();

	mControl->sceneReady();

    return true;
};
//-------------------------------------------------------------------------------------
void ExoSimBase::go()
{
    if (!setup())
        return;

    mRoot->startRendering();
}
//-------------------------------------------------------------------------------------
bool ExoSimBase::update()
{
	Ogre::WindowEventUtilities::messagePump();
	return mRoot->renderOneFrame();
}
//-------------------------------------------------------------------------------------
bool ExoSimBase::frameRenderingQueued(const Ogre::FrameEvent& evt)
{
	if (mWindow->isClosed())
		return false;
    
	return mControl->frameRenderingQueued(evt);
}
//-------------------------------------------------------------------------------------
void ExoSimBase::windowResized(Ogre::RenderWindow* window)
{
    unsigned int width, height, depth;
    int left, top;
    window->getMetrics(width, height, depth, left, top);

	if (mControl)
		mControl->adjustMouseClipping(width, height);
}
//-------------------------------------------------------------------------------------
void ExoSimBase::windowClosed(Ogre::RenderWindow* window)
{
	// unattach OIS before window shutdown
	if(window == mWindow && mControl)
		mControl->destroyControl();
}
