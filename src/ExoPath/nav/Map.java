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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import javax.imageio.ImageIO;

import org.newdawn.slick.util.pathfinding.PathFinderMap;

import com.jhlabs.image.ConvolveFilter;
import com.jhlabs.image.MaximumFilter;

import exopath.nav.NavigationTask.Position;

/**
 * The terrain map for the rover environment.
 * The path finder uses this map object to check traversability of the map cells.
 */
public class Map implements PathFinderMap {

	/** The slope terrain parameter index. */
	public static final int SLP = 0;

	/** The ground terrain parameter index. */
	public static final int GRD = 1;

	/** The obstacle terrain parameter index. */
	public static final int OBS = 2;

	/** The hazard terrain parameter index. */
	public static final int HAZ = 3;

	/** The valid terrain parameter index. */
	public static final int VAL = 4;

	/** The map width. */
	private final int width;

	/** The map height. */
	private final int height;

	/** The grid that holds the values for all the terrain parameters. */
	private final byte[][][] terrain;

	/** Indicator if a given tile has been visited during the search. */
	private boolean[][] visited;

	/** The range point index to map the terrain grid to the environment data */
	private final int[][] rangePtIdx;

	/** The actual position. */
	private Point position;

	/** A preallocated image for filtering. */
	BufferedImage filtImg1;

	/** Another preallocated image for filtering. */
	BufferedImage filtImg2;

	/**
	 * Create and init a new map.
	 *
	 * @param width the map width
	 * @param height the map height
	 */
	public Map(int width, int height) {

		this.width = width;
		this.height = height;

		terrain = new byte[width][height][5];
		rangePtIdx = new int[width][height];

		position = new Point(width/2, height/2);

		filtImg1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		filtImg2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	}

	/**
	 * Sets the data and update the grid values.
	 * The filter steps are done for the different terrain parameters.
	 *
	 * @param rangePoints the new data
	 */
	public void setData(List<Position> rangePoints) {

//		saveRangeMap(rangePoints);

		// clear old data
		clearImages();
		clearTerrain();
		clearVisited();

		// set the obstacle values to an image for dilation filter
		for (Position p : rangePoints)
			filtImg1.getRaster().setSample(p.mapX, p.mapY, 1, p.obs);

		// the filter op
		MaximumFilter mf = new MaximumFilter();
		mf.filter(filtImg1, filtImg2);

		// set all valid points and the hazard value to an image for blur filter
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				filtImg2.getRaster().setSample(x, y, 0, 2); // init the valid index with 2

		for (Position p : rangePoints) {
			filtImg2.getRaster().setSample(p.mapX, p.mapY, 0, 0); // mark as valid by 0
			filtImg2.getRaster().setSample(p.mapX, p.mapY, 2, p.haz); // set hazard
		}

		// the filter op
		float[] kernel = new float[]{
				0.3f, 0.3f, 0.3f,
				0.3f,    1, 0.3f,
				0.3f, 0.3f, 0.3f,
		};
	    ConvolveFilter cf = new ConvolveFilter(kernel); // convolve kernel for blurring
	    cf.setEdgeAction(ConvolveFilter.ZERO_EDGES);
        cf.filter(filtImg2, filtImg1);

        // fill the terrain grid data
		int[] pix = new int[3];
        for (int i = 0; i < rangePoints.size(); i++) {
        	Position p = rangePoints.get(i);
        	filtImg1.getRaster().getPixel(p.mapX, p.mapY, pix);
        	terrain[p.mapX][p.mapY][SLP] = (byte)p.slp; // slope value
        	terrain[p.mapX][p.mapY][GRD] = (byte)p.grd; // ground value
        	terrain[p.mapX][p.mapY][OBS] = (byte)pix[1]; // filtered obstacle value
        	terrain[p.mapX][p.mapY][HAZ] = (byte)pix[2]; // filtered hazard value
        	// after filtering points are valid with the value 0,
        	// they will be marked with 2, 1 means available as range point but filtered out
        	terrain[p.mapX][p.mapY][VAL] = (byte)(pix[0] == 0 ? 2 : 1);
        	rangePtIdx[p.mapX][p.mapY] = i; // remember the range point index
        }

        // workaround for missing data directly for the position
		for (int h = height/2, y = h-4 ; y < h+4; y++)
			for (int w = width/2, x = w-4; x < w+4; x++)
				terrain[x][y][VAL] = 2;
	}

	/**
	 * A helper A helper method to save the range map to a file.
	 *
	 * @param rangePoints the range points
	 */
	@SuppressWarnings("unused")
	private void saveRangeMap(List<Position> rangePoints) {

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (Position p : rangePoints) {
			try {
				img.getRaster().setPixel(p.mapX, p.mapY,
						new int[]{p.slp, p.grd == 0 ? 1 : p.grd, p.obs, p.haz});
			} catch (Exception e) {}
		}

		NavigationTask navigation = NavigationTask.getTask();
		DecimalFormat df = new DecimalFormat("0000");
		File imgFile = new File("rangemap-" + df.format(navigation.rangeTS) + ".png");
		try {
			ImageIO.write(img, "png", imgFile);
		} catch (IOException e) {}
	}

	/**
	 * Clear images by setting pixel values to 0.
	 */
	private void clearImages() {

		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				for (int b = 0; b < 3; b++) {
					filtImg1.getRaster().setSample(x, y, b, 0);
					filtImg2.getRaster().setSample(x, y, b, 0);
				}

	}

	/**
	 * Clear terrain by setting parameter values to 0.
	 */
	private void clearTerrain() {

		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++) {
				terrain[x][y][0] = 0;
				terrain[x][y][1] = 0;
				terrain[x][y][2] = 0;
				terrain[x][y][3] = 0;
				terrain[x][y][4] = 0;
				rangePtIdx[x][y] = -1;
			}
	}

	/**
	 * Clear the array marking which tiles have been visited by the path finder.
	 */
	public void clearVisited() {
		if (visited != null) {
			for (int x = 0; x < getWidth(); x++)
				for (int y = 0; y < getHeight(); y++)
					visited[x][y] = false;
		}
	}

	/**
	 * Returns if coordinate was visited by the path finder.
	 * For Dijkstra this means, the point is reachable.
	 *
	 * @param x the x coordinate of the cell
	 * @param y the y coordinate of the cell
	 * @return true, if cell was visited
	 * @see PathFinderMap#visited(int, int)
	 */
	public boolean visited(int x, int y) {
		if (visited == null)
			return false;
		return visited[x][y];
	}

	/**
	 * Get the terrain parameter at a given location.
	 *
	 * @param x The x coordinate of the terrain cell to retrieve
	 * @param y The y coordinate of the terrain cell to retrieve
	 * @param prop the property/parameter of the terrain cell
	 * @return The parameter value at the given location
	 */
	public int getTerrain(int x, int y, int prop) {
		return terrain[x][y][prop] & 0xff;
	}

	/**
	 * Gets the range point index related to the given location.
	 *
	 * @param x the x coordinate of the cell
	 * @param y the y coordinate of the cell
	 * @return the index of the range point array this cell data is based on
	 */
	public int getRangePtIdx(int x, int y) {
		return rangePtIdx[x][y];
	}

	/**
	 * Check, if this is the actual position.
	 *
	 * @param x The x coordinate of the cell to check
	 * @param y The y coordinate of the cell to check
	 * @return true, if the given location is our position, false otherwise
	 */
	public boolean isPosition(int x, int y) {
		return x == position.x && y == position.y;
	}

	/**
	 * Set the position within the map
	 *
	 * @param x The x coordinate of the location where the position should be set
	 * @param y The y coordinate of the location where the position should be set
	 */
	public void setPosition(int x, int y) {
		position = new Point(x, y);
	}

	/**
	 * Gets the x coordinate of the actual position
	 */
	public int getPosX() {
		return position.x;
	}

	/**
	 * Gets the y coordinate of the actual position
	 */
	public int getPosY() {
		return position.y;
	}

	/**
	 * Check if the given map cell is blocked (not traversable).
	 *
	 * @param x the x coordinate of the cell
	 * @param y the y coordinate of the cell
	 * @return true, if the cell is blocked/not traversable
	 * @see PathFinderMap#blocked(int, int)
	 */
	public boolean blocked(int x, int y) {

		return getTerrain(x, y, OBS) > 0 ||  // is there an obstacle
			   getTerrain(x, y, VAL) < 2 ||  // is the cell entry valid
			   getTerrain(x, y, GRD) > 30 || // is ground value smaller than a given max
			   getTerrain(x, y, SLP) > 15;   // is the slope smaller than a given max
	}

	/**
	 * Gets the cost for moving over the given cell.
	 *
	 * @param sx the x coordinate of the source cell
	 * @param sy the y coordinate of the source cell
	 * @param tx the x coordinate of the target cell
	 * @param ty the y coordinate of the target cell
	 * @return the cost value for the movement
	 * @see PathFinderMap#getCost(int, int, int, int)
	 */
	public float getCost(int sx, int sy, int tx, int ty) {

		float cost = 0;

		float slope = getTerrain(tx, ty, SLP); // cost value for slope
		if (slope > 5)
			cost += slope / 5;

		float ground = getTerrain(tx, ty, GRD); // cost value for the ground value
		if (ground > 5)
			cost += ground / 10;

		float hazard = getTerrain(tx, ty, HAZ); // cost value for the hazard
		cost += hazard / 128;

		return cost; // the overall cell movement cost
	}

	/**
	 * Gets the map height.
	 *
	 * @return the map height
	 * @see PathFinderMap#getHeight()
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Gets the map width.
	 *
	 * @return the map width
	 * @see PathFinderMap#getWidth()
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Method for the path finder to mark the given cell as visited.
	 *
	 * @param x the x coordinate of the cell
	 * @param y the y coordinate of the cell
	 * @see PathFinderMap#pathFinderVisited(int, int)
	 */
	public void pathFinderVisited(int x, int y) {
		if (visited == null)
			visited = new boolean[width][height];

		visited[x][y] = true;
	}
}
