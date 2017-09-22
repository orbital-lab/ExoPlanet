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

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import exopath.nav.NavigationTask;
import exopath.nav.NavigationTask.NavigationListener;

/**
 * The navigation view part which contains a image canvas to display
 * the navigation map image (the results of the path planner task) within the GUI.
 */
public class NavView extends ViewPart {

	/** The navigation component. */
	private final NavigationTask navigation = NavigationTask.getTask();

	/** The image canvas. */
	private ImageCanvas imageCanvas;

	@Override
	public void createPartControl(final Composite parent) {

		imageCanvas = new ImageCanvas(parent);

		// refresh the image when a new navigation map is available
		// therefore we register a listener to the navigation component to become notified
		// when a new navigation step is finished by the path planner
		navigation.addListener(new NavigationListener() {
			public void updateData() {
				if (parent.isDisposed())
					return;
				// hook the image modification to the display thread
				parent.getDisplay().asyncExec(new Runnable() {
					public void run() {
						// get the navigation / range map
						ImageData rangeData = navigation.getRangeMap();
						if (parent.isDisposed())
							return;
						// create a new (upscaled) image from it
						imageCanvas.setImage(new Image(parent.getDisplay(),
								rangeData.scaledTo(4*rangeData.width, 4*rangeData.height)));
					}
				});
			}
		});
	}

	@Override
	public void setFocus() {
		imageCanvas.setFocus();
	}

	@Override
	public void dispose() {
		imageCanvas.dispose();
		super.dispose();
	}
}
