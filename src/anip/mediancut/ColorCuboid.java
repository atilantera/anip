package anip.mediancut;

/**
 * Represents a subspace of the RGB color space. Used in creating an
 * indexed palette by median cut method.
 * 
 * @author Artturi Tilanterä
 */

public abstract class ColorCuboid {

    protected int minR;
    protected int maxR;
    protected int minG;
    protected int maxG;
    protected int minB;
    protected int maxB;

    /**
     * Constructs a new ColorCuboid. Initial size is
     * (0, 0, 0) - (255, 255, 255).
     */
    public ColorCuboid() {
        minR = 0;
        minG = 0;
        minB = 0;
        maxR = 255;
        maxG = 255;
        maxB = 255;
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
     */
    public ColorCuboid(int minR, int minG, int minB, int maxR, int maxG,
            int maxB) {
        this.minR = minR;
        this.minG = minG;
        this.minB = minB;
        this.maxR = maxR;
        this.maxG = maxG;
        this.maxB = maxB;
    }
    
    public void setProportions(int minR, int minG, int minB, int maxR, int maxG,
            int maxB) {
        this.minR = minR;
        this.minG = minG;
        this.minB = minB;
        this.maxR = maxR;
        this.maxG = maxG;
        this.maxB = maxB;
    }
    
    /**
     * Minimizes the size of the cuboid so that the faces of the
     * cuboid touch the outermost color occurrences in the RGB
     * subspace contained by the cuboid.
     */
    public abstract void minimize();

    /**
     * Returns volume of this cuboid. Min and max values are
     * included in the lengths of the sides of the cuboid.
     *
     * @return volume in RGB space.
     */
    public int volume() {
        int rLength = maxR - minR + 1;
        int gLength = maxG - minG + 1;
        int bLength = maxB - minB + 1;
        return rLength * gLength * bLength;
    }

    /**
     * Returns longest side meaning one of the proportions {R, G, B}
     * which is the largest.
     *
     * @return 0 if R, 1 if G, 2 if B.
     */
    public int longestSide() {
        int rLength = maxR - minR + 1;
        int gLength = maxG - minG + 1;
        int bLength = maxB - minB + 1;
        int side = 0;       
        if (rLength > gLength) {       // BRG or RBG or RGB
            if (bLength > rLength) {   // BRG
                side = 2;
            }
            side = 0;                  // RBG or RGB
        } else {                       // BGR or GBR or GRB
            if (bLength > gLength) {   // BGR
                side = 2;
            }
            side = 1;                  // GBR or GRB
        }
        return side;
    }

    
    /**
     * Returns median of the points along the specified axis.
     *
     * @param axis 0 if R, 1 if G, 2 if B.
     * @return median
     */
    public abstract int getMedian(int axis);
    
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
    public abstract ColorCuboid split(int axis, int point);

    /**
     * Writes weighted average of colors contained by this cuboid
     * into the specified palette.
     *
     * @param palette RGB palette. Data in order RGB RGB RGB..
     * @param index Index number of color, 0..255.
     */
    public abstract void getColor(short[] palette, int index);
    
    /**
     * Creates a new instance. Actual implementation MUST copy
     * the reference(s) to color occurrences in color space.
     * 
     * See http://en.wikipedia.org/wiki/Factory_method_pattern
     */
    public abstract ColorCuboid newInstance();

}
