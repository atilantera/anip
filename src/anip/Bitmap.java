package anip;

/**
 * Represents bitmap images.
 * 
 * @author Artturi Tilanterä
 *
 */

public class Bitmap {
    
    /**
     * Width of the image in pixels.
     */
    public short width;
    
    /**
     * Height of the image in pixels.
     */
    public short height;
    
    /**
     * Color depth in bytes per pixel.
     */
    public int depth;
    
    /**
     * Pixel data. Pixels are adviced to be stored from left to right,
     * and top to down, all color components together if there are
     * any. For example, three red-green-blue component pixel data
     * would be in order RGB RGB RGB. Every short value is actually
     * an <b>unsigned</b> byte, meaning its value is 0..255. That's
     * because Java doesn't have an "unsigned byte" type.
     */
    public short[] pixels; 

    /**
     * Constructs new bitmap with default size of 0 x 0 pixels and
     * depth of 3. Image data is null.
     */
    public Bitmap() {
        width = 0;
        height = 0;
        depth = 3;
        pixels = null;
    }    
    
    /**
     * Resizes the image reallocating memory for pixel data.
     * 
     * @param newWidth desired width of the image.
     * @param newHeight desired height of the image.
     * @param newDepth desired depth of the image in bytes.
     */
    public void resize(int newWidth, int newHeight,
            int newDepth) {
        if (newWidth > 1 && newHeight > 1 &&
            newWidth < 32768 && newHeight < 32768 && newDepth > 0) {
            width = (short)newWidth;
            height = (short)newHeight;
            depth = newDepth;
            pixels = new short[width * height * depth];
        }
    }
}
