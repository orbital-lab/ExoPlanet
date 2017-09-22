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

package exopath.client;

import java.nio.ByteBuffer;

import javaclient3.OpaqueInterface;
import javaclient3.PlayerClient;
import javaclient3.PlayerDevice;

/**
 * A custom Player interfaces to request some general configuration data (like map dimensions).
 */
public class ConfigDataInterface extends PlayerDevice {

	/** The request id for map dimension data. */
	public static final int MAP_DIM_REQ = 0;

	/**
	 * The MapDim data class.
	 * A data structure for the dimensions of the simulated area.
	 */
	static public class MapDim {

		/** The left map boundary. */
		public double xmin = -517.4; // initial values for dbg

		/** The right map boundary. */
		public double xmax = 1552.2;

		/** The upper map boundary. */
		public double ymin = 517.4;

		/** The lower map boundary. */
		public double ymax = -1552.2;
	}

	/** The Player OpaqueInterface, the ConfigDataInterface is based on. */
	private final OpaqueInterface opaqueIf;

	/** The map dimensions field. */
	private MapDim mapDim;

	/**
	 * Instantiates a new ConfigDataInterface.
	 *
	 * @param robot the Player Client class
	 */
	protected ConfigDataInterface(PlayerClient robot) {
		super(robot);
		opaqueIf = robot.requestInterfaceOpaque(0, PLAYER_OPEN_MODE);
	}

	/**
	 * Sends a request for the map dimensions.
	 */
	public void queryMapData() {

		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putInt(4); // the size of the following query id
		buffer.putInt(MAP_DIM_REQ); // the query id
		opaqueIf.queryData(buffer.array());

		mapDim = null;
	}


    /**
     * Gets the map data.
     *
     * @return the map data
     */
    public MapDim getMapData() {

    	if (mapDim == null) {
    		ByteBuffer data = opaqueIf.getData();
    		if (data != null) {
    			mapDim = new MapDim();
    			mapDim.xmin = data.getFloat();
    			mapDim.xmax = data.getFloat();
    			mapDim.ymin = data.getFloat();
    			mapDim.ymax = data.getFloat();
    		}
    	}

    	return mapDim;
    }

    @Override
	public boolean isDataReady() {
    	return opaqueIf.isDataReady();
    }
}
