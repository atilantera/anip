package anip.gui;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import anip.Bitmap;

/**
 * A rectangular area that can display video frame by frame.
 * 
 * @author Artturi Tilanterä
 */

public class VideoWindow extends JPanel {

    private BufferedImage bi;
    private int[] rgbArray;
    private int width;
    private int height;
    private int bufferWidth;
    private int bufferHeight;
    private int scanlineLength;

    /**
     * Constructs new video display component.
     * 
     * @param videoWidth width of video display.
     * @param videoHeight height of video display.
     * @param bufferWidth width of video image buffer.
     * @param bufferHeight height of video image buffer.
     */
    public VideoWindow(int videoWidth, int videoHeight,
        int bufferWidth, int bufferHeight) {
        this.width = videoWidth;
        this.height = videoHeight;
	this.bufferWidth = bufferWidth;
	this.bufferHeight = bufferHeight;
	rgbArray = new int[bufferWidth * this.bufferHeight];
	bi = new BufferedImage(bufferWidth, bufferHeight,
	    BufferedImage.TYPE_INT_RGB);
    }
    
    public void paintComponent(Graphics g) {
        if (this.bi != null) {
            g.drawImage(bi, 0, 0, null);
                    
        }
    }
    
    public void paint(Graphics g) {
        g.drawImage(bi, 0, 0, null);
    }
       
    /**
     * Updates the video display.
     *
     * @param buffer image buffer whose size is bufferWidth * bufferHeight
     * as in parameters of the constructor PlayerWindow.PlayerWindow().
     * Every integer value contains color of a single pixel in RGB color
     * space. Value = r * 65536 + g * 256 + b. Pixels are listed in
     * horizontal scanlines from left to right, and scanlines from top to
     * bottom.
     */    
    public void setImage(int[] imageBuffer) {
        if (this.bi != null) {
            bi.setRGB(0, 0, bufferWidth, bufferHeight, imageBuffer, 0,
                    bufferWidth);
	    this.repaint();
        }
    }
    
    public Dimension getPreferredSize() {
        return new Dimension(this.width, this.height);
    }
}
