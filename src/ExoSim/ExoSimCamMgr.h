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

#ifndef __ExoSimCamMgr_h_
#define __ExoSimCamMgr_h_

#include "Ogre.h"

#include "OISEvents.h"
#include "OISKeyboard.h"
#include "OISMouse.h"

/** Available camera styles. */
enum CameraStyle
{
	/** The camera is fixed, so only the target node will be moved on key commands. */
	CS_FIXED,
	/** The camera points to the target node and can be moved on a sphere around it. The target node
	will be moved on key commands and the camera view can be changed my mouse commends. */
	CS_ORBIT,
	/** The camera can be moved around freely. Key and mouse commands are used for camera movement.
	The targed node will not be changed. */
	CS_FREE
};

/** The camera manager class to provide move or rotate commands or to switch between different perspectives. */
class ExoSimCamMgr
{
public:
	/** The camera manager constructor.
	@param cam a reference to the camera
	@param target the targed node that will be moved and the camera points to in orbit style */
	ExoSimCamMgr(Ogre::Camera* cam, Ogre::SceneNode* target);

	/** Set the camara to be used for move commands.
	@param cam a reference to the camera */
	void setCamera(Ogre::Camera* cam);
	/** Set the target node that will be moved and the camera points to in orbit style.
	@param target the targed node */
	void setTarget(Ogre::SceneNode* target) { mTarget = target; }

	/** Set the camera style to be used.
	@param style the new camera style */
	void setStyle(CameraStyle style);
	/** Get the active camera style
	@returns the active camera style */
	CameraStyle getStyle() { return mStyle;	}

	/** A callback function for render loop event (next frame queued) handling.
	It is used to update the position for a move request.
	@param evt information about the frame event
	@returns true if the frame rendering event could be handled, 
	on false the rendering process will stop */
	bool frameRenderingQueued(const Ogre::FrameEvent& evt);

	/** A callback function for key event (key pressed) handling.
	For the callback we dont register a listener but it is used as a notify 
	method e.g. for the object owning this instance.
	@param evt information about the event */
	void injectKeyDown(const OIS::KeyEvent& evt);
	/** A callback function for key event (key released) handling. 
	For the callback we dont register a listener but it is used as a notify 
	method e.g. for the object owning this instance.
	@param evt information about the event */
	void injectKeyUp(const OIS::KeyEvent& evt);

	/** A callback function for mouse event (mouse moved) handling. 
	For the callback we dont register a listener but it is used as a notify 
	method e.g. for the object owning this instance.
	@param evt information about the event */
	void injectMouseMove(const OIS::MouseEvent& evt);
	/** A callback function for mouse event (mouse button pressed) handling. 
	For the callback we dont register a listener but it is used as a notify 
	method e.g. for the object owning this instance.
	@param evt information about the event
	@param id the button id for mouse devices */
	void injectMouseDown(const OIS::MouseEvent& evt, OIS::MouseButtonID id);
	/** A callback function for mouse event (mouse button released) handling. 
	For the callback we dont register a listener but it is used as a notify 
	method e.g. for the object owning this instance.
	@param evt information about the event
	@param id the button id for mouse devices */
	void injectMouseUp(const OIS::MouseEvent& evt, OIS::MouseButtonID id);

protected:
	/** The reference to the camera, which is controlled. */
	Ogre::Camera* mCamera;
	/** The reference to the target node, which is moved and the camera points to in orbit style. */
	Ogre::SceneNode* mTarget;
	/** The active camera style. */
	CameraStyle mStyle;

	/** A helper function to set angles for orbit style camera movements.
	@param yaw the new yaw angle
	@param pitch the new pitch angle
	@param dist the new distance from target node */
	void setYawPitchDist(Ogre::Radian yaw, Ogre::Radian pitch, Ogre::Real dist);

	/** A flag to indicate that the camera is moved on the orbit sphere. It is true
	when orbit style is active and the left mouse button is pressed and hold down. */
	bool mOrbiting;
	/** A flag to indicate that the camera is moved to or away from the target node in orbit style.
	It is true when orbit style is active and the right mouse button is pressed and hold down. */
	bool mZooming;

	/** A flag to indicate that forward movement is requested. */
	bool mGoingForward;
	/** A flag to indicate that backward movement is requested. */
	bool mGoingBack;
	/** A flag to indicate that movement to the left is requested. */
	bool mGoingLeft;
	/** A flag to indicate that movement to the right is requested. */
	bool mGoingRight;

	/** A flag to indicate that fast movement is active (so make bigger moving steps). */
	bool mFastMove;
};

#endif // #ifndef __ExoSimCamMgr_h_

