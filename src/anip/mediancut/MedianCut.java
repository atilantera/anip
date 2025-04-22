package anip.mediancut;

import java.util.ArrayList;
import java.util.Iterator;

import anip.Bitmap;

/**
 * Represents functionality for creating an optimized, indexed color palette
 * from a source image by well-known median cut method.
 *  
 * @author Artturi Tilanterä
 */

public abstract class MedianCut {
    
    /**
     * Creates an indexed palette for a 24-bit source image.
     * Uses median cut color quantization.
     * 
     * @param image source image.
     * @param palette color palette, data order RGB RGB RGB ...
     * @param maxColors maximum number of colors in the palette.
     */
    public abstract void createPalette(Bitmap image, short[] palette,
            int maxColors);
    
    /**
     * Creates an indexed palette for a 24-bit source image.
     * Uses median cut color quantization. Parameters minR .. maxB are
     * initial values, each of them between 0..255 and of course the minimum
     * values being less than the maximum ones. These initial values speed up
     * the palette creation process if they are known. Otherwise one may want
     * to use the <code>createPalette(Bitmap image, short[] palette,
     * shortMaxColors) </code> version of the image, which calls this method
     * with parameters minR = minG = minB = 0 and maxR = maxG = maxB = 255.
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
            int minR, int minG, int minB, int maxR, int maxG, int maxB,
            ColorCuboid prototype) {
        countOccurrences(image, minR, minG, minB, maxR, maxG, maxB);
        doMedianCut(palette, maxColors, minR, minG, minB,
                maxR, maxG, maxB, prototype);
    }
    
    protected abstract void countOccurrences(Bitmap image, int minR, int minG,
            int minB, int maxR, int maxG, int maxB);
    
    protected void doMedianCut(short[] palette, int maxColors,
            int minR, int minG, int minB, int maxR, int maxG, int maxB,
            ColorCuboid prototype) {
        
        // RGB space is split to cuboids containing RGB occurrences
        // mentioned before. We start with a cuboid containing the whole
        // RGB space (eg. all color occurrences). Color occurrences
        // inside the same cuboid will be mapped into the same palette
        // index.
        ArrayList<ColorCuboid> cuboids = new ArrayList<ColorCuboid>();
        ColorCuboid startCuboid = prototype.newInstance();
        startCuboid.setProportions(minR, minG, minB, maxR, maxG, maxB);
        
        startCuboid.minimize();
        cuboids.add(startCuboid);

        // Increase number of cuboids to 256 (number of colors in the
        // indexed palette): split cuboid with largest volume in the RGB
        // space by the longest side of the cuboid (across R, G or B axis).        
        int largestVolume;
        int largestVolumeIndex;
        int currentVolume;
        Iterator<ColorCuboid> cuboidIterator;
        ColorCuboid cuboid;
        ColorCuboid newCuboid;
        int axis;
        int medianPoint;
        int i;
        while (cuboids.size() < maxColors) {
            largestVolume = 0;
            largestVolumeIndex = 0;
            cuboidIterator = cuboids.iterator();
            for (i = 0; cuboidIterator.hasNext(); i++) {
                cuboid = cuboidIterator.next();
                currentVolume = cuboid.volume();
                if (currentVolume > largestVolume) {
                    largestVolume = currentVolume;
                    largestVolumeIndex = i;
                }
            }
            cuboid = cuboids.get(largestVolumeIndex);
            axis = cuboid.longestSide();
            medianPoint = cuboid.getMedian(axis);
            newCuboid = cuboid.split(axis, medianPoint);
            cuboid.minimize();
            newCuboid.minimize();
            cuboids.add(newCuboid);
        }
        // Cuboids are ready. Get averaged colors inside them to the
        // palette.
        cuboidIterator = cuboids.iterator();
        for (i = 0; i < maxColors; i++) {
            cuboid = cuboidIterator.next();
            cuboid.getColor(palette, i);                
        }        
    }
}
