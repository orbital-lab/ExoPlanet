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

#define DLL extern "C" __declspec(dllexport)

#include <string.h>
#include "libplayercore/playercore.h"

#include "ExoSimApp.h"

//-------------------------------------------------------------------------------------
/** This is the Player driver instance which is loades as dynamic library plug-in and 
controls the ExoSim component. */
class ExoSimDriver : public ThreadedDriver
{
public:
	/** The driver constructor.  Retrieve options from the configuration file (like the provided
	intervaces) and do some initialization. */
	ExoSimDriver(::ConfigFile* cf, int section);

	/** The message handler. This function is called once for each message in the incoming queue.
	It is used to check the received commands, set new position, trigger the rendering process or reply 
	with data like configs (map dimensions) or actual interface results (deph maps, images, etc.).
	@param resp_queue The message queue.
	@param hdr The message header
	@param data The message body
	@returns 0, if the message could be handeled successfully, -1 otherwise (then NACK will be 
	sent automaticaly, if a response is required) */
	virtual int ProcessMessage(QueuePointer &resp_queue, player_msghdr* hdr, void* data);

private:
	/** Main function for device thread. Causes to render a new frame for display window,
	checks for new messages and sleeps some time to reduce cpu load. */
	virtual void Main();

	/** Set up the device.  Return 0 if things go well, and -1 otherwise. The render 
	application is created here so this call can take some time. */
	virtual int MainSetup();
	/** Shutdown the device. Therefore we destroy the application instance. */
	virtual void MainQuit();

	/** Collect the data to reply it to the client. We create a packet for the position, the
	rover and sky image as well as a range map.
	@param full if false we will not create and send range maps */
	void PublishData(bool full);

	/** The render application instance. */
	ExoSimApp* mApplication;
	/** The application control instance. */
	ExoSimControl* mControl;
	/** A conter for the packet time stamps. */
	double mTime;

	/** The Player position interface. */
    player_devaddr_t m_position_addr;
	/** The first Player camera interface for the rover cam image. */
    player_devaddr_t m_camera_addr;
	/** The second Player camera interface for the world cam image. */
	player_devaddr_t m_camera2_addr;
	/** The Player point cloud interface for the range maps. */
	player_devaddr_t m_points_addr;
	/** The Player Interface to exchange config data (like map dimensions). */
	player_devaddr_t m_confdata_addr;

	/** The Player command for the position interface. */
	player_position2d_cmd_pos_t position2d_cmd_pos;

	/** Data structure which is collected for the range maps. */
	typedef struct location_data
	{
		bool valid; // flag to indicate if there was an pixel for the cell
		int cnt; // counter to sum up all pixels to the same grid cell
		float x; // x pos
		float y; // y pos
		float z; // z pos
		int grd; // ground condition
		int obs; // obstacles
		int haz; // hazard
		int slp; // slope
	} location_data_t;

	/** The config data structure. */
	typedef struct config_data_map
	{
		float xmin;
		float xmax;
		float ymin;
		float ymax;
	} config_data_map_t;
};

//-------------------------------------------------------------------------------------
/** A factory creation function, declared outside of the class so that it
can be invoked without any object context (alternatively, you can
declare it static in the class).  In this function, we create and return
(as a generic Driver*) a pointer to a new instance of this driver. */
Driver* ExoSimDriver_Init(::ConfigFile* cf, int section)
{
	// Create and return a new instance of this driver
	return((Driver*)(new ExoSimDriver(cf, section)));
}
//-------------------------------------------------------------------------------------
/** A driver registration function, again declared outside of the class so
that it can be invoked without object context.  In this function, we add
the driver into the given driver table, indicating which interface the
driver can support and how to create a driver instance. */
void ExoSimDriver_Register(DriverTable* table)
{
	table->AddDriver("exosimdriver", ExoSimDriver_Init);
}
//-------------------------------------------------------------------------------------
ExoSimDriver::ExoSimDriver(::ConfigFile* cf, int section) : ThreadedDriver(cf, section)
{
	// Create camera interfaces
	if (cf->ReadDeviceAddr(&(this->m_camera_addr), section, 
		"provides", PLAYER_CAMERA_CODE, 0, NULL) != 0)
	{
		this->SetError(-1);
		return;
	}    
	if (this->AddInterface(this->m_camera_addr))
	{
		this->SetError(-1);        
		return;
	}
	if (cf->ReadDeviceAddr(&(this->m_camera2_addr), section, 
		"provides", PLAYER_CAMERA_CODE, 1, NULL) != 0)
	{
		this->SetError(-1);
		return;
	} 
	if (this->AddInterface(this->m_camera2_addr))
	{
		this->SetError(-1);        
		return;
	}

	// Create position interface
	if (cf->ReadDeviceAddr(&(this->m_position_addr), section, 
		"provides", PLAYER_POSITION2D_CODE, -1, NULL) != 0)
	{
		this->SetError(-1);
		return;
	}  
	if (this->AddInterface(this->m_position_addr))
	{
		this->SetError(-1);    
		return;
	}

	// Create points interface
	if (cf->ReadDeviceAddr(&(this->m_points_addr), section, 
		"provides", PLAYER_POINTCLOUD3D_CODE, -1, NULL) != 0)
	{
		this->SetError(-1);
		return;
	}    
	if (this->AddInterface(this->m_points_addr))
	{
		this->SetError(-1);        
		return;
	}

	// Create configuration data interface
	if (cf->ReadDeviceAddr(&(this->m_confdata_addr), section, 
		"provides", PLAYER_OPAQUE_CODE, -1, NULL) != 0)
	{
		this->SetError(-1);
		return;
	}    
	if (this->AddInterface(this->m_confdata_addr))
	{
		this->SetError(-1);        
		return;
	}

	return;
}
//-------------------------------------------------------------------------------------
int ExoSimDriver::MainSetup()
{   
	mApplication = new ExoSimApp(true);

	if (!mApplication->setup())
		exit(-1);

	mControl = mApplication->getControl();
	puts("ExoSim driver ready");

	return 0;
}
//-------------------------------------------------------------------------------------
void ExoSimDriver::MainQuit()
{
	delete mApplication;
	puts("ExoSim driver finished");
}
//-------------------------------------------------------------------------------------
int ExoSimDriver::ProcessMessage(QueuePointer &resp_queue, player_msghdr* hdr, void* data)
{
	// process messages
	if (Message::MatchMessage(hdr, PLAYER_MSGTYPE_CMD, PLAYER_POSITION2D_CMD_POS, m_position_addr))
	{
		if (!data) return -1;

		position2d_cmd_pos = *(reinterpret_cast<player_position2d_cmd_pos_t *>(data));

		mControl->setPosition(Ogre::Vector2(position2d_cmd_pos.pos.px, position2d_cmd_pos.pos.py));
		mControl->setRotation(position2d_cmd_pos.pos.pa);
		mControl->setRoverRotation(position2d_cmd_pos.vel.pa);

		if (mApplication->update())
			PublishData(position2d_cmd_pos.state == 1);
	}
	else if (Message::MatchMessage(hdr, PLAYER_MSGTYPE_REQ, PLAYER_OPAQUE_REQ, m_confdata_addr))
	{
		player_opaque_data_t* opaqueDataIn = (player_opaque_data_t*) data;
		int reqType = *((int *)opaqueDataIn->data);

		switch (reqType)
		{
		case 0:
			Ogre::FloatRect mapDim = mApplication->getMapDim();
			config_data_map_t mapData;
			mapData.xmin = mapDim.left;
			mapData.xmax = mapDim.right;
			mapData.ymin = mapDim.top;
			mapData.ymax = mapDim.bottom;

			player_opaque_data_t opaqueData;
			opaqueData.data_count = sizeof(config_data_map_t);
			opaqueData.data = (Ogre::uint8*)&mapData;

			Publish(device_addr, PLAYER_MSGTYPE_DATA, 
				PLAYER_OPAQUE_DATA_STATE, (void*)&opaqueData);
			
			break;
		}
	}

	return 0;
}
//-------------------------------------------------------------------------------------
void ExoSimDriver::Main() 
{
	mTime = 0;

	// The main loop; interact with the device here
	while(true)
	{
		// test if we are supposed to cancel
		pthread_testcancel();

		if (!mApplication->update())
			exit(-1); // quit the thread and kill the parent process
			//pthread_exit(NULL);

		ProcessMessages();

		usleep(500000);
	}
}
//-------------------------------------------------------------------------------------
void ExoSimDriver::PublishData(bool full)
{
	// pos data
	Ogre::Vector2 pos = mControl->getPosition();
	Ogre::Real rot = mControl->getRotation();
	player_position2d_data_t posData;
	posData.pos.px = pos.x;
	posData.pos.py = pos.y;
	posData.pos.pa = rot;

	// img data
	Ogre::Image* roverImg = mControl->getImage(CAM_ROVER);

	player_camera_data_t roverCamData;
	roverCamData.width = roverImg->getWidth();
	roverCamData.height = roverImg->getHeight();
	roverCamData.bpp = roverImg->getBPP();
	roverCamData.image_count = roverImg->getSize();
	roverCamData.image = (uint8_t*) roverImg->getPixelBox().data;

	Ogre::Image* worldImg = mControl->getImage(CAM_WORLD);

	player_camera_data_t worldCamData;
	worldCamData.width = worldImg->getWidth();
	worldCamData.height = worldImg->getHeight();
	worldCamData.bpp = worldImg->getBPP();
	worldCamData.image_count = worldImg->getSize();
	worldCamData.image = (uint8_t*) worldImg->getPixelBox().data;

	Publish(m_position_addr, PLAYER_MSGTYPE_DATA,
		PLAYER_POSITION2D_DATA_STATE, (void*)&posData, 0, &mTime);

	Publish(m_camera_addr, PLAYER_MSGTYPE_DATA,
		PLAYER_CAMERA_DATA_STATE, (void*)&roverCamData, 0, &mTime);

	Publish(m_camera2_addr, PLAYER_MSGTYPE_DATA,
		PLAYER_CAMERA_DATA_STATE, (void*)&worldCamData, 0, &mTime);

	delete roverImg;
	delete worldImg;

	// point data
	if (full)
	{
		int maxDist = 49; // in m
		Ogre::Image* depth = mControl->getDepth();

		int pointsSize = 0;
		int gwidth = 2 * 2 * maxDist; // ground map width: resolution = 0.5 m => 2*2*maxDist
		location_data_t* g = new location_data_t[gwidth * gwidth];
		memset(g, 0, gwidth * gwidth * sizeof(location_data_t));

		for (int i = 0; i < 4; i++) // for all 4 directions/images
		{
			float* depthData = (float*) depth[i].getData();
			int dataLength = depth[i].getWidth() * depth[i].getHeight() * 4;
			for (int i = 0; i < dataLength; i+=4)
			{
				int miscData = (int) depthData[i+3];
				int zVal = miscData & 0xFF;
				if (zVal > 0 && zVal < maxDist)
				{
					float dist = sqrt(pow(pos.x - depthData[i], 2) + pow(pos.y - depthData[i+2], 2));
					if(dist <= maxDist) // only when inside visibly radius of maxDist
					{
						int xidx = (depthData[i] - pos.x + maxDist) * 2;
						int yidx = (depthData[i+2] - pos.y + maxDist) * 2;
						location_data_t* loc_data = &g[xidx * gwidth + yidx];

						if (!loc_data->valid)
							pointsSize++;

						int grd = (miscData >> 8) & 0xFF;
						int obs = (miscData >> 16) & 0x1;
						int haz = (miscData >> 17) & 0x1;
						int slp = (miscData >> 18) & 0x3F;
						loc_data->valid = true;
						loc_data->cnt++;
						loc_data->x += depthData[i];
						loc_data->y += depthData[i+2];
						loc_data->z += depthData[i+1];
						loc_data->grd += grd;
						loc_data->obs += obs;
						loc_data->haz += haz;
						loc_data->slp += slp;
					}
				}
			}
		}
		delete[] depth;

		// use pointsSize + 1 because the firt point marks the current position
		player_pointcloud3d_element_t* points = new player_pointcloud3d_element_t[pointsSize + 1];

		points[0].point.px = pos.x;
		points[0].point.py = pos.y;

		for (int i = 0, j = 1; i < gwidth * gwidth; i++)
		{
			if (g[i].valid) // there should be pointsSize valid entries
			{
				points[j].point.px = g[i].x / g[i].cnt;
				points[j].point.py = g[i].y / g[i].cnt;
				points[j].point.pz = g[i].z / g[i].cnt;
				points[j].color.red = g[i].grd / g[i].cnt; // 0..255
				points[j].color.green = g[i].obs > 0 ? 255 : 0; 
				points[j].color.blue = g[i].haz > 0 ? 255 : 0; 
				points[j].color.alpha = g[i].slp / g[i].cnt; // degree
				
				j++;
			}
		}

		player_pointcloud3d_data_t pointData;
		pointData.points_count = pointsSize;
		pointData.points = points;

		Publish(m_points_addr, PLAYER_MSGTYPE_DATA,
			PLAYER_POINTCLOUD3D_DATA_STATE, (void*)&pointData, 0, &mTime);

		delete[] g;
		delete[] points;
	}

	mTime++;
}
//-------------------------------------------------------------------------------------
/** Initializes the driven whenn shared object was loaded. */
DLL int player_driver_init(DriverTable* table)
{
	puts("ExoSim driver initializing");
	ExoSimDriver_Register(table);

	return(0);
}
