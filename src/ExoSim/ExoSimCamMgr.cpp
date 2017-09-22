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

#include "ExoSimCamMgr.h"

//-------------------------------------------------------------------------------------
ExoSimCamMgr::ExoSimCamMgr(Ogre::Camera* cam, Ogre::SceneNode* target) : 
	mOrbiting(false),
	mZooming(false),
	mGoingForward(false),
	mGoingBack(false),
	mGoingLeft(false),
	mGoingRight(false),
	mFastMove(false)
{
	setTarget(target);
	setCamera(cam);
	setStyle(CS_FIXED);
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::setCamera(Ogre::Camera* cam) 
{ 
	mCamera = cam; 
	mCamera->setFixedYawAxis(true);
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::setStyle(CameraStyle style)
{
	if (style == CS_ORBIT)
	{
		mCamera->setPosition(mTarget->getPosition());
		mCamera->setOrientation(Ogre::Quaternion::IDENTITY);
		setYawPitchDist(Ogre::Degree(0), Ogre::Degree(25), 18);
	}

	mStyle = style;
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::setYawPitchDist(Ogre::Radian yaw, Ogre::Radian pitch, Ogre::Real dist)
{
	mCamera->setPosition(Ogre::Vector3(0.0));
	mCamera->yaw(yaw);
	mCamera->pitch(-pitch);
	mCamera->moveRelative(Ogre::Vector3(0, 0, dist));
}
//-------------------------------------------------------------------------------------
bool ExoSimCamMgr::frameRenderingQueued(const Ogre::FrameEvent& evt)
{
	Ogre::Vector3 accel = Ogre::Vector3::ZERO;
	if (mStyle == CS_FREE)
	{
		// build our acceleration vector based on keyboard input composite
		if (mGoingForward) accel += mCamera->getDirection();
		if (mGoingBack) accel -= mCamera->getDirection();
		if (mGoingRight) accel += mCamera->getRight();
		if (mGoingLeft) accel -= mCamera->getRight();

		if (accel.squaredLength() != 0)
		{
			accel.normalise();
			double mv = evt.timeSinceLastFrame * 30;
			mCamera->move(accel * (mFastMove ? 2*mv : mv));
		}
	}
	else
	{
		if (mGoingForward) accel -= mTarget->getOrientation().zAxis();
		if (mGoingBack) accel += mTarget->getOrientation().zAxis();
		if (mGoingRight) accel += mTarget->getOrientation().xAxis();
		if (mGoingLeft) accel -= mTarget->getOrientation().xAxis();

		if (accel.squaredLength() != 0)
		{
			accel.normalise();
			double mv = evt.timeSinceLastFrame * 20;
			mTarget->setPosition(mTarget->getPosition() + accel * (mFastMove ? 5*mv : mv));
		}
	}

	return true;
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::injectKeyDown(const OIS::KeyEvent& evt)
{
	if (evt.key == OIS::KC_W || evt.key == OIS::KC_UP) mGoingForward = true;
	else if (evt.key == OIS::KC_S || evt.key == OIS::KC_DOWN) mGoingBack = true;
	else if (evt.key == OIS::KC_A || evt.key == OIS::KC_LEFT) mGoingLeft = true;
	else if (evt.key == OIS::KC_D || evt.key == OIS::KC_RIGHT) mGoingRight = true;
	//else if (evt.key == OIS::KC_PGUP) mGoingUp = true;
	//else if (evt.key == OIS::KC_PGDOWN) mGoingDown = true;
	else if (evt.key == OIS::KC_LSHIFT) mFastMove = true;
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::injectKeyUp(const OIS::KeyEvent& evt)
{
	if (evt.key == OIS::KC_W || evt.key == OIS::KC_UP) mGoingForward = false;
	else if (evt.key == OIS::KC_S || evt.key == OIS::KC_DOWN) mGoingBack = false;
	else if (evt.key == OIS::KC_A || evt.key == OIS::KC_LEFT) mGoingLeft = false;
	else if (evt.key == OIS::KC_D || evt.key == OIS::KC_RIGHT) mGoingRight = false;
	//else if (evt.key == OIS::KC_PGUP) mGoingUp = false;
	//else if (evt.key == OIS::KC_PGDOWN) mGoingDown = false;
	else if (evt.key == OIS::KC_LSHIFT) mFastMove = false;
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::injectMouseMove(const OIS::MouseEvent& evt)
{
	if (mStyle == CS_ORBIT)
	{
		Ogre::Real dist = mCamera->getPosition().length();
		if (mOrbiting)   // yaw around the target, and pitch locally
		{
			setYawPitchDist(Ogre::Degree(-0.25 * evt.state.X.rel), 
							Ogre::Degree(0.25 *  evt.state.Y.rel), dist);
		}
		else if (mZooming)  // move the camera toward or away from the target
		{
			// the further the camera is, the faster it moves
			mCamera->moveRelative(Ogre::Vector3(0, 0, 0.004 * evt.state.Y.rel * dist));
		}
		else if (evt.state.Z.rel != 0)  // move the camera toward or away from the target
		{
			// the further the camera is, the faster it moves
			mCamera->moveRelative(Ogre::Vector3(0, 0, -0.0008 * evt.state.Z.rel * dist));
		}
	}
	else if (mStyle == CS_FREE)
	{
		mCamera->yaw(Ogre::Degree(-0.15 * evt.state.X.rel));
		mCamera->pitch(Ogre::Degree(-0.15 * evt.state.Y.rel));
	}
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::injectMouseDown(const OIS::MouseEvent& evt, OIS::MouseButtonID id)
{
	if (mStyle == CS_ORBIT)
	{
		if (id == OIS::MB_Left) mOrbiting = true;
		else if (id == OIS::MB_Right) mZooming = true;
	}
}
//-------------------------------------------------------------------------------------
void ExoSimCamMgr::injectMouseUp(const OIS::MouseEvent& evt, OIS::MouseButtonID id)
{
	if (mStyle == CS_ORBIT)
	{
		if (id == OIS::MB_Left) mOrbiting = false;
		else if (id == OIS::MB_Right) mZooming = false;
	}
}
