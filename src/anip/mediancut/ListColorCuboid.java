package anip.mediancut;

import java.util.ArrayList;
import java.util.Iterator;


/**
 * Represents a subspace of an RGB color space. Used in creating an
 * indexed palette by median cut method. This implementations handles colors
 * as a list. Useful for images that contain very few colors
 * (few hundreds at most).
 */

public class ListColorCuboid extends ColorCuboid {

    /**
     * A list of all color occurrences in the whole RGB space, meaning
     * not just the occurrences of this cuboid. The field .cuboid in the
     * class ColorOccurrence contains information on which cuboid every
     * occurrence belongs to. This field is a reference to an ArrayList
     * that is common between every ListColorCuboid.
     */
    private ArrayList<ColorOccurrence> allOccurrences;

    /**
     * A list of color occurrences of this cuboid.
     */
    private ArrayList<ColorOccurrence> ownOccurrences;

    /**
     * Constructs a new ColorCuboid. Initial size is
     * (0, 0, 0) - (255, 255, 255).
     * 
     * @param allOccurrences A list of all color occurrences in the whole
     * RGB space.
     */
    
    @SuppressWarnings("cast")
    ListColorCuboid(ArrayList<ColorOccurrence> allOccurrences) {
        super();
        this.allOccurrences = allOccurrences;        
        this.ownOccurrences =
            (ArrayList<ColorOccurrence>) allOccurrences.clone();
    } 
    
    /**
     * Constructs a new ListColorCuboid.
     *
     * @param minR smallest value of red contained by the cuboid.
     * @param minG smallest value of green contained by the cuboid.
     * @param minB smallest value of blue contained by the cuboid.
     * @param maxR greatest value of red contained by the cuboid.
     * @param maxG greatest value of green contained by the cuboid.
     * @param maxB greatest value of blue contained by the cuboid.
     * @param allOccurrences A list of all color occurrences in the whole
     * RGB space.
     * @param ownOccurrences A list of color occurrences of this cuboid.
     */
    public ListColorCuboid(int minR, int minG, int minB, int maxR, int maxG,
            int maxB, ArrayList<ColorOccurrence> allOccurrences,
            ArrayList<ColorOccurrence> ownOccurrences) {
        super(minR, minG, minB, maxR, maxG, maxB);
        this.allOccurrences = allOccurrences;
        this.ownOccurrences = ownOccurrences;
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
        Iterator<ColorOccurrence> iterator = ownOccurrences.iterator();
        ColorOccurrence occurrence;
        while (iterator.hasNext()) {
            occurrence = iterator.next();
            r = occurrence.r;
            g = occurrence.g;
            b = occurrence.b;
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
        int index;
        switch (axis) {
        case 0: { // red
            Iterator<ColorOccurrence> iterator = ownOccurrences.iterator();
            ColorOccurrence occurrence;
            while (iterator.hasNext()) {
                occurrence = iterator.next();
                index = occurrence.r;
                if (!pointsTowardsAxis[index]) {
                    pointsTowardsAxis[index] = true;
                    totalPointsOnAxis++;
                }
            }
            break;
        }
        case 1: { // green
            Iterator<ColorOccurrence> iterator = ownOccurrences.iterator();
            ColorOccurrence occurrence;
            while (iterator.hasNext()) {
                occurrence = iterator.next();
                index = occurrence.g;
                if (!pointsTowardsAxis[index]) {
                    pointsTowardsAxis[index] = true;
                    totalPointsOnAxis++;
                }
            }
            break;
        }
        case 2: { // blue 
            Iterator<ColorOccurrence> iterator = ownOccurrences.iterator();
            ColorOccurrence occurrence;
            while (iterator.hasNext()) {
                occurrence = iterator.next();
                index = occurrence.b;
                if (!pointsTowardsAxis[index]) {
                    pointsTowardsAxis[index] = true;
                    totalPointsOnAxis++;
                }
            }
            break;
        }
        }
        int halfOfTotalPoints = totalPointsOnAxis / 2;
        int pointsCount = 0;
        for (index = 0; index < 256; index++) {
            if (pointsTowardsAxis[index]) {
                pointsCount++;
                if (pointsCount == halfOfTotalPoints) {
                    return index;
                }
            }
        }
        return 128;
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
    public ListColorCuboid split(int axis, int point) {
        ListColorCuboid otherHalf = null;
        ArrayList<ColorOccurrence> othersOccurrences =
            new ArrayList<ColorOccurrence>();
        ColorOccurrence occurrence;
        int i;
        switch (axis) {
        case 0: { // red
            for (i = 0; i < ownOccurrences.size();) {
                occurrence = ownOccurrences.get(i);
                if (occurrence.r > point) {
                    ownOccurrences.remove(i);
                    othersOccurrences.add(occurrence);
                } else {
                    i++;
                }
            }
            otherHalf = new ListColorCuboid(point + 1, minG, minB,
                    maxR, maxG, maxB, allOccurrences, othersOccurrences);
            maxR = point;
            break;
        }
        case 1: { // green 
            for (i = 0; i < ownOccurrences.size();) {
                occurrence = ownOccurrences.get(i);
                if (occurrence.g > point) {
                    ownOccurrences.remove(i);
                    othersOccurrences.add(occurrence);
                } else {
                    i++;
                }
            }
            otherHalf = new ListColorCuboid(minR, point + 1, minB,
                    maxR, maxG, maxB, allOccurrences, othersOccurrences);
            maxG = point;
            break;
        }
        case 2: { // blue 
            for (i = 0; i < ownOccurrences.size();) {
                occurrence = ownOccurrences.get(i);
                if (occurrence.b > point) {
                    ownOccurrences.remove(i);
                    othersOccurrences.add(occurrence);
                } else {
                    i++;
                }
            }
            otherHalf = new ListColorCuboid(minR, minG, point + 1,
                    maxR, maxG, maxB, allOccurrences, othersOccurrences);

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
        Iterator<ColorOccurrence> iterator = ownOccurrences.iterator();
        ColorOccurrence occurrence;
        while (iterator.hasNext()) {
            occurrence = iterator.next();
            colorCount += occurrence.count;
            sumR += (float)occurrence.r * (float)occurrence.count;
            sumG += (float)occurrence.g * (float)occurrence.count;
            sumB += (float)occurrence.b * (float)occurrence.count;
        }
        index *= 3;
        palette[index++] = (short)(sumR / colorCount);
        palette[index++] = (short)(sumG / colorCount);
        palette[index] = (short)(sumB / colorCount);
    }
    
    /**
     * Creates a new instance. Copies references to color occurrences
     * in color space.
     * 
     * See http://en.wikipedia.org/wiki/Factory_method_pattern
     */
    public ListColorCuboid newInstance() {
        return new ListColorCuboid(this.allOccurrences);
    }
}
