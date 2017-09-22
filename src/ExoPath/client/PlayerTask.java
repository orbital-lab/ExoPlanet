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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javaclient3.CameraInterface;
import javaclient3.PlayerClient;
import javaclient3.PlayerDevice;
import javaclient3.PlayerException;
import javaclient3.PointCloud3DInterface;
import javaclient3.Position2DInterface;
import javaclient3.structures.PlayerConstants;
import javaclient3.structures.PlayerPose;

/**
 * This class provides the client connection, initialization, the interface management,
 * data polling and notification to the task listeners.
 */
public class PlayerTask implements PlayerConstants {

	/**
	 * The listener interface for receiving Player events.
	 * (e.g. when the client receives new data)
	 * The class that is interested in processing such events
	 * implements this interface and can be registered for
	 * new event notifications.
	 */
	static public interface PlayerListener {

		/**
		 * Notify callback when client receives new data.
		 *
		 * @param dev the Player device for which new data are available
		 */
		public void updateData(Device dev);
	}

	/** The singleton instance. */
	static private PlayerTask instance;

	/** The executor for the connection check and data polling task. */
	private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

	/** The task to check if a connection can established. */
	private Runnable connectionTask;

	/** The Player communication task. */
	private Runnable playerComTask;

	/** The connections status. */
	private boolean connected = false;

	/** The Player client object. */
	private PlayerClient robot;

	/**
	 * The Player device enum type.
	 * Device and interface have the same meaning here. For the Player client the
	 * device is a base class for all interfaces. Interface classes are used to
	 * provide direct (already casted) data access.
	 */
	public static enum Device {

		/** The 2d position device. */
		P2D(PLAYER_POSITION2D_CODE, 0),

		/** The first camera (rover cam) device. */
		CAM1(PLAYER_CAMERA_CODE, 0),

		/** The second camera (sky cam) device. */
		CAM2(PLAYER_CAMERA_CODE, 1),

		/** The 3d point cloud device. */
		PTS(PLAYER_POINTCLOUD3D_CODE, 0),

		/** The configuration interface. */
		CFG(PLAYER_OPAQUE_CODE, 0);

		/** The Player device code. */
		private short type;

		/** The index for multiple devices of the same type. */
		private short index;

		/**
		 * Instantiates a new device enum object.
		 *
		 * @param type The Player device code
		 * @param index the device index
		 */
		Device(short type, int index) {
			this.type = type;
			this.index = (short) index;
		}
	};

	/**
	 * The simulation command.
	 */
	public static enum SIMCMD {

		/** The MOVE command to just change the rover position */
		MOVE,
		/** The FULL command to change position and provide environment data. */
		FULL
	};

	/** The devices map to get access to the device object by the appropriate device enum. */
	private final Map<Device, PlayerDevice> devices = new HashMap<Device, PlayerDevice>();

	/** The timestamp of the last received data. */
	public double timestamp = 0;

	/** The listener list for Player events. */
	private final List<PlayerListener> listeners = new ArrayList<PlayerListener>();

	/**
	 * Gets the Player client component.
	 *
	 * @return the PlayerTask singleton object
	 */
	static public PlayerTask getTask() {
		if (instance == null)
			instance = new PlayerTask();

		return instance;
	}

	/**
	 * Instantiates a new player task.
     * We start a new timer thread to check connectivity and a timer for new data polling.
	 */
	private PlayerTask() {

		// create the connection task which tries to establish a connection
		// and initializes it on success
		connectionTask = new Runnable() {
			@Override
			public void run() {
				if (connected)
					return;
				try {
					System.out.println("try to connect ...");
					robot = new PlayerClient("localhost", 6665); // try to connect
				} catch (PlayerException e) {
					return;
				}
				try {
					for(Device dev : Device.values())
						requestDevice(dev); //
					initConnection();
				} catch (PlayerException e) {
					disconnect();
					return;
				}
			}
		};

		playerComTask = new Runnable() {
			@Override
			public void run() {
				try {
					if (!connected)
						return;
					if (robot.isAlive()) {
						pollData();
					}
					else {
						robot.close();
						connected = false;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
	}

	/**
	 * Request device.
	 *
	 * @param dev the device identifier
	 */
	private void requestDevice(Device dev) {

		PlayerDevice playerDev;
		switch (dev) {
		case CFG:
			playerDev = new ConfigDataInterface(robot); // our own config device
			break;
		default: // get the devices from the Player client object
			playerDev = robot.requestInterface(dev.type, dev.index, PLAYER_OPEN_MODE);
			break;
		}
		devices.put(dev, playerDev); // save device objects
	}

	/**
	 * Inits the Player connection.
	 * This method sends a config request and notifies listeners on reply.
	 */
	private void initConnection() {

		ConfigDataInterface cfgIf = getConfigInterface();
		cfgIf.queryMapData();
		robot.readAll();
		if (!cfgIf.isDataReady()) {
			robot.close();
			return;
		}

		robot.runThreaded(-1, -1);
		connected = true;

		notifyListeners(Device.CFG);
	}

	/**
	 * Connect method.
	 * Starts the tasks for periodic connectivity and new data check.
	 */
	public void connect() {

		exec.scheduleWithFixedDelay(connectionTask, 0, 5, TimeUnit.SECONDS);
		exec.scheduleWithFixedDelay(playerComTask, 0, 200, TimeUnit.MILLISECONDS);
	}

	/**
	 * Disconnect method.
	 * Stops the connection and communication task and closes the connection.
	 */
	public void disconnect() {

		exec.shutdown();
		if (robot != null)
			robot.close();
		connected = false;
	}

	/**
	 * Check for new data, save time stamp and notify listeners.
	 */
	private void pollData() {

		for (Device dev : Device.values()) {
			PlayerDevice playerDev = devices.get(dev);
			if (playerDev.isDataReady()) {
				timestamp = playerDev.getTimestamp();
				notifyListeners(dev);
			}
		}
	}

	/**
	 * Adds a listener for Player events.
	 *
	 * @param listener the listener
	 */
	public void addListener(PlayerListener listener) {
		listeners.add(listener);
	}

	/**
	 * Notify listeners.
	 * A Player event occur for the given device.
	 *
	 * @param dev the device type
	 */
	private void notifyListeners(Device dev) {
		for (PlayerListener listener : listeners)
			listener.updateData(dev);
	}

	/**
	 * Gets the position interface.
	 *
	 * @return the position interface
	 */
	public Position2DInterface getPositionInterface() {
		return (Position2DInterface) devices.get(Device.P2D);
	}

	/**
	 * Gets the first camera (rover cam) interface.
	 *
	 * @return the first camera interface
	 */
	public CameraInterface getCameraInterface1() {
		return (CameraInterface) devices.get(Device.CAM1);
	}

	/**
	 * Gets the second camera (sky cam) interface.
	 *
	 * @return the second camera interface
	 */
	public CameraInterface getCameraInterface2() {
		return (CameraInterface) devices.get(Device.CAM2);
	}

	/**
	 * Gets the point cloud interface.
	 *
	 * @return the point cloud interface
	 */
	public PointCloud3DInterface getPointCloudInterface() {
		return (PointCloud3DInterface) devices.get(Device.PTS);
	}

	/**
	 * Gets the config interface.
	 *
	 * @return the config interface
	 */
	public ConfigDataInterface getConfigInterface() {
		return (ConfigDataInterface) devices.get(Device.CFG);
	}

	/**
	 * Move the simulated rover to the given position.
	 *
	 * @param x the x coordinate of the rover
	 * @param y the y coordinate of the rover
	 * @param a the rotation angle of the sky camera view
	 * @param sim the move command
	 */
	public void moveTo(double x, double y, double a, SIMCMD sim) {

		if (!connected)
			return;

		PlayerPose nextPos = new PlayerPose(x, y, -a);
		try {
			getPositionInterface().setPosition(nextPos, new PlayerPose(), sim.ordinal());
		} catch(PlayerException e) {}
	}

	/**
	 * Move the simulated rover to the given position and change the view.
	 *
	 * @param x the x coordinate of the rover
	 * @param y the y coordinate of the rover
	 * @param ra the rotation angle of the rover
	 * @param a the rotation angle of the sky camera view
	 * @param sim the move command
	 */
	public void moveTo(double x, double y, double ra, double a, SIMCMD sim) {

		if (!connected)
			return;

		PlayerPose nextPos = new PlayerPose(x, y, -a);
		PlayerPose extPos = new PlayerPose(0, 0, -ra);
		try {
			getPositionInterface().setPosition(nextPos, extPos, sim.ordinal());
		} catch(PlayerException e) {}
	}

	/**
	 * Helper method to move the rover forward.
	 *
	 * @param range the range to move
	 */
	public void forward(double range) {

		PlayerPose nextPos = new PlayerPose(getPositionInterface().getData().getPos());
		double a = nextPos.getPa();

		// get the coordinates
		double rx = range * Math.sin(a);
		double ry = range * Math.cos(a);
		nextPos.setPx(nextPos.getPx() - rx);
		nextPos.setPy(nextPos.getPy() - ry);

		getPositionInterface().setPosition(nextPos, new PlayerPose(), 0);
	}

	/**
	 * Helper method to rotate the rover.
	 *
	 * @param angle the rotation angle
	 */
	public void rotate(double angle) {

		PlayerPose nextPos = new PlayerPose(getPositionInterface().getData().getPos());
		nextPos.setPa(nextPos.getPa() - angle);
		getPositionInterface().setPosition(nextPos, new PlayerPose(), 0);
	}
}
