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

import javaclient3.structures.camera.PlayerCameraData;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Composite;

/**
 * An extended ImageCanvas which creates a image from
 * the Player camera data structure.
 */
public class CameraCanvas extends ImageCanvas {

	/**
	 * Instantiates a new camera canvas given its parent composite.
	 *
	 * @param parent the parent composite
	 */
	public CameraCanvas(Composite parent) {
		super(parent);
	}

	/**
	 * Creates a new image based on the given Player camera data.
	 *
	 * @param camData the Player camera data
	 */
	synchronized
	protected void setImageData(PlayerCameraData camData) {

		// define the image color interpretation of the given pixel bits
		PaletteData palette = new PaletteData(0xFF , 0xFF00 , 0xFF0000);
		// creates a image data structure based on the camera data
		ImageData imageData = new ImageData(
				camData.getWidth(), camData.getHeight(), camData.getBpp(), palette, 4, camData.getImage());

		// instantiate a new image and set it to display
		// the image will be disposed by the image canvas
		setImage(new Image(getDisplay(), imageData));
	}
}
