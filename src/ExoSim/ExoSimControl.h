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

#ifndef __ExoSimControl_h_
#define __ExoSimControl_h_

#include "Ogre.h"

#include "OISEvents.h"
#include "OISInputManager.h"
#include "OISKeyboard.h"
#include "OISMouse.h"
#include "SdkTrays.h"

#include "ExoSimCamMgr.h"

/** The camera identifier. */
enum CameraType
{
	/** The rover cam, fixed to the rover node. */
	CAM_ROVER,
	/** The world cam for top views or custom perspectives. */
	CAM_WORLD
};

/** The application control class for move commands and data access (when app is running as plug-in). */
class ExoSimControl : public OIS::KeyListener, public OIS::MouseListener, OgreBites::SdkTrayListener
{
public:
	/** The application control constructor.
	@param root the reference to the ogre root instance
	@param window the reference to the application main window
	@param isPlugin a flag to indicate if this application runs stand-alone or as Player plugin */
	ExoSimControl(Ogre::Root* root, Ogre::RenderWindow* window, bool isPlugin);

	/** The destructor to free all ressources. */
    ~ExoSimControl();

	/** A callback function to signalize that scene creation is finished for some finalization tasks. */
	void sceneReady();

	/** A callback function for render loop event (next frame queued) handling.
	It is used to hold rendering process to update mouse and keyboard states. We also call
	the callback methods of some member objects (like the camera controller) and update
	the height value of the rover node afterwards to pin it to the ground.
	@param evt information about a frame event
	@returns true if the frame rendering event could be handled, 
	on false the rendering process will stop */
	bool frameRenderingQueued(const Ogre::FrameEvent& evt);

	/** A callback function for window resize events to update the area for the
	mouse device. For the callback we dont register a listener but it is used as a
	notify method e.g. for the object owning this instance.
	@param width the new window width
	@param height the new window height */
	void adjustMouseClipping(const unsigned int width, const unsigned int height);

	/** This is a cleanup method to disconnect imput devices and release other ressources.
	This method should e.g. called with a window close event. */
	void destroyControl();

	/** Set a function pointer for the height evaluation function to use for pinning
	the rover model to the ground. */
	void setHeightFunction(float (*height)(float x, float z)) { this->height = height; }

	/** Set the position of the position node and by this also for rover model and cam.
	@param pos the x,y coordinate vector of the new position */
	void setPosition(Ogre::Vector2 pos);
	/** Set the rotation of the position node. This is used to change the perspective for
	the view from outside to the rover.
	@param rot the rotation (yaw) angle in rad */
	void setRotation(Ogre::Real rot);
	/** Set the rotation of the rover node and model. This is used to simulate rover driving
	for different directions.
	@param rot the rotation (yaw) angle of the rover model in rad */
	void setRoverRotation(Ogre::Real rot);

	Ogre::Vector2 getPosition();
	Ogre::Real getRotation();

	/** Get the image for the given camera.
	@param camera the camera identifier
	@returns the actual camera image */
	Ogre::Image* getImage(CameraType camera);

	/** Get an array of the deph maps in all directions with 90deg FOV. This is a float image.
	The first channel is used for the x coordinate (in m), the second is the y or height value
	and the third channel holds the z coordinate. The alpha channel is used for misc data:
	first 8 bit the distance in m, 8 bit ground condition, 1 bit obstacle flag, 1 bit hazard flag
	and last 6 bit slope value in deg.
	@returns the array of the 4 deph maps */
	Ogre::Image* getDepth();

protected:
	/** A callback function for key event (key pressed) handling. 
	@param evt information about the event
	@returns true, on successful event handling, other listeners will get this event too,
	return false to break further notifications */
    virtual bool keyPressed(const OIS::KeyEvent &evt);
	/** A callback function for key event (key released) handling. 
	@param evt information about the event
	@returns true, on successful event handling, other listeners will get this event too,
	return false to break further notifications */
	virtual bool keyReleased(const OIS::KeyEvent &evt);

	/** A callback function for mouse event (mouse moved) handling. 
	@param evt information about the event
	@returns true, on successful event handling, other listeners will get this event too,
	return false to break further notifications */
	virtual bool mouseMoved(const OIS::MouseEvent &evt);
	/** A callback function for mouse event (mouse button pressed) handling. 
	@param evt information about the event
	@param id the button id for mouse devices
	@returns true, on successful event handling, other listeners will get this event too,
	return false to break further notifications */
	virtual bool mousePressed(const OIS::MouseEvent &evt, OIS::MouseButtonID id);
	/** A callback function for mouse event (mouse button released) handling.
	@param evt information about the event
	@param id the button id for mouse devices
	@returns true, on successful event handling, other listeners will get this event too,
	return false to break further notifications */
	virtual bool mouseReleased(const OIS::MouseEvent &evt, OIS::MouseButtonID id);

	/** The Ogre root instance to provide access to the different scene objects. */
	Ogre::Root* mRoot;
	/** The application main window instance to set some view properties like background color or 
	displayed on-screen ui elements. */
	Ogre::RenderWindow* mWindow;
	/** The actual position node to move the attaced rover model and camera around. */
	Ogre::SceneNode* mPosition;

	/** The camera controller as a helper class for camera and rover movements based on
	keyboard or mouse inputs. */
	ExoSimCamMgr* mCameraMan;

    /** The world camera instance to provide views from outside to the rover. */
	Ogre::Camera* mWorldCam;
	/** The (fixed) rover camera instance. */
	Ogre::Camera* mRoverCam;
	/** The depth camera instance for depth map rendering (e.g. with 90deg FOV) */
	Ogre::Camera* mDepthCam;

	/** A flag to indicate if this application runs stand-alone or as Player plugin. */
	bool mPlugin;
	/** A flag to indicate that application shutdown was requested. */
	bool mShutDown;

    //OIS Input stuff
	/** The manager class to create input (mouse, keyboard) device controllers. */
    OIS::InputManager* mInputManager;
	/** A reference to the connected mouse device. */
    OIS::Mouse* mMouse;
	/** A reference to the connected keyboard device. */
    OIS::Keyboard* mKeyboard;

    // OgreBites stuff
	/** The controller to provide custom ui elements (like a custom information panel). */
    OgreBites::SdkTrayManager* mTrayMgr;
    /** The ui element to provide to show some information about the actual position or currend render modes. */
    OgreBites::ParamsPanel* mDetailsPanel;

	/** A pointer to the height function to get the height value vor a given 2D coordinate. */
	float (*height)(float x, float z);

	/** A texture used as render target to create images for the rover camera. */
	Ogre::TexturePtr mRoverTexture;
	/** A texture used as render target to create images for the world camera. */
	Ogre::TexturePtr mWorldTexture;
	/** A texture used as render target to create depth maps. */
	Ogre::TexturePtr mDepthTexture;
};

#endif // #ifndef __ExoSimControl_h_
