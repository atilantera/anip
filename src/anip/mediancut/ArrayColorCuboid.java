package anip.mediancut;


/**
 * Represents a subspace of an RGB color space. Used in creating an
 * indexed palette by median cut method. This implementations handles colors
 * as a huge 64 megabyte array representing every possible RGB combination.
 * Useful for images that contain thousands or even more colors.
 */

public class ArrayColorCuboid extends ColorCuboid {

    /**
     * Reference to a RGB color space.
     * Index = r * 256 * 256 + g * 256 + b.
     * Value = number of occurrences of the color (in an image).
     */
    private int[] count;
    
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
     * Constructs a new ColorCuboid. 
     * 
     * @param colorSpace reference to a 256 * 256 * 256 color space.
     * @param depth Color sampling accuracy of RGB space in bits per color
     * component.
     */
    ArrayColorCuboid(int[] colorSpace, int depth) {
        super();
        this.count = colorSpace;
        this.depth = depth;        
        this.rShift = depth * 2;
        this.shift = 8 - depth;
        this.maxR = (1 << depth) - 1;
        this.maxG = (1 << depth) - 1;
        this.maxB = (1 << depth) - 1;
    }   
    
    /**
     * Constructs a new ColorCuboid.
     *
     * @param minR smallest value of red contained by the cuboid.
     * @param minG smallest value of green contained by the cuboid.
     * @param minB smallest value of blue contained by the cuboid.
     * @param maxR greatest value of red contained by the cuboid.
     * @param maxG greatest value of green contained by the cuboid.
     * @param maxB greatest value of blue contained by the cuboid.
     * @param colorSpace reference to a 256 * 256 * 256 color space.
     * @param depth Color sampling accuracy of RGB space in bits per color
     * component.
     */
    
    ArrayColorCuboid(int minR, int minG, int minB, int maxR, int maxG,
        int maxB, int[] colorSpace, int depth) {
        super (minR, minG, minB, maxR, maxG, maxB);
        this.count = colorSpace;
        this.depth = depth;
        this.rShift = depth * 2;
        this.shift = 8 - depth;
    }
    
    /**
     * Minimizes the size of the cuboid so that the faces of the
     * cuboid touch the outermost color occurrences in the RGB
     * subspace contained by the cuboid.
     */
    public void minimize() {     
        int newMinR = maxR;
        int newMinG = maxG;
        int newMinB = maxB;
        int newMaxR = minR;
        int newMaxG = minG;
        int newMaxB = minB;
        int r;
        int g;
        int b;
        for (r = minR; r <= maxR; r++) {
            for (g = minG; g <= maxG; g++) {
                for (b = minB; b <= maxB; b++) {
                    if (count[(r << rShift) + (g << depth) + b] > 0) {
                        if (r < newMinR) {
                            newMinR = r;
                        }
                        if (g < newMinG) {
                            newMinG = g;
                        }
                        if (b < newMinB) {
                            newMinB = b;
                        }
                        if (r > newMaxR) {
                            newMaxR = r;
                        }
                        if (g > newMaxG) {
                            newMaxG = g;
                        }
                        if (b > newMaxB) {
                            newMaxB = b;
                        }
                    }
                }
            }
        }
        minR = newMinR;
        minG = newMinG;
        minB = newMinB;
        maxR = newMaxR;
        maxG = newMaxG;
        maxB = newMaxB;
    }

    /**
     * Returns median of the points along the specified axis.
     *
     * @param axis 0 if R, 1 if G, 2 if B.
     * @return median
     */
    public int getMedian(int axis) {
        // Specifies if there are any color points having their
        // count > 0 with the specified value of red, green or blue
        // (which one, depends on axis).
        boolean[] pointsTowardsAxis = new boolean[256];
        int totalPointsOnAxis = 0;
        int r;
        int g;
        int b;
        int index;
        boolean found;
        int start = 0;
        int end = 255;
        switch (axis) {
        case 0: { // red
            start = minR;
            end = maxR + 1;
            for (r = minR; r <= maxR; r++) {
                found = false;
                for (g = minG; g <= maxG && !found; g++) {
                    for (b = minB; b <= maxB; b++) {
                        index = (r << rShift) + (g << depth) + b;
                        if (count[index] > 0) {
                            pointsTowardsAxis[r] = true;
                            totalPointsOnAxis++;
                            found = true;
                            break;
                        }
                    }
                }
            }
            break;
        }
        case 1: { // green
            start = minG;
            end = maxG + 1;
            for (g = minG; g <= maxG; g++) {
                found = false;
                for (r = minR; r <= maxR && !found; r++) {
                    for (b = minB; b <= maxB; b++) {
                        index = (r << rShift) + (g << depth) + b;
                        if (count[index] > 0) {
                            pointsTowardsAxis[g] = true;
                            totalPointsOnAxis++;
                            found = true;
                            break;
                        }
                    }
                }
            }
            break;
        }
        case 2: { // blue
            start = minB;
            end = maxB + 1;
            for (b = minB; b <= maxB; b++) {
                found = false;
                for (r = minR; r <= maxR && !found; r++) {
                    for (g = minG; g <= maxG; g++) {
                        index = (r << rShift) + (g << depth) + b;
                        if (count[index] > 0) {
                            pointsTowardsAxis[b] = true;
                            totalPointsOnAxis++;
                            found = true;
                            break;
                        }
                    }
                }
            }
            break;
        }
        }
        int halfOfTotalPoints = totalPointsOnAxis / 2;
        int pointsCount = 0;
        for (index = start; index < end; index++) {
            if (pointsTowardsAxis[index]) {
                pointsCount++;
                if (pointsCount == halfOfTotalPoints) {
                    return index;
                }
            }
        }
        return (end - start) / 2;
    }

    /**
     * Splits the cuboid at the plane towards the axis and at the
     * specified point at that axis.
     *
     * @param axis 0 if R, 1 if G, 2 if B.
     * @param point where to split. Its value has to be between
     * min and max points of the cuboid along the correspongind axis.
     *
     * @return the other half of splitted cuboid.
     */
    public ArrayColorCuboid split(int axis, int point) {                     
        ArrayColorCuboid otherHalf = null;
        switch (axis) {
        case 0: { // red
            otherHalf = new ArrayColorCuboid(point + 1, minG, minB,
                    maxR, maxG, maxB, count, depth);
            maxR = point;
            break;
        }
        case 1: { // green 
            otherHalf = new ArrayColorCuboid(minR, point + 1, minB,
                    maxR, maxG, maxB, count, depth);
            maxG = point;
            break;
        }
        case 2: { // blue 
            otherHalf = new ArrayColorCuboid(minR, minG, point + 1,
                    maxR, maxG, maxB, count, depth);
            maxB = point;
            break;
        }
        }       
        return otherHalf;
    }

    /**
     * Writes weighted average of colors contained by this cuboid
     * into the specified palette.
     *
     * @param palette RGB palette. Data in order RGB RGB RGB..
     * @param index Index number of color, 0..255.
     */
    public void getColor(short[] palette, int index) {
        // Using floats gives enough precision: we don't need the type double
        // here because we are calculating weighted averages where the
        // largest values dominate the result so that rounding errors
        // coming from limited precision mean practically nothing.
        float sumR = 0;
        float sumG = 0;
        float sumB = 0;
        int colorCount = 0;
        int r;
        int g;
        int b;
        int occurrences;
        for (r = minR; r <= maxR; r++) {
            for (g = minG; g <= maxG; g++) {
                for (b = minB; b <= maxB; b++) {
                    occurrences = count[(r << rShift) + (g << depth) + b];
                    if (occurrences > 0) {
                        colorCount += occurrences;
                        sumR += (float)(r << shift) * (float)occurrences;
                        sumG += (float)(g << shift) * (float)occurrences;
                        sumB += (float)(b << shift) * (float)occurrences;
                    }
                }
            }
        }
       	index *= 3;
        palette[index++] = (short)(sumR / colorCount);
        palette[index++] = (short)(sumG / colorCount);
        palette[index] = (short)(sumB / colorCount);
    }
    
    /**
     * Creates a new instance. Copies the reference to color occurrences
     * in color space.
     * 
     * See http://en.wikipedia.org/wiki/Factory_method_pattern
     */
    public ArrayColorCuboid newInstance() {
        return new ArrayColorCuboid(this.count, this.depth);
    }
}
