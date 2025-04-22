package anip.mediancut;

import java.util.ArrayList;
import java.util.Iterator;

import anip.Bitmap;

/**
 * Functionality for creating an optimized, indexed color palette
 * from a source image by median cut method. This version handles
 * color occurrences as a huge array. Suitable for images with lots
 * of colors.
 * 
 * @author Artturi Tilanterä
 */

public class ArrayMedianCut extends MedianCut {
    
    /**
     * Number of occurrences of every possible RGB combination
     * in the image, a "RGB space".
     */
    private int[] rgbSpace;

    /**
     * Size of rgbSpace in indices; depth * depth * depth.
     */
    private int rgbSpaceVolume;
    
    /**
     * Color sampling accuracy in bits per color component.
     * Also the bit shift of the green component.
     */
    private int depth;
    
    /**
     * Bit shift of the red component.
     */
    private int rShift;
    
    /**
     * Bit shift from this.depth to 8.
     */
    private int shift;
    
    /**
     * Determines whether contents of rgbSpace
     * must be cleared before colors can be counted for
     * the next median cut operation.
     */
    private boolean rgbSpaceIsCleared;
    
    /**
     * Constructs a new ArrayMedianCut.
     * 
     * @param depth sampling depth in bits per color component.
     * Possible values are 6, 7 and 8.
     */
    public ArrayMedianCut(int depth) {
        if (depth >= 6 && depth <= 8) {
            this.depth = depth;
        } else {
            this.depth = 8;
        }
        this.rShift = this.depth * 2;
        this.shift = 8 - this.depth;
        int cuboidSide = 1 << this.depth;
        
        rgbSpaceVolume = cuboidSide * cuboidSide * cuboidSide;
        rgbSpace = new int[rgbSpaceVolume];
    }
    
    /**
     * Creates an indexed palette for a 24-bit source image.
     * Uses median cut color quantization.
     * 
     * @param image source image.
     * @param palette color palette, data order RGB RGB RGB ...
     * @param maxColors maximum number of colors in the palette.
     */
    public void createPalette(Bitmap image, short[] palette,
            int maxColors) {
        createPalette(image, palette, maxColors, 0, 0, 0,
                ((1 << depth) - 1), ((1 << depth) - 1), ((1 << depth) - 1));
    }
    
    /**
     * Creates an indexed palette for a 24-bit source image.
     * Uses median cut color quantization.
     * 
     * @param image source image.
     * @param palette color palette, data order RGB RGB RGB ...
     * @param maxColors maximum number of colors in the palette.
     * @param minR minimum value of red component in the source image.
     * @param minG minimum value of green component in the source image.
     * @param minB minimum value of blue component in the source image.
     * @param maxR maximum value of red component in the source image.
     * @param maxG maximum value of green component in the source image.
     * @param maxB maximum value of blue component in the source image.
     */
    public void createPalette(Bitmap image, short[] palette, int maxColors,
            int minR, int minG, int minB, int maxR, int maxG, int maxB) {
        int i;
        for (i = 0; i < rgbSpaceVolume; i++) {
            rgbSpace[i] = 0;
        }
        rgbSpaceIsCleared = true;
        int r;
        int g;
        int b;
        int bytesInImage = image.width * image.height * image.depth;
        // Count RGB value occurrences in the source image.
        for (i = 0; i < bytesInImage;) {
            r = image.pixels[i++] >> shift;
            g = image.pixels[i++] >> shift;
            b = image.pixels[i++] >> shift;
            rgbSpace[(r << rShift) + (g << depth) + b]++;
        }
        rgbSpaceIsCleared = false;
        ArrayColorCuboid prototype = new ArrayColorCuboid(rgbSpace, depth);
        doMedianCut(palette, maxColors, minR, minG, minB,
                maxR, maxG, maxB, prototype);
    }
    
    protected void countOccurrences(Bitmap image, int minR, int minG,
            int minB, int maxR, int maxG, int maxB) {
        int i;
        int r;
        int g;
        int b;
        int bytesInImage = image.width * image.height * image.depth;

        // Reset color counting table. Clear only the portion where
        // the new color occurrences will be inserted - it's faster that way.
        if (!rgbSpaceIsCleared) {
            int shiftedR;
            int shiftedGsum;
            for (r = minR; r <= maxR; r++) {
                shiftedR = r << rShift;
                for (g = minG; g <= maxG; g++) {
                    shiftedGsum = shiftedR + (g << depth) + minB;
                    for (b = minB; b <= maxB; b++) {
                        rgbSpace[shiftedGsum++] = 0;
                    }
                }
            }
        }
        
        // Count RGB value occurrences in the source image.
        for (i = 0; i < bytesInImage;) {
            r = image.pixels[i++] >> shift;
            g = image.pixels[i++] >> shift;
            b = image.pixels[i++] >> shift;
            rgbSpace[(r << rShift) + (g << depth) + b]++;
        }
        rgbSpaceIsCleared = false;
    }
}
