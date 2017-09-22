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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * A GUI part (SWT Canvas), which can be extended to display images within views.
 */
public class ImageCanvas extends Canvas {

	/** The source image. */
	protected Image sourceImage;

	/** The screen image, transformed to fit the view area. */
	protected Image screenImage;

	/** The x-offset of the image to center it within the view. */
	protected int dx;

	/** The y-offset of the image to center it within the view. */
	protected int dy;

	/**
	 * Instantiates a new image canvas given its parent composite.
	 * The default constructor.
	 *
	 * @param parent the parent composite
	 */
	public ImageCanvas(final Composite parent) {
		this(parent, SWT.NONE);
	}

	/**
	 * Instantiates a new image canvas given its parent composite
     * and a style value describing its behavior and appearance.
     *
     * @see org.eclipse.swt.widgets.Canvas
	 *
	 * @param parent the parent
	 * @param style the style
	 */
	public ImageCanvas(final Composite parent, int style) {

		super(parent, style);

		// resize the image when the GUI is resized
		addControlListener(new ControlAdapter() { /* resize listener. */
			@Override
			public void controlResized(ControlEvent event) {
				fitCanvas();
			}
		});
		// draw the image when the GUI needs to repaint
		addPaintListener(new PaintListener() {
			public void paintControl(final PaintEvent event) {
				paint(event.gc);
			}
		});
	}

	@Override
	public void dispose() {

		// destroy all resources
		if (sourceImage != null && !sourceImage.isDisposed()) {
			sourceImage.dispose();
		}
		if (screenImage != null && !screenImage.isDisposed()) {
			screenImage.dispose();
		}
	}

	/**
	 * Draws the image (if given) to the given graphics context or clears the area.
	 *
	 * @param gc the graphic context
	 */
	protected void paint(GC gc) {

		if (screenImage != null) {
			gc.drawImage(screenImage, dx, dy);
		} else {
			Rectangle clientRect = getClientArea(); /* Canvas' painting area */
			gc.setClipping(clientRect);
			gc.fillRectangle(clientRect);
		}
	}

	/**
	 * Fit the image canvas to the GUI area and create a new screen image with
	 * the new size.
	 */
	synchronized
	public void fitCanvas() {

		if (sourceImage == null)
			return;

		// the original image dimensions
		int srcImgWidth = sourceImage.getBounds().width;
		int srcImgHeight = sourceImage.getBounds().height;
		Rectangle destRect = getClientArea();

		// the scale factors
		double sx = (double) destRect.width / srcImgWidth;
		double sy = (double) destRect.height / srcImgHeight;
		double scale = Math.min(sx, sy);

		if (scale <= 0)
			return;

		// free old image
		if (screenImage != null)
			screenImage.dispose();

		// create a new image with the scaled size
		int screenImgWidth = (int)(srcImgWidth * scale);
		int screenImgHeight = (int)(srcImgHeight * scale);
		screenImage = new Image(getDisplay(), screenImgWidth, screenImgHeight);

		// create and init a graphic context belonging to the image
		GC gc = new GC(screenImage);
		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);
		// and draw a scaled copy of the source image to the screen image
		gc.drawImage(sourceImage, 0, 0, srcImgWidth, srcImgHeight, 0, 0, screenImgWidth, screenImgHeight);
		gc.dispose();

		// update the offset values for a centered view
		dx = (int)(0.5 * (destRect.width - screenImgWidth));
		dy = (int)(0.5 * (destRect.height - screenImgHeight));
	}

	/**
	 * Sets the image to display within this canvas.
	 *
	 * @param img the image to display
	 */
	synchronized
	public void setImage(Image img) {
		if (sourceImage != null)
			sourceImage.dispose();

		sourceImage = img;

		// hook the screen image drawing to the display thread
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (!isDisposed()) {
					fitCanvas(); // create a copy of the source image with the screen dimensions
					redraw();    // trigger a draw event, which results in calling the paint method
				}
			}
		});
	}

	/**
	 * Gets the image which is displayed by this canvas.
	 *
	 * @return the image
	 */
	public Image getImage() {
		return sourceImage;
	}
}
