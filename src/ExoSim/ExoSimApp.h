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

#ifndef __ExoSimApp_h_
#define __ExoSimApp_h_
 
#include "ExoSimBase.h"

#include "Terrain/OgreTerrainGroup.h"
#include "PagedGeometry.h"

/** The ExoSim application class as main component to create a planetary landscape. */
class ExoSimApp : public ExoSimBase
{
public:
	/** The application constructor.
	@param isPlugin a flag to indicate if this application runs stand-alone or as Player plugin */
    ExoSimApp(bool isPlugin = false);

	/** The destructor to free all ressources. */
    ~ExoSimApp();

	/** The map dimensions field. */
	Ogre::RealRect mMapDim;

	/** Get the map dimensions. 
	@return a rectangle object for the map dimensions / boundary */
	Ogre::RealRect getMapDim() { return mMapDim; };

	/** The callback method for the paged geometry to get the height value for an ojects
	@param x the x coordinate of the object
	@param z the z coordinate of the object
	@returns the height value of the terrain for the given coordinate */
	inline float getTerrainHeight(const float x, const float z) {
		Ogre::Ray ray;										// get the height value by a ray intersection
		ray.setOrigin(Ogre::Vector3(x, 1000, z));           // the source is 1000 meters above
		ray.setDirection(Ogre::Vector3::NEGATIVE_UNIT_Y);   // to the ground direction
		return (float) mTerrainGroup->rayIntersects(ray).position.y; // return the intersection point
	}

private:
	/** The properties for the terrain component. */
    Ogre::TerrainGlobalOptions* mTerrainGlobals;

	/** The terrain manager object. */
    Ogre::TerrainGroup* mTerrainGroup;

	/** Creates the terrain instances by reading height data from file and setting texture data.
	@param dtmName the name of the height data / dtm page (the dtm file name without extension)
	@param x the x index of the terrain page this dtm is for
	@param y the y index of the terrain page this dtm is for
	@param dtmImageSize the size of the dtm file (the grid dimension) */
	void defineTerrain(Ogre::String dtmName, long x, long y, int dtmImageSize);

	/** Set some terrain properties like the landscape size and a custom material generator. */
	void configureTerrainDefaults();

	/** The paged geometry component for big rocks / obstacles. */
	Forests::PagedGeometry* mRocks;
	/** The paged geometry component for small rocks / hazard. */
	Forests::PagedGeometry* mSoil;

protected:
	/** Setup the planetary landscape scene. */
    virtual void createScene();

	/** A callback function for render loop event (next frame queued) handling.
	It is used to update the paged geometry component.
	@param evt information about a frame event
	@returns true if the frame rendering event could be handled, 
	on false the rendering process will stop */
    virtual bool frameRenderingQueued(const Ogre::FrameEvent& evt);
};
 
#endif // #ifndef __ExoSimApp_h_
