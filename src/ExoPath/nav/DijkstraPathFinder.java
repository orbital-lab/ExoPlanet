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

import org.newdawn.slick.util.pathfinding.Node;
import org.newdawn.slick.util.pathfinding.Path;
import org.newdawn.slick.util.pathfinding.PathFinder;
import org.newdawn.slick.util.pathfinding.PathFinderMap;
import org.newdawn.slick.util.pathfinding.SortedNodeList;

/**
 * A path finder implementation that uses the Dijkstra algorithm
 * to determine a path.
 */
public class DijkstraPathFinder extends PathFinder {

	/**
	 * The extended Dijkstra node to save a cost value.
	 */
	private class DNode extends Node {

		@Override
		protected float getSortValue() {
			return cost;
		}
	}

	/** The init flag. If its true, the calculation is finished for a
	 * given start point and all paths are available*/
	private boolean init = false;

	/** The set of nodes that we do not yet consider fully searched. */
	private final SortedNodeList open = new SortedNodeList();

	/**
	 * Create a path finder.
	 *
	 * @param map The map to be searched
	 * @param maxSearchDistance The maximum depth we'll search before giving up
	 * @param allowDiagMovement True if the search should try diagonal movement
	 */
	public DijkstraPathFinder(PathFinderMap map, int maxSearchDistance, boolean allowDiagMovement) {

		super(map, maxSearchDistance, allowDiagMovement);

		// create the nodes
		for (int x = 0; x < map.getWidth(); x++)
			for (int y = 0; y < map.getHeight(); y++)
				setNode(x, y, new DNode());
	}

	/**
	 * The path finder init method for a given map.
	 * The start point from the map is used.
	 *
	 * @param map the map
	 */
	public void initPathFinder(PathFinderMap map) {

		this.map = map;

		// init all nodes
		for (int x = 0; x < map.getWidth(); x++)
			for (int y = 0; y < map.getHeight(); y++) {
				Node node = getNode(x, y);
				node.parent = null;
				node.closed = false;
				node.cost = Float.MAX_VALUE;
				node.dist = 0;
			}

		initPathFinder(map.getPosX(), map.getPosY());
	}

	/**
	 * The Dijkstra algorithm to get all paths from the given source
	 *
	 * @param sx the source x coordinate
	 * @param sy the source y coordinate
	 */
	private void initPathFinder(int sx, int sy) {

		// initial state
		if (getNode(sx, sy) == null)
			return;

		getNode(sx, sy).cost = 0;
		getNode(sx, sy).depth = 0;
		open.clear();
		open.add(getNode(sx, sy));

		int maxDepth = 0;
		while ((maxDepth < maxSearchDistance) && (open.size() != 0)) {

			// pull out the first node in our open list
			Node node = open.first();
			open.remove(node);
			node.closed = true;

			map.pathFinderVisited(node.x, node.y);

			// search through all the neighbors of the current node
			for (int x = -1; x < 2; x++) {
				for (int y = -1; y < 2; y++) {

					// not a neighbor, its the current tile
					if ((x == 0) && (y == 0))
						continue;

					// if we're not allowing diagonal movement
					if (!allowDiagMovement) {
						if ((x != 0) && (y != 0))
							continue;
					}

					// determine the location of the neighbor and evaluate it
					int xp = x + node.x;
					int yp = y + node.y;

					if (isValidLocation(sx, sy) && isValidLocation(xp, yp)) {
						Node neighbour = getNode(xp, yp);
						if (neighbour.closed)
							continue;

						float dist = getDist(node.x, node.y, xp, yp);
						float cost = node.cost + dist + map.getCost(node.x, node.y, xp, yp);
						if (cost < neighbour.cost) {
							maxDepth = Math.max(maxDepth, neighbour.setParent(node));
							neighbour.cost = cost;
							neighbour.dist = node.dist + dist;
						}
						if (!open.contains(node))
							open.add(neighbour);
					}
				}
			}
		}
	}

	/**
	 * Returns the path to a target location.
	 * The start point from the map is used.
	 * From the initialized Dijkstra node list the path can be evaluated by following
	 * the nodes parent references.
	 *
	 * @param tx the target x coordinate
	 * @param ty the target y coordinate
	 * @return the path
	 */
	public Path findPath(int tx, int ty) {

		return findPath(map.getPosX(), map.getPosY(), tx, ty);
	}

	/**
	 * Returns the path from a source to a target location.
	 * From the initialized Dijkstra node list the path can be evaluated by following
	 * the nodes parent references.
	 *
	 * @param sx the source x coordinate
	 * @param sy the source y coordinate
	 * @param tx the target x coordinate
	 * @param ty the target y coordinate
	 * @return the path
	 * @see PathFinder#findPath(int, int, int, int)
	 */
	@Override
	public Path findPath(int sx, int sy, int tx, int ty) {

		// easy first check, if the destination is blocked, we can't get there
		if (map.blocked(tx, ty))
			return null;

		// run Dijkstra algorithm if not done yet
		if (!init) { //TODO init for specific sx sy
			initPathFinder(sx, sy);
			init = true;
		}

		// if the target wasn't reachable there is no path. Just return null
		if (getNode(tx, ty).parent == null)
			return null;

		// At this point we've definitely found a path so we can uses the parent
		// references of the nodes to find out way from the target location back
		// to the start recording the nodes on the way.
		Path path = new Path();
		Node target = getNode(tx, ty);
		while (target != getNode(sx, sy)) {
			path.prependStep(target.x, target.y);
			target = target.parent;
		}
		path.prependStep(sx,sy);

		return path;
	}

	/**
	 * Gets the path cost value of a given cell coordinate.
	 *
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @return the path cost value
	 */
	public float getPathCost(int x, int y) {
		return getNode(x, y).cost;
	}

	/**
	 * Gets the path distance value to a given cell.
	 *
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @return the path distance
	 */
	public float getPathDist(int x, int y) {
		return getNode(x, y).dist;
	}
}
