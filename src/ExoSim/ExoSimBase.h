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

#ifndef __ExoSimBase_h_
#define __ExoSimBase_h_

#include "Ogre.h"

#include "ExoSimControl.h"

/** An extendable base class for the ExoSim application. All the necessary stuff independent from
the landscape like ressource allocation and initialization is done here. */
class ExoSimBase : public Ogre::FrameListener, public Ogre::WindowEventListener
{
public:
	/** The application base class constructor.
	@param isPlugin a flag to indicate if this application runs stand-alone or as Player plugin */
    ExoSimBase(bool isPlugin);

	/** The destructor to free all ressources. */
    ~ExoSimBase();

	/** Start the rendering loop. */
	void go();

	/** Initializes the ogre root object and call the scene creation methods. 
	@returns true, if everything is done well */
	bool setup();

	/** If the rendering task is not running in a loop, this method catches window
	events and causes to render one frame. 
	@returns the frame render function result */
	bool update();

	/** Gets the application control instance, e.g. for the plug-in access the
	positioning methods and the render result data.
	@returns application control instance */
	ExoSimControl* getControl() { return mControl; };

protected:
	/** Reads the config files and sets applicatio properties accordingly.
	@param resourcesCfg the configuration file name */
	void setupResources(Ogre::String resourcesCfg);

	/** Creates and inits (size, name, etc.) the application main window.
	@returns true, if window could create successfully or false otherwise (e.g. canceled by the user) */
    bool createWindow();

	/** Basic scene creation stuff. Creates and inits application control, 
	position node or the rover cam and registers window event listeners. The
	rest is done by the createScene() method. */
    void initScene();

	/** The scene creation method to implement by the subclasses. This method is called
	by the setup() method. */
    virtual void createScene() = 0; // Override me!

    /** A callback function for render loop event (next frame queued) handling.
	It is used to check if the user wants to quit the application (by pressing esc),
	then the rendering loop will be stopped.
	@param evt information about a frame event
	@returns true if the frame rendering event could be handled, 
	on false the rendering process will stop */
    virtual bool frameRenderingQueued(const Ogre::FrameEvent& evt);

	/** A callback function for window event (resized) handling.
	It is used do adjust mouse clipping area when the window is resized.
	@param rw a reference to the window */
    virtual void windowResized(Ogre::RenderWindow* rw);

	/** A callback function for window event (closed) handling.
	It is used to unattach OIS (by destroying the application control instance) before window shutdown.
	@param rw a reference to the window */
    virtual void windowClosed(Ogre::RenderWindow* rw);

	/** A flag to indicate that the application is running as a Player plug-in or stand alone. */
	bool mPlugin;

	/** The Ogre root instance. */
    Ogre::Root *mRoot;
	/** The application main window instance. */
    Ogre::RenderWindow* mWindow;    
	/** The scene manager instance to handle all scene objects like meshes, lights or cameras. */
    Ogre::SceneManager* mSceneMgr;
	/** The log manager instance, to cusomize log messages. */
	Ogre::LogManager* mLogMgr;

	/** The actual position node, where the rover entity and cameras are attached to.
	When the position node is moved, the attached objects are moved also. */
	Ogre::SceneNode* mPosition;

	/** The rover camera instance. */
	Ogre::Camera* mRoverCam;

	/** The application control instance for commands and data access. */
	ExoSimControl *mControl;
};

#endif // #ifndef __ExoSimBase_h_

