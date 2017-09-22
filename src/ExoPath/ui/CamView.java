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

import java.text.DecimalFormat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import exopath.client.PlayerTask;
import exopath.client.PlayerTask.Device;
import exopath.client.PlayerTask.PlayerListener;

/**
 * The camera view part which contains a camera canvas to display
 * the rover camera image within the GUI.
 */
public class CamView extends ViewPart {

	/** The Player main component */
	private final PlayerTask player = PlayerTask.getTask();

	/** The camera canvas. */
	private CameraCanvas cameraCanvas;

	@Override
	public void createPartControl(Composite parent) {

		cameraCanvas = new CameraCanvas(parent);

		// refresh the image when new camera data are available
		// therefore we register a listener for Player events at the PlayerTask
		player.addListener(new PlayerListener() {
			public void updateData(Device dev) {    // if there are new data available
				if (dev == Device.CAM1) {           // check if the new data device is the rover cam
					if (cameraCanvas.isDisposed())
						return;
					cameraCanvas.setImageData(player.getCameraInterface1().getData()); // update the image
//					saveImg();
				}
			}
		});
	}

	@Override
	public void setFocus() {
		cameraCanvas.setFocus();
	}

	@Override
	public void dispose() {
		cameraCanvas.dispose();
		super.dispose();
	}

	/**
	 * A helper method to save the rover cam image to a file.
	 */
	@SuppressWarnings("unused")
	private void saveImg() {

		ImageLoader imageLoader = new ImageLoader();
		imageLoader.data = new ImageData[] {cameraCanvas.getImage().getImageData()};
		String ts = new DecimalFormat("0000").format(player.getCameraInterface1().getTimestamp());
		String imgFile = "rovmap-" + ts + ".png";
		imageLoader.save(imgFile, SWT.IMAGE_PNG);
	}
}
