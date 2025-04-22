package anip.mediancut;

/**
 * Represents a single RGB color and number of its occurrences in some image.
 * 
 * @author Artturi Tilanterä
 */

public class ColorOccurrence {
    
    /**
     * Value of the red component, 0..255.
     */
    public short r;

    /**
     * Value of the green component, 0..255.
     */
    
    public short g;    
    
    /**
     * Value of the blue component, 0..255.
     */    
    public short b;
    
    /**
     * Count of this color value in some image.
     */
    public int count;
    
    public ColorOccurrence(short r, short g, short b, int count) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.count = count;
    }
}
