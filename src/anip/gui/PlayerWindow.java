package anip.gui;

import javax.swing.JFrame;

/**
 * Main window of the anip graphical user interface. Contains
 * sub-window for video display and playback controls.
 * 
 * @author Artturi Tilanterä
 *
 */

public class PlayerWindow extends JFrame {

    private VideoWindow video;
    
    /**
     * Constructs new video player window containing video display
     * area of specified size.
     * 
     * @param videoWidth width of video display sub-window.
     * @param videoHeight height of video display sub-window.
     * @param bufferWidth width of video image buffer.
     * @param bufferHeight height of video image buffer.
     */
    
    public PlayerWindow(int videoWidth, int videoHeight, int
	bufferWidth, int bufferHeight) {
        super("anip");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        video = new VideoWindow(videoWidth, videoHeight, bufferWidth,
	    bufferHeight);
        this.getContentPane().add(video);        
    }

    /**
     * Updates the video display in the window.
     *
     * @param buffer image buffer whose size is bufferWidth * bufferHeight
     * as in parameters of the constructor PlayerWindow.PlayerWindow().
     * Every integer value contains color of a single pixel in RGB color
     * space. Value = r * 65536 + g * 256 + b. Pixels are listed in
     * horizontal scanlines from left to right, and scanlines from top to
     * bottom.
     */
    public void drawImage(int[] buffer) {
        video.setImage(buffer);
    }

}
