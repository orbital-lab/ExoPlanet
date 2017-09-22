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

package exopath.ui;

import java.awt.geom.Point2D;
import java.io.File;
import java.text.DecimalFormat;

import javaclient3.structures.position2d.PlayerPosition2dData;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import uky.article.imageviewer.views.ImageView;
import uky.article.imageviewer.views.SWT2Dutil;
import uky.article.imageviewer.views.SWTImageCanvas;
import exopath.client.ConfigDataInterface;
import exopath.client.ConfigDataInterface.MapDim;
import exopath.client.PlayerTask;
import exopath.client.PlayerTask.Device;
import exopath.client.PlayerTask.PlayerListener;
import exopath.client.PlayerTask.SIMCMD;
import exopath.nav.ExplorationTree;
import exopath.nav.NavigationTask;
import exopath.nav.NavigationTask.NavigationListener;

/**
 * The map view part which contains a map image canvas to display
 * a map of the whole area within the GUI.
 */
public class MapView extends ImageView {

	/** The Player main component */
	private final NavigationTask navigation = NavigationTask.getTask();

	@Override
	public void createPartControl(Composite frame) {

		imageCanvas = new MapImgCanvas(frame);

		// Get the map image from a file from the applications workbench directory
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				IPath workspace = Platform.getLocation();
				File map = workspace.append("maps\\terrain-vis-part.jpg").toFile();
				imageCanvas.loadImage(map.getAbsolutePath());
				imageCanvas.fitCanvas();
			}
		});
	}

	/**
	 * The MapImgCanvas is an extended SWTImageCanvas to provide big images to display.
	 * The library class SWTImageCanvas has also actions for zooming and resizing the image.
	 *
	 * In exploration mode the exploration tree is displayed as a map image overlay. The new
	 * positions are calculated by the NavigationTask. The MapCanvas can also react on mouse
	 * and keyboard events to move the simulated rover manually (this feature is mostly for
	 * debugging purposes).
	 */
	private class MapImgCanvas extends SWTImageCanvas {

		/** The Player main component */
		private final PlayerTask player = PlayerTask.getTask();

		/** The map dimensions. */
		private MapDim mapDim = new MapDim();

		/** The actual position within the map. Just used for manual movement */
		private Point2D.Double curWorldPos;

		/** The time stamp for the actual position. */
		double posTS;

		/** A update flag to indicate new data received. */
		boolean update;

		/** A moving flag to indicate manual movement command is processed. */
		private boolean moving = false;

		/**
		 * Instantiates a map image canvas given its parent composite.
		 *
		 * @param parent the parent
		 */
		public MapImgCanvas(Composite parent) {

			super(parent);

			// refresh the actual position or map dimensions when new data is available
			// therefore we register a listener for Player events at the PlayerTask
			player.addListener(new PlayerListener() {
				public void updateData(Device dev) {
					switch (dev) {
					case CFG: // check if new data comes from the config device
						// set the map dimension based on the device interface data
						setMapDim(player.getConfigInterface().getMapData());
						break;
					case P2D: // check if new data comes from the 2d-position device
						// get the position from the device interface
						posTS = player.getPositionInterface().getTimestamp();
						PlayerPosition2dData posData = player.getPositionInterface().getData();
						setPosition(posData.getPos().getPx(), posData.getPos().getPy());
						moving = false; // manual moving is done, a new move command can be processed
						break;
					default:
						break;
					}
				}
			});

			// register a listener to the navigation component to become notified
			// when the exploration tree was modified
			navigation.addListener(new NavigationListener() {
				public void updateData() {
					update = true;
					doRedraw(); // redraw the image and the exploration tree overlay
				}
			});

			// check if map dimensions has been arrived before this object was created
			ConfigDataInterface cfgIf = player.getConfigInterface();
			if (cfgIf != null)
				setMapDim(player.getConfigInterface().getMapData());

			// add mouse listener for manual movement, react on double click
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					handleMouse(e);
				}
			});

			// add mouse listener to provide zooming by the mouse wheel
			addMouseWheelListener(new MouseWheelListener() {
				public void mouseScrolled(MouseEvent e) {
		            if (e.count > 0)
		            	zoomIn();
		            else
		            	zoomOut();
				}
			});

			// add keyboard listener for manual movement
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					handleKey(e);
				}
			});
		}

		/**
		 * The hook to the ImageCanvas paint method to draw custom overlays
		 * (like the navigation tree on the map image).
		 *
		 * @param screenGC the screen image graphic context
		 */
		@Override
		protected void paintToScreen(GC screenGC) {

			// for each rover
			for (int i = 0; i < navigation.multiNum; i++) {
				switch (i) {
				// set circle color for the exploration tree for multiple rovers
				case 0: screenGC.setBackground(getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN)); break;
				case 1: screenGC.setBackground(getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE)); break;
				case 2: screenGC.setBackground(getDisplay().getSystemColor(SWT.COLOR_DARK_RED)); break;
				case 3: screenGC.setBackground(getDisplay().getSystemColor(SWT.COLOR_DARK_MAGENTA)); break;
				// add more colors for using more rovers
				default: break;
				}
				// get the rovers navigation tree
				ExplorationTree navTree = navigation.multiNavTree.get(i);
				if (!navTree.isEmpty()) {
					paintTreeArea(screenGC, navTree.getRoot()); // draw explored areas
					paintTreeLine(screenGC, navTree.getRoot()); // draw connection lines

					// draw a dot to the actual position
					paintDot(screenGC, navTree.getNode().pos.x, navTree.getNode().pos.y);
				}
				if (curWorldPos != null) {
					paintDot(screenGC, curWorldPos.x, curWorldPos.y);
				}
			}

//			saveMap(screenImage);
		}

		/**
		 * Paint a dot to the given map coordinates (e.g. for the actual rover position)
		 *
		 * @param gc the graphic context
		 * @param worldX the world map x coordinate
		 * @param worldY the world map y coordinate
		 */
		private void paintDot(GC gc, double worldX, double worldY) {

			int dot = 5;
			gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_YELLOW));
			// transform the location to a image point
			Point pt = worldToMap(worldX, worldY);
			if (pt == null)
				return;
			// scale the point to the screen image
			Point screen = SWT2Dutil.transformPoint(getTransform(), pt);
			// and draw a point
			gc.fillRectangle(screen.x - dot/2, screen.y - dot/2, dot, dot);
		}

		/**
		 * Paint the circles of the mean explored area for all tree positions.
		 *
		 * @param gc the graphic context
		 * @param tree the exploration tree data
		 */
		private void paintTreeArea(GC gc, ExplorationTree tree) {

			// the position of the actual tree node
			double worldPosX = tree.getNode().pos.x;
			double worldPosY = tree.getNode().pos.y;

			// the radius of the actual tree node
			double r = tree.getNode().r;

			// transform the world map location to the corresponding map image point
			Point pos = worldToMap(tree.getNode().pos.x, tree.getNode().pos.y);
			// get the upper left point needed to draw a circle centered in the position point
			Point upleft = worldToMap(worldPosX - r, worldPosY - r);

			if (pos == null || upleft == null)
				return;

			// transform the map image point of the position to the screen image point
			Point p = SWT2Dutil.transformPoint(getTransform(), pos);
			// scale the map image point of the upper left circle boundary to the screen image point
			Point ul = SWT2Dutil.transformPoint(getTransform(), upleft);
			// draw the circle with the radius r in scale with the screen image
			gc.fillOval(ul.x, ul.y, 2*(p.x-ul.x), 2*(p.y-ul.y));

			// do the drawing for the subtrees recursively
			for (ExplorationTree subt : tree.getSubTrees())
				paintTreeArea(gc, subt);
		}

		/**
		 * Paints the connection lines of the exploration tree.
		 *
		 * @param gc the graphic context
		 * @param tree the exploration tree data
		 */
		private void paintTreeLine(GC gc, ExplorationTree tree) {

			// the position of the actual tree node within the map image
			Point pos = worldToMap(tree.getNode().pos.x, tree.getNode().pos.y);
			if (pos == null)
				return;

			// transform the map image point to the screen image point
			Point p = SWT2Dutil.transformPoint(getTransform(), pos);

			// set color
			gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_DARK_YELLOW));
			gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_YELLOW));

			// draw a dot for the position
			//gc.fillRectangle(p.x - dot/2, p.y - dot/2, dot, dot);

			// draw the connection line between the position of the actual tree node and its parent position
			if (tree.getParent() != null) {
				ExplorationTree parent = tree.getParent();
				Point pos2 = worldToMap(parent.getNode().pos.x, parent.getNode().pos.y);
				if (pos != null) {
					Point p2 = SWT2Dutil.transformPoint(getTransform(), pos2);
					gc.drawLine(p.x, p.y, p2.x, p2.y);
				}
			}

			// do the drawing for the subtrees recursively
			for (ExplorationTree subt : tree.getSubTrees())
				paintTreeLine(gc, subt);
		}

		/**
		 * Trigger a redraw event by a display thread hook.
		 */
		private void doRedraw() {
			if (!isDisposed()) getDisplay().asyncExec(new Runnable() {
				public void run() {
					if (!isDisposed())
						redraw();
				}
			});
		}

		/**
		 * A helper method to save the map with painted overlays to a file.
		 *
		 * @param img the map image to save
		 */
		@SuppressWarnings("unused")
		private void saveMap(Image img) {

			if (update && navigation.multiCnt == 0 && navigation.multiSCnt % 10 == 0) {
				ImageLoader imageLoader = new ImageLoader();
				imageLoader.data = new ImageData[] {img.getImageData()};
				DecimalFormat df = new DecimalFormat("0000");
				String imgFile = "map-" + df.format(posTS) + ".png";
				imageLoader.save(imgFile, SWT.IMAGE_PNG);
			}
			update = false;
		}

		/**
		 * A mouse listener to move the rover manually to the selected map location.
		 *
		 * @param e the mouse event data containing the click position
		 */
		private void handleMouse(MouseEvent e) {

			if (!moving) { // when former move commands has been finished
				// get map location from mouse position
				Point map = SWT2Dutil.inverseTransformPoint(getTransform(), new Point(e.x, e.y));
				double[] worldPos = mapToWorld(map);
				if (worldPos == null)
					return;

				moving = true; // set the busy flag until new data arrive
				player.moveTo(worldPos[0], worldPos[1], 0, SIMCMD.MOVE);
			}
		}

		/**
		 * A keyboard listener to move or rotate the rover manually.
		 *
		 * @param e the key event data containing the information about the pressed key
		 */
		private void handleKey(KeyEvent e) {

			if (!moving) { // when former move commands has been finished
				switch (e.keyCode) {
				case SWT.ARROW_LEFT:
					moving = true;
					player.rotate(-Math.PI/10); // rotate left
					break;
				case SWT.ARROW_RIGHT:
					moving = true;
					player.rotate(Math.PI/10); // rotate right
					break;
				case SWT.ARROW_UP:
					moving = true;
					player.forward(5); // move forward
					break;
				case SWT.ARROW_DOWN:
					moving = true;
					player.forward(-5); // move backward
					break;
				default: break;
				}
			}
		}

		/**
		 * Sets or update the map dimensions to transform image points to
		 * map locations. Just needed for manual movement.
		 *
		 * @param mapDim the new map dimension data
		 */
		public void setMapDim(MapDim mapDim) {
			this.mapDim = mapDim;
		}

		/**
		 * Sets the actual position.
		 * This method is only needed for manual movement, in explore mode
		 * we just draw the exploration tree.
		 *
		 * @param x the x
		 * @param y the y
		 */
		public void setPosition(double x, double y) {
			curWorldPos = new Point2D.Double(x, y);
			doRedraw();
		}

		/**
		 * The transformation method from map (world) location to the
		 * corresponding point within the map image.
		 *
		 * @param worldPosX the world map x coordinate
		 * @param worldPosY the world map y coordinate
		 * @return the point within the map image
		 */
		private Point worldToMap(double worldPosX, double worldPosY) {

			Image mapImage = getSourceImage();
			if (mapImage == null || mapImage.isDisposed() || mapDim == null)
				return null;

			int width = mapImage.getBounds().width;
			int height = mapImage.getBounds().height;

			// get the corresponding image point
			double x = (worldPosX - mapDim.xmin) * width / (mapDim.xmax - mapDim.xmin);
			double y = (worldPosY - mapDim.ymin) * height / (mapDim.ymax - mapDim.ymin);

			return new Point((int)x, (int)y);
		}

		/**
		 * The transformation method from image points to the corresponding
		 * map (world) location.
		 *
		 * @param mapPos the point coordinates within the map image
		 * @return the double[] the corresponding location within the world map
		 */
		private double[] mapToWorld(Point mapPos) {

			if (mapPos == null)
				return null;
			Image mapImage = getSourceImage();
			if (mapImage == null || mapImage.isDisposed() || mapDim == null)
				return null;

			int width = mapImage.getBounds().width;
			int height = mapImage.getBounds().height;

			// get the corresponding map point
			double worldPosX = (mapDim.xmax - mapDim.xmin) / width * mapPos.x + mapDim.xmin;
			double worldPosY = (mapDim.ymax - mapDim.ymin) / height * mapPos.y + mapDim.ymin;

			return new double[]{worldPosX, worldPosY};
		}
	}
}
