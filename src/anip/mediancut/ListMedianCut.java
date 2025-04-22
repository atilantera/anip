package anip.mediancut;

import java.util.ArrayList;
import java.util.Iterator;
import anip.Bitmap;

/**
 * Functionality for creating an optimized, indexed color palette
 * from a source image by median cut method. This version handles
 * color occurrences as a list. Suitable for colors with few colors,
 * say, some hundreds of.
 * 
 * @author Artturi Tilanterä
 */

public class ListMedianCut extends MedianCut {
    
    private ArrayList<ColorOccurrence> occurrences;
    private boolean useAlternativeInterface;
    
    /**
     * Constructs a new ListMedianCut.
     */
    public ListMedianCut() {
        occurrences = new ArrayList<ColorOccurrence>();
        useAlternativeInterface = false;
    }
    
    /**
     * Creates an indexed palette for a 24-bit source image.
     * Uses median cut color quantization.
     * 
     * @param image source image.
     * @param palette color palette, data order RGB RGB RGB ...
     * @param maxColors maximum number of colors in the palette.
     */
    public void createPalette(Bitmap image, short[] palette, int maxColors) {
        ListColorCuboid prototype = new ListColorCuboid(occurrences);
        createPalette(image, palette, maxColors, 0, 0, 0, 255, 255, 255,
                prototype);
    }

    /**
     * Alternative interface for creating palette. This one allows creating
     * common palette for several source images. Usage of it is following:
     * first one must call <code>allowSeveralCounts()</code> with value
     * <code>true</code>.
     * Then call <code>countAndAddOccurrences()</code> once time for every
     * image whose data will participate in palette creation. Finally create
     * the palette with <code>getPaletteOfSeveralImages</code>. Before
     * another palette is created, <code>clearCounted()</code> must be called
     * to reset the color data. Switching to using the normal interface is
     * done by calling <code>allowSeveralCounts()</code> with value
     * <code>false</code>.
     * 
     * @param allow Value false means the normal mode, true this alternative
     * interface.
     */
    public void allowSeveralCounts(boolean allow) {
        useAlternativeInterface = allow;
    }
    
    /**
     * Alternative interface for counting color occurrences in an image.
     * Adds color data of this image to the color data acquired from
     * previous calls of this method (all calls after clearCounted()).
     * @param image source image.
     */
    public void countAndAddOccurrences(Bitmap image) {
        countOccurrences(image, 0, 0, 0, 255, 255, 255); 
    }
    
    
    /**
     * Alternative interface: clears color data acquired from calling
     * countAndAddOccurrences().
     */
    public void clearCounted() {
        occurrences.clear();
    }
    
    /**
     * Alternative interface: create palette according to the acquired
     * color data obtained with one or more calls of coundAndAndOccurrences().
     *
     * @param palette color palette, data order RGB RGB RGB ...
     * @param maxColors maximum number of colors in the palette.
     */
    public void getPaletteOfSeveralImages(short[] palette, int maxColors) {
        ListColorCuboid prototype = new ListColorCuboid(occurrences);
        doMedianCut(palette, maxColors, 0, 0, 0, 255, 255, 255, prototype);        
    }
    
    protected void countOccurrences(Bitmap image, int minR, int minG,
            int minB, int maxR, int maxG, int maxB) {
        
        // Clear color counting list.
        if (!useAlternativeInterface) {
            occurrences.clear();
        }
        
        // Count RGB value occurrences in the source image.
        int bytesInImage = image.width * image.height * image.depth;
        int i;
        short r;
        short g;
        short b;
        Iterator<ColorOccurrence> iterator;
        ColorOccurrence occurrence;
        boolean found;
        for (i = 0; i < bytesInImage;) {
            r = image.pixels[i++];
            g = image.pixels[i++];
            b = image.pixels[i++];
            iterator = occurrences.iterator();
            found = false;
            while (iterator.hasNext()) {
                occurrence = iterator.next();
                if (occurrence.r == r && occurrence.g == g &&
                    occurrence.b == b) {
                    occurrence.count++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                occurrences.add(new ColorOccurrence(r, g, b, 1));
            }
        }
    }
}
