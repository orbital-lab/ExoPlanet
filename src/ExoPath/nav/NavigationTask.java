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

package exopath.nav;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javaclient3.structures.PlayerPoint3d;
import javaclient3.structures.pointcloud3d.PlayerPointCloud3DElement;

import javax.imageio.ImageIO;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.newdawn.slick.util.pathfinding.Path;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jhlabs.image.MaximumFilter;

import exopath.client.ConfigDataInterface.MapDim;
import exopath.client.PlayerTask;
import exopath.client.PlayerTask.Device;
import exopath.client.PlayerTask.PlayerListener;
import exopath.client.PlayerTask.SIMCMD;

/**
 * The class provides the navigation algorithm for the ExoPlanet exploration strategy.
 */
public class NavigationTask {

	/** The singleton instance. */
	static private NavigationTask instance;

	/**
	 * The listener interface for receiving navigation task events
	 * (e.g. when new path planning results are available).
	 * The class that is interested in processing a navigation
	 * event implements this interface and can be registered for
	 * new event notifications.
	 */
	static public interface NavigationListener {

		/**
		 * Notify callback when new path planning results are available.
		 */
		public void updateData();
	}

	/**
	 * A descriptoin for position related data.
	 */
	public class Position {

		/** The x coordinate of the position. */
		public double x;
		/** The y coordinate of the position. */
		public double y;

		/** The ground condition indicator for the position. */
		public int grd;
		/** The obstacle indicator for the position. */
		public int obs;
		/** The slope value for the position (in degree). */
		public int slp;
		/** The hazard indicator for the position. */
		public int haz;

		/** The related map x coordinate for the position. */
		public int mapX;
		/** The related map x coordinate for the position. */
		public int mapY;

		/**
		 * Set the data for the position object.
		 *
		 * @param pce the Player point cloud data which provide the different parameters
		 */
		public void setData(PlayerPointCloud3DElement pce) {
			this.x = pce.getPoint().getPx();
			this.y = pce.getPoint().getPy();
			this.grd = pce.getColor().getRed();   // the ground condition value is coded to the red channel
			this.obs = pce.getColor().getGreen(); // the obstacle flag is coded to the green channel
			this.haz = pce.getColor().getBlue();  // the hazard indicator  is coded to the blue channel
			this.slp = pce.getColor().getAlpha(); // the slope value is coded to the alpha channel
		}

		/**
		 * Sets the data by copying a given position object.
		 *
		 * @param p position object for data copy
		 */
		public void setData(Position p) {
			x = p.x; y = p.y; mapX = p.mapX; mapY = p.mapY;
			grd = p.grd; obs = p.obs; haz = p.haz; slp = p.slp;
		}
	}

	/**
	 * The exploration tree nodes (containing the position,
	 * mean area radius and the route to the next exploration point.
	 */
	public class TreeNode {

		/** The position of the node. */
		public Point2D.Double pos;

		/** The mean distance (radius) of the explored area. */
		public Double r;

		/** The selected route to the next exploration point. */
		Route route;

		/**
		 * Creates a new tree node based on the given data.
		 *
		 * @param pos the position of the node
		 * @param r the mean radius of the explored area
		 * @param route the route to the next exploration point
		 */
		public TreeNode(Point2D.Double pos, Double r, Route route) {
			this.pos = pos;
			this.r = r;
			this.route = route;
		}
	}

	/**
	 * The class to represent a route and its parameters.
	 */
	private class Route {

		/** The path as the result of the path planner. */
		transient public Path path;

		/** The goal or the route. */
		transient public Point goal;

		/** The path cost value. */
		transient public float cost;
		/** The direct distance between the start and the goal point. */
		transient public float dist;

		/** The path length. */
		public float length;

		/**
		 * Set the data by copying a given route object.
		 *
		 * @param r the route object to copy
		 */
		public void copyFrom(Route r) {
			path = r.path;
			goal = r.goal;
			cost = r.cost;
			dist = r.dist;
			length = r.length;
		}
	}

	/**
	 * A maximum filter used for the dilation operation.
	 */
	private class ContourFilter extends MaximumFilter {

		@Override
		protected int[] filterPixels( int width, int height, int[] inPixels, Rectangle transformedSpace ) {
			int[] outPixels = super.filterPixels(width, height, inPixels, transformedSpace);
			for (int i = 0; i < outPixels.length; i++)
				outPixels[i] -= inPixels[i];
			return outPixels;
		}
	}

	/** The player client component. */
	private final PlayerTask player = PlayerTask.getTask();

	/** The time stamp for the last received range points. */
	public int rangeTS;

	/** The time stamp for the last received position. */
	public int posTS;

	/** The maximum range for the local region. */
	public double range = 50;

	/** The resolution for the range maps. */
	public double res = 0.5;

	/** The navigation grid size (based on the max range and resolution). */
	public int imgSize = (int) (2*range / res);

	/** The map instance for the path planner. */
	private final Map map = new Map(imgSize, imgSize);

	/** The Dijkstra path planner instance. */
	private final DijkstraPathFinder finder = new DijkstraPathFinder(map, 200, true);

	/** An image for range map filtering. */
	private final BufferedImage rangeMap = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_BGR);

	/** An image to hold the determined reachable area. */
	private final BufferedImage reachImg = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_BYTE_BINARY);

	/** An image to hold the determined unreachable area. */
	private final BufferedImage unreachImg = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_BYTE_BINARY);

	/** The maximum size for the range point buffer. */
	private final int rangePtBufferSize = (int)(2*Math.PI*Math.pow(range/res, 2));

	/** The range point data buffer. We use a fixed array to avoid instanciation and garbage collection overhead*/
	private final List<Position> rangePtBuffer = new ArrayList<Position>(rangePtBufferSize);

	/** The number of exploration rovers. */
	public int multiNum   = 2;

	/** The number of steps before we switch to the next rover. */
	public int multiSteps = 4;

	/** The counter for the current rover. */
	public int multiCnt   = 0;

	/** The counter for the passed steps. */
	public int multiSCnt  = 0;

	/** The buffer to hold the next navigation goal when switching between the rovers. */
	private final List<Point2D.Double> nextPts = new ArrayList<Point2D.Double>();

	/** The overall path lengths. */
	public float[] pathLength = new float[multiNum];

	/** The exploration trees. */
	public List<ExplorationTree> multiNavTree = new ArrayList<ExplorationTree>();

	/** The moving sync object to make the path move thread waiting on new data. */
	private final String moving = "moving lock";

	/** The navigation task event listeners. */
	private final List<NavigationListener> listeners = new ArrayList<NavigationListener>();

	/**
	 * Gets the navigation component.
	 *
	 * @return the navigation task singleton object
	 */
	static public NavigationTask getTask() {
		if (instance == null)
			instance = new NavigationTask();

		return instance;
	}

	/**
	 * Instantiates a new navigation task.
	 * All the initialization is done here: registering the player event listeners
	 * and set the rover start positions
	 */
	private NavigationTask() {

		// init exploration trees and next point buffer
		for (int i = 0; i < multiNum; i++) {
			multiNavTree.add(new ExplorationTree());
			nextPts.add(new Point2D.Double(0, 0));
		}

		// allocate range point buffer
		for (int i = 0; i < rangePtBufferSize;i++)
			rangePtBuffer.add(new Position());

//		restoreTrees();

		player.addListener(new PlayerListener() {
			public void updateData(Device dev) {
				switch (dev) {
				case CFG:
					for (int i = 0; i < multiNum; i++) {
						ExplorationTree t = multiNavTree.get(i);
						if (t.isEmpty()) {
							// setup initial positions in a circle around map center
							MapDim mapDim = player.getConfigInterface().getMapData();
							double posX = mapDim.xmin + (mapDim.xmax - mapDim.xmin) / 2;
							double posY = mapDim.ymin + (mapDim.ymax - mapDim.ymin) / 2;
							double inc = 2.0 * Math.PI / multiNum;
							double dist = 60;
							double x = posX + Math.cos(inc * i) * dist;
							double y = posY + Math.sin(inc * i) * dist;
							nextPts.set(i, new Point2D.Double(x, y));
						}
					}
					player.moveTo(nextPts.get(0).x, nextPts.get(0).y, 0, SIMCMD.FULL);
					break;
				case PTS:
					rangeTS = (int) player.getPointCloudInterface().getTimestamp();
					PlayerPointCloud3DElement[] pcData =
						player.getPointCloudInterface().getData().getPoints();

					List<Position> rangePoints = rangePtBuffer.subList(0, pcData.length-1);
					PlayerPoint3d worldPos = pcData[0].getPoint();
					for (int i = 0; i < pcData.length-1; i++) {
						Position pos = rangePoints.get(i);
						pos.setData(pcData[i+1]);
						pos.mapX = (int)((pos.x - worldPos.getPx() + range) / (2*range) * imgSize);
						pos.mapY = (int)((pos.y - worldPos.getPy() + range) / (2*range) * imgSize);
					}

					map.setData(rangePoints);

					if (multiSCnt == multiSteps) {
						multiSCnt = 0;
						multiCnt = (multiCnt + 1) % multiNum;
					}

					explore(new Point2D.Double(worldPos.getPx(), worldPos.getPy()));
					break;
				case P2D:
					posTS = (int) player.getPositionInterface().getTimestamp();
					synchronized (moving) {
						moving.notify();
					}
					break;
				default:
					break;
				}
			}
		});
	}

	/**
	 * Register navigation task listeners.
	 *
	 * @param listener the listener instance
	 */
	public void addListener(NavigationListener listener) {
		listeners.add(listener);
	}

	/**
	 * Notify all navigation task listeners.
	 */
	private void notifyListeners() {
		for (NavigationListener listener : listeners)
			listener.updateData();
	}

	/**
	 * Gets the range map as image.
	 *
	 * @return the range map image
	 */
	public ImageData getRangeMap() {
		return convertToSWT(rangeMap);
	}

	/**
	 * Gets the exploration tree for the current rover.
	 *
	 * @return the navigation tree
	 */
	private ExplorationTree getNavTree() {
		return multiNavTree.get(multiCnt);
	}

	/**
	 * Update the exploration tree for the current rover, e.g. when a new tree node was added.
	 *
	 * @param tree the new exploration tree
	 */
	private void setNavTree(ExplorationTree tree) {
		if (tree != null)
			multiNavTree.set(multiCnt, tree);
	}

	/**
	 * The explore method is the implementation of the navigation process.
	 *
	 * @param worldPos the current world position
	 */
	private void explore(final Point2D.Double worldPos) {

		finder.initPathFinder(map);

		for (int y = 0; y < map.getHeight(); y++) {
			for (int x = 0; x < map.getWidth(); x++) {
				reachImg.getRaster().setSample(x, y, 0, 0);
				unreachImg.getRaster().setSample(x, y, 0, 0);
			}
		}

		Graphics2D gc = rangeMap.createGraphics();
		gc.setBackground(new Color(30, 150, 30));
		gc.clearRect(0, 0, imgSize, imgSize);

		for (int y = 0; y < map.getHeight(); y++) {
			for (int x = 0; x < map.getWidth(); x++) {

				int val = map.getTerrain(x, y, Map.VAL);
				gc.setColor(val == 1 ? Color.gray : Color.darkGray);

				if (val > 1) {
					int slp = map.getTerrain(x, y, Map.SLP);
					if (slp > 8) {
						int slpVal = 20 + 5 * slp;
						gc.setColor(new Color(255, 250, 0, slpVal > 255 ? 255 : slpVal));
						gc.fillRect(x, y, 1, 1);
					}
					int grd = map.getTerrain(x, y, Map.GRD);
					if (grd < 70) {
						gc.setColor(new Color(50, 90, 0, 2*grd));
						gc.fillRect(x, y, 1, 1);
					}
					else {
						gc.setColor(new Color(50, 90, 0));
						gc.fillRect(x, y, 1, 1);
					}
					int haz = map.getTerrain(x, y, Map.HAZ);
					if (haz > 0)
						gc.setColor(new Color(240, 150, 0, haz));
					int obs = map.getTerrain(x, y, Map.OBS);
					if (obs > 0)
						gc.setColor(new Color(220, 20, 20, obs));
					if (map.isPosition(x, y))
						gc.setColor(Color.black);
				}
				gc.fillRect(x, y, 1, 1);

				if (map.visited(x, y))
					reachImg.getRaster().setSample(x, y, 0, 1);
			}
		}

		List<Point> plist = new LinkedList<Point>();
		plist.add(new Point(0,0));

		while (!plist.isEmpty()) {
			Point pt = plist.remove(0);
			unreachImg.getRaster().setSample(pt.x, pt.y, 0, 1);
			for (int x = -1; x < 2; x++) {
				for (int y = -1; y < 2; y++) {
					if (x == 0 && y == 0)
						continue;
					int cx = pt.x + x;
					int cy = pt.y + y;
					if (cx < 0 || cy < 0 || cx >= imgSize || cy >= imgSize)
						continue;
					if (reachImg.getRaster().getSample(cx, cy, 0) == 1)
						continue;
					if (unreachImg.getRaster().getSample(cx, cy, 0) != 1) {
						plist.add(new Point(cx, cy));
						unreachImg.getRaster().setSample(cx, cy, 0, 1);
					}
				}
			}
		}

		BufferedImage unreachImgFilt = reachImg; // reuse buffer of reachImg
        new ContourFilter().filter(unreachImg, unreachImgFilt);

		List<Point> reachable = new ArrayList<Point>();
		gc.setColor(Color.white);
		for (int y = 0; y < imgSize; y++)
			for (int x = 0; x < imgSize; x++)
				if (unreachImgFilt.getRaster().getSample(x, y, 0) == 1) {
					gc.fillRect(x, y, 1, 1);
					reachable.add(new Point(x, y));
				}

		Point2D.Double nextPt = new Point2D.Double(Double.NaN, Double.NaN);
		Route nextRoute = new Route();
		int r = 0;
		if (reachable.size() > 0) {

			double sum = 0;
			int posX = map.getPosX();
			int posY = map.getPosY();
			for (Point p : reachable)
				sum += Math.sqrt(Math.pow(p.x - posX, 2) + Math.pow(p.y - posY, 2));
			r = (int)(sum / reachable.size());
			gc.drawOval(posX-r, posY-r, 2*r, 2*r);

			List<Route> routes = new ArrayList<Route>();
			Random rndm = new Random();
			for (int i = 0; i < 15; i++) {
				Point g = reachable.get(rndm.nextInt(reachable.size()));
				Route route = new Route();
				route.goal = g;
				route.path = finder.findPath(g.x, g.y);
				route.cost = finder.getPathCost(g.x, g.y);
				route.dist = (float) (Math.sqrt(Math.pow(g.x-posX, 2) + Math.pow(g.y-posY, 2)) * res);
				route.length = (float) (finder.getPathDist(g.x, g.y) * res);
				routes.add(route);
			}

			Collections.sort(routes, new Comparator<Route>() {
				public int compare(Route r1, Route r2) {
					return new Float(r1.cost / r1.dist).compareTo(r2.cost / r2.dist);
				}
			});

			for (Route route : routes) {

				//double nextX = (double) route.goal.x / imgSize * 2*range - range + worldPos.x;
				//double nextY = (double) route.goal.y / imgSize * 2*range - range + worldPos.y;
				int idx = map.getRangePtIdx(route.goal.x, route.goal.y);
				if (idx < 0 || rangePtBuffer.size() <= idx)
					continue;
				double nextX = rangePtBuffer.get(idx).x;
				double nextY = rangePtBuffer.get(idx).y;
				Point2D.Double pt = new Point2D.Double(nextX, nextY);

				if (worldPos.distance(pt) > 10)
					if (!isMultiExplored(pt)) {
						nextPt.x = pt.x;
						nextPt.y = pt.y;
						drawPath(gc, route.path, Color.black);
						nextRoute.copyFrom(route);
						break;
					}
			}
			gc.dispose();
		}

		if (getNavTree().isEmpty() || getNavTree().getNode().pos.distance(worldPos) > 5) {
			TreeNode node = new TreeNode(worldPos, r * res, nextRoute);
			ExplorationTree newNavTree = getNavTree().addLeaf(node);
			setNavTree(newNavTree);
		}

		notifyListeners();
		//saveNavMap();
		//saveTree();

		//moveRover(nextPt, nextRoute, worldPos);

		if (!Double.isNaN(nextPt.x)) {
			moveTo(nextPt, 0, SIMCMD.FULL);
		}
		else {
			setNavTree(getNavTree().getParent());
			moveTo(getNavTree().getNode().pos, 0, SIMCMD.FULL);
		}

		if (!Double.isNaN(nextPt.x))
			pathLength[multiCnt] += nextRoute.length;
		else if (getNavTree().getParent() != null)
			pathLength[multiCnt] += getNavTree().getParent().getNode().route.length;
	}

	/**
	 * Starts the rover driving thread to move it along the path (e.g. to create animations).
	 *
	 * @param nextPt the next position
	 * @param nextRoute the the path to the next position
	 */
	@SuppressWarnings("unused")
	private void moveRover(final Point2D.Double nextPt, final Route nextRoute) {

		new Thread("Rover Driving Thread") {
			@Override
			public void run() {

				double ra = 0;
				Path path = nextRoute.path; // TODO check valid
				double[] px = new double[path.getLength()];
				double[] py = new double[path.getLength()];
				for (int i = 0; i < path.getLength(); i++) {
					int idx = map.getRangePtIdx(path.getX(i), path.getY(i));
					px[i] = rangePtBuffer.get(idx).x;
					py[i] = rangePtBuffer.get(idx).y;
				}

				int psteps = 6;
				for (int i = 0; i < px.length-1; i++) {
					double dx = (px[i+1] - px[i]) / psteps;
					double dy = (py[i+1] - py[i]) / psteps;
					ra = Math.atan2(py[i+1] - py[i], px[i+1] - px[i]);
					ra = (ra + Math.PI/2 + 2*Math.PI) % (2*Math.PI);
					for (int j = 0; j < psteps; j++) {
						synchronized (moving) {
							moveTo(px[i] + j*dx, py[i] + j*dy, ra, SIMCMD.MOVE);
							try {
								moving.wait();
							} catch (InterruptedException e) {}
						}
					}
					notifyListeners();
				}

				if (!Double.isNaN(nextPt.x)) {
					moveTo(nextPt, ra, SIMCMD.FULL);
				}
				else {
					setNavTree(getNavTree().getParent());
					moveTo(getNavTree().getNode().pos, ra, SIMCMD.FULL);
				}
			};
		}.start();
	}

	/**
	 * Draws the path.
	 *
	 * @param gc the graphics context used for drawing
	 * @param path the path to draw
	 * @param color the color of the path to draw
	 */
	private void drawPath(Graphics2D gc, Path path, Color color) {
		gc.setColor(color);
		for (int i = 0; i < path.getLength(); i++)
			gc.fillRect(path.getX(i), path.getY(i), 1, 1);
	}

	/**
	 * Save the navigation map as image to a file.
	 */
	@SuppressWarnings("unused")
	private void saveNavMap() {

		DecimalFormat df = new DecimalFormat("0000");
		File imgFile = new File("navmap-" + df.format(rangeTS) + ".png");
		try {
			ImageIO.write(rangeMap, "png", imgFile);
		} catch (IOException e) {}
	}

	/**
	 * Save the exploration tree structure to a file, e.g. to continue the exploration process later.
	 */
	@SuppressWarnings("unused")
	private void saveTree() {

		Gson gson = new Gson();

		int id = getNavTree().getID();
		ExplorationTree t = getNavTree().getRoot();
		Type treeType = new TypeToken<ExplorationTree>(){}.getType();

		Writer fw = null;
		try {
			DecimalFormat df = new DecimalFormat("0000");
			File jsonFile = new File("navtree-" + multiCnt + "-" + df.format(rangeTS) + ".json");
			fw = new FileWriter(jsonFile, false);
			fw.write(pathLength[multiCnt] + "\n");
			fw.write(id + "\n");
			fw.write(gson.toJson(t, treeType));
		}
		catch (IOException e) { e.printStackTrace(); }
		finally {
			if (fw != null)
				try { fw.close(); } catch(IOException e) {}
		}
	}


	/**
	 * Restore the exploration trees from files.
	 */
	@SuppressWarnings("unused")
	private void restoreTrees() {

		IPath workspace = Platform.getLocation();
		for (int i = 0; i < multiNum; i++) {
			File jsonFile = workspace.append("maps\\navtree-" + i + ".json").toFile();
			if (jsonFile.exists()) {
				BufferedReader br = null;
				try {
					Gson gson = new Gson();
					br = new BufferedReader(new FileReader(jsonFile));
					pathLength[i] = Float.parseFloat(br.readLine());
					int id = Integer.parseInt(br.readLine());
					Type treeType = new TypeToken<ExplorationTree>(){}.getType();
					ExplorationTree t = gson.fromJson(br.readLine(), treeType);
					t = t.rebuildTree(id);
					multiNavTree.set(i, t);
					nextPts.set(i, t.getNode().pos);
				}
				catch (IOException e) {}
				finally {
					if (br != null)
						try { br.close(); } catch(IOException e) {}
				}
			}
		}
	}

	/**
	 * Move (and rotate) the rover to a given position.
	 *
	 * @param pt the point to move to
	 * @param ra the rover rotation angle
	 * @param cmd the simulation command for the next position
	 */
	private void moveTo(Point2D.Double pt, double ra, SIMCMD cmd) {

		multiSCnt++;
		if (multiSCnt == multiSteps) {
			int nextCnt = (multiCnt + 1) % multiNum;
			nextPts.set(multiCnt, pt);
			pt = nextPts.get(nextCnt);
		}

		player.moveTo(pt.x, pt.y, ra, 0, cmd);
	}

	/**
	 * Move (and rotate) the rover to a given position.
	 *
	 * @param x the x coordinate to move to
	 * @param y the y coordinate to move to
	 * @param ra the rover rotation angle
	 * @param cmd the simulation command for the next position
	 */
	private void moveTo(double x, double y, double ra, SIMCMD cmd) {
		player.moveTo(x, y, ra, 0, cmd);
	}

	/**
	 * Check all exploration trees if the given position inside the explored area.
	 *
	 * @param pt the position to check
	 * @return true, if the position is inside the explored area
	 */
	private boolean isMultiExplored(Point2D.Double pt) {

		boolean explored = false;
		for (ExplorationTree t : multiNavTree)
			if (!t.isEmpty())
				explored |= isExplored(t.getRoot(), pt);

		return explored;
	}

	/**
	 * Check if the given position inside the explored area of the given exploration tree.
	 *
	 * @param tree the exploration tree
	 * @param pt the position to check
	 * @return true, if the position is inside the explored area
	 */
	private boolean isExplored(ExplorationTree tree, Point2D.Double pt) {

		boolean explored = tree.getNode().pos.distance(pt) < tree.getNode().r;
		Iterator<ExplorationTree> it = tree.getSubTrees().iterator();
		for (;it.hasNext() && !explored;)
			explored = isExplored(it.next(), pt);

		return explored;
	}

	/**
	 * Helper function to convert an AWT image (used for image manipulation and filtering)
	 * to SWT image data (needed by the GUI to display it).
	 *
	 * @param bufferedImage the AWT image
	 * @return the SWT image data
	 */
	private ImageData convertToSWT(BufferedImage bufferedImage) {

		if (bufferedImage.getColorModel() instanceof DirectColorModel) {
			DirectColorModel colorModel = (DirectColorModel)bufferedImage.getColorModel();
			PaletteData palette = new PaletteData(colorModel.getRedMask(), colorModel.getGreenMask(), colorModel.getBlueMask());
			ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
			for (int y = 0; y < data.height; y++)
				for (int x = 0; x < data.width; x++) {
					int rgb = bufferedImage.getRGB(x, y);
					int pixel = palette.getPixel(new RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
					data.setPixel(x, y, pixel);
					if (colorModel.hasAlpha())
						data.setAlpha(x, y, (rgb >> 24) & 0xFF);
				}
			return data;
		} else if (bufferedImage.getColorModel() instanceof IndexColorModel) {
			IndexColorModel colorModel = (IndexColorModel)bufferedImage.getColorModel();
			int size = colorModel.getMapSize();
			byte[] reds = new byte[size];
			byte[] greens = new byte[size];
			byte[] blues = new byte[size];
			colorModel.getReds(reds);
			colorModel.getGreens(greens);
			colorModel.getBlues(blues);
			RGB[] rgbs = new RGB[size];
			for (int i = 0; i < rgbs.length; i++)
				rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF, blues[i] & 0xFF);
			PaletteData palette = new PaletteData(rgbs);
			ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
			data.transparentPixel = colorModel.getTransparentPixel();
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[1];
			for (int y = 0; y < data.height; y++)
				for (int x = 0; x < data.width; x++) {
					raster.getPixel(x, y, pixelArray);
					data.setPixel(x, y, pixelArray[0]);
				}
			return data;
		}
		return null;
	}
}
