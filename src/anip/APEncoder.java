package anip;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.LinkedList;
import anip.mediancut.ArrayMedianCut;
import anip.mediancut.ListMedianCut;

/**
 * <p>
 * Encoder for AP-format video files.
 * </p>
 * 
 * <p>
 * Method call order for encoding one file: <code>setFile()</code>,
 * <code>setOptions()</code>, then <code>putImage()</code> for all the
 * images in their display order, and finally <code>close()</code>. Encoding
 * begins when <code>setFile()</code> and <code>setOptions()</code> are
 * called, and then putImage() called for the first time.
 * </p>
 * 
 * @author Artturi Tilanterä
 */

public class APEncoder {

    //
    // Contents of this class:
    //
    // Fields: constants (static fields)
    // Fields: General information on the file being encoded
    // Fields: General fields for writing the file
    // Fields: Data applying to the whole current image being encoded
    // Fields: Data used in encoding a masterblock
    // Fields: Color quantizers
    // Fields: Statistics
    //
    // Public methods
    // Private methods: preparing encoding
    // Private methods: encoding a frame
    // Private methods: compressing a masterblock
    // Private methods: compression by optimized palette
    // Private methods: painting onto an image
    // Private methods: testing
    //
    
    //
    // Constants
    //

    /**
     * <p>
     * Width of a block. A block is a constant sized, square shaped sub-image
     * the encoded image consists of.
     * </p>
     * 
     * <p>
     * SUMMARY OF BLOCKS: The frame, a single image that will be encoded, is
     * split into constant-sized squares called master blocks. Master blocks
     * consist of smaller squares called blocks. The changes between frames
     * are handled in blocks while subsections of frame containing local
     * palettes are handled with size of master blocks.
     * </p>
     */
    private final static int BLOCK_WIDTH = 8;

    /** Width of a master block. */
    private final static int MASTERBLOCK_WIDTH = BLOCK_WIDTH * 2;

    /** Area of a master block. */
    private final static int MASTERBLOCK_AREA = MASTERBLOCK_WIDTH
            * MASTERBLOCK_WIDTH;

    /** Half of the area area of a master block. */
    private final static int HALF_MASTERBLOCK_AREA = MASTERBLOCK_AREA / 2;

    /** Maximum colors in the local palette of one palette block. */
    private final static int MAX_COLORS_IN_BLOCK = 16;

    /**
     * Maximum colors in a frame when it is in indexed color mode (has a
     * palette).
     */
    private final static int MAX_COLORS_IN_IMAGE = 256;

    /** Maximum interval between two keyframes in secods. */
    private final static float MAX_KEYFRAME_INTERVAL = 10;

    /** Minimum interval between two keyframes in secods. */
    private final static float MIN_KEYFRAME_INTERVAL = 2;

    /**
     * Minimum ratio of changing blocks per all blocks to make
     * current frame a keyframe.
     */
    private final static double MIN_CHANGE_FOR_KEYFRAME = 0.8;

    /**
     * Minimum color distance in a block where the block is marked to be
     * changing. Distance in 256x256x256 RGB space.
     */
    private final static int BLOCK_CHANGE_THRESOLD = 8;

    //
    // Fields
    //    

    // General information on the file being encoded.

    /**
     * Determines whether the encoder is ready to accept images and write
     * them to a video file. This also indicates that a file is opened
     * in a file system.
     */
    private boolean isEncoding;

    /** Name of the video file. */
    private String fileName;

    /** Width of the video image in pixels. */
    private short frameWidth;

    /** Height of the video image in pixels. */
    private short frameHeight;

    /** Video playing speed in frames per second. */
    private float fps;

    /** Number of frames in the video. Counter. */
    private int frameCount;

    /** Depth in bits per color component {R, G, B} in median cut
     * palette optimization. */
    private int medianCutDepth;
       
    //
    // General fields for writing the file
    //

    /** Handle to the disk I/O. */
    private RandomAccessFile file;

    /**
     * Addresses of the keyframes as bytes from the beginning of the file.
     */
    private LinkedList<Integer> keyFrameAddresses;

    /** Data of one encoded frame ready to be written into the file. */
    private byte[] encodedFrame;
    
    /** The actual size of current encoded frame in bytes. */
    private int encodedFrameLength;

    //
    // Data applying to the whole current image being encoded
    //

    /** Number of last keyframe in frames. */
    private int numberOfLastKeyframe;

    /** 256-color RGB palette for the current frame. */
    private short[] imagePalette;

    /** The encoded image with 256 color palette (this.imagePalette). */
    private Bitmap imageWithPalette;

    /** Width of the image in blocks. */
    private int widthInBlocks;

    /** Height of the image in blocks. */
    private int heightInBlocks;

    /** Width of the image in masterblocks. */
    private int widthInMasterBlocks;

    /** Height of the image in masterblocks. */
    private int heightInMasterBlocks;

    /**
     * Bytemap representing the blocks of the current frame. Size is
     * this.widthInBlocks * this.heightInBlocks. Values are: 0 = block contains
     * exactly the same pixel values as the block in the same position in the
     * previous frame; 1 = block is different from the corresponding block in
     * the previous frame.
     */
    private byte[] changedBlocks;

    /** Number of value == 1:s in this.changedBlocks. */
    private int numberOfChangingBlocks;
    
    /**
     * Same as this.changedBlocks, but for previous frame.
     * Used by compressRegionChanges().
     */
    private byte[] previousChangedBlocks;

    /**
     * Bitmap representing containing sum of all image changes from the last
     * keyframe. It is used to detect changing blocks in the current frame. The
     * advantage of storing sum the all the changes since last keyframe is that
     * then even the slight changes that occur between several images will be
     * encoded when the thresold is reached, if the change between two adjacent
     * frames was below the thresold. For further information see the methods
     * where this field is actually used.
     */
    private Bitmap previousFrame;

    /**
     * Image used to test changing block detection and palette creation.
     */
    private Bitmap testImage;

    //
    // Data used in encoding a masterblock
    //

    /**
     * 16 color palette for the current master block. Values are indices of
     * this.imagePalette.
     */
    private short[] blockPalette;

    /**
     * 16 color palette for current master block. Values are RGB RGB .. copied
     * from imagePalette.
     */
    private short[] blockPaletteRGB;

    /**
     * Small bitmap in indexed color, for encoding the current master block.
     */
    private Bitmap currentMasterBlock;

    /**
     * Small bitmap that contains image data of this.currentMasterBlock in RGB
     * fullcolor.
     */
    private Bitmap currentMasterBlockFullColor;

    /**
     * Small bitmap in fullcolor, for encoding the current master block. NOTE:
     * size of this bitmap is BLOCK_WIDTH * BLOCK_WIDTH. It represents a single
     * quarter of the current master block.
     */
    private Bitmap blockFullColor;

    /**
     * Table used to count occurrences of the colors of the image palette in
     * this.currentMasterBlock. See createBlockPalette().
     */
    private short[] blockColorsIndex;

    /**
     * Table used to count occurrences of the colors of the image palette in
     * this.currentMasterBlock. See createBlockPalette().
     */
    private short[] blockColorsCount;

    /** Number of colors actually used in this.blockPalette. */
    private int blockColorsUsed;

    //
    // Color quantizers
    //

    /**
     * Median cut color quantizer used in converting a frame into maximum of
     * 256-color indexed palette.
     */
    private ArrayMedianCut arrayQuantizer;

    /**
     * Median cut color quantizer used in converting a master block into maximum
     * of 16 color indexed palette.
     */
    private ListMedianCut listQuantizer;
   
    //
    // Sub-encoders
    //
    
    /** Pixel encoder for pixels of a masterblock. */
    private PixelEncoder pixEncoder;
    
    //
    // Public methods
    //

    /**
     * Constructs a new encoder.
     */
    public APEncoder() {

        //
        // Initialize fields
        //

        // General information on the file being encoded
        isEncoding = false;
        fileName = null;
        frameWidth = 0;
        frameHeight = 0;
        fps = 1;
        frameCount = 0;
        medianCutDepth = 7;

        // General fields for writing the file
        file = null;
        keyFrameAddresses = new LinkedList<Integer>();
        encodedFrame = null;
        encodedFrameLength = 0;

        // Data applying to the whole current image being encoded
        numberOfLastKeyframe = 0;
        imagePalette = new short[MAX_COLORS_IN_IMAGE * 3];
        imageWithPalette = new Bitmap();
        widthInBlocks = 0;
        heightInBlocks = 0;
        widthInMasterBlocks = 0;
        heightInMasterBlocks = 0;
        changedBlocks = null;
        previousChangedBlocks = null;
        previousFrame = new Bitmap();
        testImage = new Bitmap();

        // Data used in encoding single masterblock
        blockPalette = new short[MAX_COLORS_IN_BLOCK];
        blockPaletteRGB = new short[MAX_COLORS_IN_BLOCK * 3];
        currentMasterBlock = new Bitmap();
        currentMasterBlock.resize(MASTERBLOCK_WIDTH, MASTERBLOCK_WIDTH, 1);
        currentMasterBlockFullColor = new Bitmap();
        currentMasterBlockFullColor.resize(MASTERBLOCK_WIDTH,
                MASTERBLOCK_WIDTH, 3);
        blockFullColor = new Bitmap();
        blockFullColor.resize(BLOCK_WIDTH, BLOCK_WIDTH, 3);
        blockColorsIndex = new short[MASTERBLOCK_AREA];
        blockColorsCount = new short[MASTERBLOCK_AREA];

        // Color quantizers
        arrayQuantizer = new ArrayMedianCut(medianCutDepth);
        listQuantizer = new ListMedianCut();
        listQuantizer.allowSeveralCounts(true);
        
        // Sub-encoders
        pixEncoder = new PixelEncoder(MASTERBLOCK_AREA);
        
    }

    /**
     * Sets name of the AP file for saving the encoded video.
     * 
     * @param fileName
     *            name of the AP file to write.
     * @throws IOException
     */
    public void setFile(String fileName) throws IOException {
        if (isEncoding) {
            throw new IOException("Cannot set file name: encoding on "
                    + "process");
        }
        this.fileName = fileName;
    }

    /**
     * Sets video encoding and playing options.
     * 
     * @param fps
     *            video playing speed in frames per second.
     */
    public void setOptions(float fps, int medianCutDepth)
        throws IOException {
        if (isEncoding) {
            throw new IOException("Cannot set frames per second: encoding "
                    + "on process");
        }
        if (fps <= 0) {
            throw new IOException("Frames per second must be positive");
        }
        this.fps = fps;
        if (medianCutDepth < 6) {
            medianCutDepth = 6;
        }
        if (medianCutDepth > 8) {
            medianCutDepth = 8;
        }
        if (medianCutDepth != this.medianCutDepth) {
            arrayQuantizer = new ArrayMedianCut(medianCutDepth);
            this.medianCutDepth = medianCutDepth;
        }
    }

    /**
     * Begins or continues encoding of the video by adding the image specified
     * to the encoded stream. Finally writes this addition to the video stream
     * to the disk. Decides the frame size according to the proportions of the
     * first image.
     * 
     * @param image
     *            image of the next frame.
     * @throws IOException
     */
    public void putImage(Bitmap image) throws IOException {

        if (!isEncoding) {

            // Make preparations for encoding

            if (image.width < 1 || image.height < 1) {
                throw new IOException("Invalid image size: " + image.width
                        + " x " + image.height);
            }
            try {
                beginEncoding(image.width, image.height);
            } catch (IOException e) {
                throw e;
            }

        }

        // Ensure properties of source image
        if (image.width != frameWidth || image.height != frameHeight) {
            throw new IOException("Image size is " + image.width + " x "
                    + image.height + "! It should be " + frameWidth + " x "
                    + frameHeight + ".");
        }
        if (image.depth != 3) {
            throw new IOException("Image depth must be 3 = 24 bits.");
        }

        // Encode the image as a new frame
        try {
            encodeFrame(image);
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Ends encoding of the video, finalizes and closes the video file that
     * was being written.
     * 
     * @throws IOException
     */
    public void close() throws IOException {

        if (!isEncoding) {
            throw new IOException("Encoder has not been writing a file.");
        }

        file.seek(5);
        file.writeInt(this.frameCount);
        file.seek(13);
        file.writeInt(this.keyFrameAddresses.size());

        Iterator<Integer> iterator = this.keyFrameAddresses.iterator();
        int previous = 0; // bytes from beginning of the file
        int current = 0; // bytes from beginning of the file
        int next = 0; // bytes from beginning of the file

        // Pointers to previous and next keyframe in keyframes
        // First keyframe
        if (iterator.hasNext()) {
            current = iterator.next();
        }
        if (iterator.hasNext()) {
            next = iterator.next();
        }

        file.seek(current + 5);
        file.writeInt(previous);
        file.writeInt(next);
        // Keyframes between the first and the last
        while (iterator.hasNext()) {
            previous = current;
            current = next;
            next = iterator.next();
            file.seek(current + 5);
            file.writeInt(previous - current);
            file.writeInt(next - current);
        }
        // Last keyframe
        if (this.keyFrameAddresses.size() > 1) {
            previous = current;
            current = next;
            next = 0;
            file.seek(current + 5);
            file.writeInt(previous);
            file.writeInt(next);
        }

        file.close();
        isEncoding = false;
    }
    
    //
    // Private methods: preparing encoding
    //
   
    /**
     * Prepares everything for encoding process.
     * 
     * @param frameWidth
     *            width of the video image in pixels.
     * @param frameHeight
     *            height of the video image in pixels.
     */

    private void beginEncoding(short frameWidth, short frameHeight)
            throws IOException {
        frameCount = 0;
        keyFrameAddresses = new LinkedList<Integer>();
        setFrameSize(frameWidth, frameHeight);

        int adjustedWidth = widthInMasterBlocks * MASTERBLOCK_WIDTH;
        int adjustedHeight = heightInMasterBlocks * MASTERBLOCK_WIDTH;

        previousFrame.resize(adjustedWidth, adjustedHeight, 3);
        imageWithPalette.resize(adjustedWidth, adjustedHeight, 1);
        testImage.resize(adjustedWidth, adjustedHeight, 3);

        // Delete existing file before creating new one.
        File testFile = new File(fileName);
        testFile.delete();

        try {
            file = new RandomAccessFile(fileName, "rw");
        } catch (IOException e) {
            throw e;
        }

        byte[] magicNumber = { 0x41, 0x4E, 0x49, 0x50 };
        file.write(magicNumber);
        byte version = 1;
        file.write(version);
        file.writeInt(0); // Number of frames
        file.writeFloat(fps);
        file.writeInt(0); // Number of keyframes
        file.writeShort(frameWidth);
        file.writeShort(frameHeight);

        isEncoding = true;
    }

    /**
     * Sets the width and height of the video image.
     * 
     * @param width
     *            width of the video image in pixels.
     * @param height
     *            height of the video image in pixels.
     */
    private void setFrameSize(short width, short height) {

        this.frameWidth = width;
        this.frameHeight = height;

        this.widthInBlocks = width / BLOCK_WIDTH;
        if (width % BLOCK_WIDTH != 0) {
            this.widthInBlocks++;
        }
        this.heightInBlocks = height / BLOCK_WIDTH;
        if (height % BLOCK_WIDTH != 0) {
            this.heightInBlocks++;
        }

        this.widthInMasterBlocks = width / MASTERBLOCK_WIDTH;
        if (width % MASTERBLOCK_WIDTH != 0) {
            this.widthInMasterBlocks++;
        }
        this.heightInMasterBlocks = height / MASTERBLOCK_WIDTH;
        if (height % MASTERBLOCK_WIDTH != 0) {
            this.heightInMasterBlocks++;
        }

        this.changedBlocks = new byte[widthInBlocks * heightInBlocks];
        this.previousChangedBlocks = new byte[widthInBlocks * heightInBlocks];
        
        // Size of compressed data at maximum should be
        // width * height / 2 since the pixels are encoded in 4 bit values.
        // Masterblock palettes and pixel compression takes up
        // 16 + 2 = 18 bytes per every 256 pixels which is ~ 1/16 bytes
        // per pixel. So 1/2 + 1/16 still fits nicely below 1.
        this.encodedFrame = new byte[frameWidth * frameHeight * 2];
    }
    
    //
    // Private methods: encoding a frame
    //
    
    /**
     * Encodes a single image as a frame in video stream.
     * 
     * @param image bitmap image to be encoded.
     */

    private void encodeFrame(Bitmap image) throws IOException {

        if (!isEncoding) {
            throw new IOException("Encoder is not ready.");
        }

        encodedFrameLength = 0;
        boolean isKeyframe = false;

        // Determine whether the new frame should be a keyframe.
        float timeSinceLastKeyframe =
            (frameCount - numberOfLastKeyframe)
                / this.fps;
        if (timeSinceLastKeyframe >= MAX_KEYFRAME_INTERVAL
                || this.keyFrameAddresses.size() == 0) {
            isKeyframe = true;
        }

        // Find changes in image (as blocks)
        resizeToBlockDivisible(image);
        int areaInBlocks = widthInBlocks * heightInBlocks;
        if (!isKeyframe) {
            findRegionChanges(previousFrame, image, BLOCK_CHANGE_THRESOLD);
            if ((float) numberOfChangingBlocks /
                (float) areaInBlocks >= MIN_CHANGE_FOR_KEYFRAME &&
		timeSinceLastKeyframe >= MIN_KEYFRAME_INTERVAL) {
                isKeyframe = true;
            }
        }
        
        int i;
        if (isKeyframe) {
            for (i = 0; i < areaInBlocks; i++) {
                this.changedBlocks[i] = 1;
            }
            keyFrameAddresses.add(new Integer((int) file.getFilePointer()));
	    numberOfLastKeyframe = frameCount;
        }

        // Paint changed blocks onto this.previousFrame.
        if (!isKeyframe) {
            paintChangedRegions(image, previousFrame);
        } else {
            // If the frame is a keyframe, all the blocks change and
            // therefore it is faster to copy all the image data directly.
            int imageSizeInBytes = frameWidth * frameHeight * 3;
            for (i = 0; i < imageSizeInBytes; i++) {
                previousFrame.pixels[i] = image.pixels[i];
            }
        }

        // Create and apply image palette
        arrayQuantizer.createPalette(image, imagePalette,
                MAX_COLORS_IN_IMAGE);

	compressFramePalette();
        compressRegionChanges(isKeyframe);        
        
        // Create and apply palettes in master blocks, write blocks.
        int x;
        int y;
        boolean hasChanging;
        for (y = 0; y < heightInMasterBlocks; y++) {
            for (x = 0; x < widthInMasterBlocks; x++) {

                hasChanging = hasChangingBlocks(x, y);
                if (hasChanging) {
		    copyFullColorBlockDataFromImage(image,
			x * MASTERBLOCK_WIDTH, y * MASTERBLOCK_WIDTH);
		    applyPalette(currentMasterBlockFullColor,
			currentMasterBlock);
                    createBlockPalette((x << 1), (y << 1));
                    applyBlockPalette();
                    compressMasterBlock((x << 1), (y << 1));
                }
            }
        }

        try {
            writeFrame(isKeyframe);
        } catch (IOException e) {
            throw e;
        }

        frameCount++;
    }

    /**
     * Writes a whole encoded frame created with encodeFrame() to the video
     * file.
     * 
     * @param isKeyframe determines whether the frame is a keyframe.
     */
    private void writeFrame(boolean isKeyframe) throws IOException {
        if (isKeyframe) {
            file.writeByte(1);
            file.writeInt(encodedFrameLength);
            file.writeInt(keyFrameAddresses.size() - 1);
            file.writeInt(0);
            file.writeInt(0);
        } else {
            file.writeByte(0);
            file.writeInt(encodedFrameLength);
        }
        file.write(encodedFrame, 0, encodedFrameLength);
    }

    /**
     * Compresses this.imagePalette into this.encodedFrame.
     */
    private void compressFramePalette() {
        for (int i = 0; i < 768; i++) {
            encodedFrame[encodedFrameLength++] = (byte) imagePalette[i];
        }
    }
    
    /**
     * Writes contents of this.changedBlocks compressed to
     * this.encodedFrame.
     */
    private void compressRegionChanges(boolean isKeyFrame) {
        int totalBlocks = widthInBlocks * heightInBlocks;
        int block;
        
        if (isKeyFrame) {
            for (block = 0; block < totalBlocks; block++) {
                changedBlocks[block] = 1;
                previousChangedBlocks[block] = 1;
            }
            return;
        }
        
        int shift = 7;
        int value = 0;
        for (block = 0; block < totalBlocks; block++) {
            value += changedBlocks[block] << shift;
            shift--;
            if (shift == -1) {
                encodedFrame[encodedFrameLength++] = (byte)value;
                value = 0;
                shift = 7;
            }
        }
        if (shift != -1) {
            encodedFrame[encodedFrameLength++] = (byte)value;
        }
        
    }

    /**
     * Adjusts image height and width to be multiples of BLOCK_WIDTH, and
     * according to this.heightInBlocks and this.widthInBlocks. This means
     * black margins are added to the right and bottom size of the image if
     * necessary.
     * 
     * @param image image to be resized.
     */
    private void resizeToBlockDivisible(Bitmap image) {
        int newWidth = this.widthInMasterBlocks * MASTERBLOCK_WIDTH;
        int newHeight = this.heightInMasterBlocks * MASTERBLOCK_WIDTH;

        if (image.width != newWidth || image.height != newHeight) {

            int newSize = newWidth * newHeight * 3;
            short[] newData = new short[newSize];
            int oldScanline = image.width * 3;
            int newScanline = newWidth * 3;

            int destIndex = 0; // byte index in newData
            int sourceIndex = 0;
            int y;
            int x;

            for (y = 0; y < image.height; y++) {

                for (x = 0; x < oldScanline; x++) {
                    newData[destIndex] = image.pixels[sourceIndex];
                    sourceIndex++;
                    destIndex++;
                }

                // Black right margin
                for (; x < newScanline; x++) {
                    newData[destIndex] = 0;
                    destIndex++;
                }
            }
            // Black bottom margin
            for (; y < newHeight; y++) {
                for (x = 0; x < newScanline; x++) {
                    newData[destIndex] = 0;
                    destIndex++;
                }
            }

            image.width = (short) newWidth;
            image.height = (short) newHeight;
            image.pixels = newData;
        }
    }

    /**
     * <p>
     * Find changes in blocks between two images. Stores results into
     * this.changedBlocks. Image width and height must be multiples of
     * BLOCK_WIDTH.
     * </p>
     * 
     * <p>
     * Detecting changes in blocks goes as following: compare block pixel by
     * pixel calculating color differences between the two images. Color
     * difference of two pixels is
     * <code>sqrt( (r2 - r1)^2 + (g2 - g1)^2 + (b2 - b1)^2 )</code> where
     * <code>sqrt</code> is square root and <code>^2</code> raising to the
     * second power. If at least one of the pixels in the block has color
     * difference that is equal or greater than thresold, then the block is
     * marked to be changing.
     * </p>
     * 
     * <p>
     * The actual implementation does not calculate square roots but rather
     * compares power-sum expression of the distance to the thresold squared.
     * Comparing of pixels in one block ends immediately if enough great
     * difference is found.
     * </p>
     * 
     * <p>
     * <b>Note:</b> It seems the optimal value for <code>thresold</code> is
     * 8.
     * </p>
     * 
     * @param firstImage
     *            image to be compared.
     * @param secondImage
     *            image to be compared.
     * @param thresold
     *            lowest color distance on which the block is marked to be
     *            changing.
     */

    private void findRegionChanges(Bitmap firstImage, Bitmap secondImage,
            int thresold) {

        // Raise thresold to the second power so that
        // we don't have to calculate square root for every
        // color distance to be able to compare it to thresold.
        thresold *= thresold;

        int blockX;
        int blockY;
        int blockIndex = 0;
        int x;
        int y;
        int pixelIndex;
        int blockScanline = BLOCK_WIDTH * 3;
        int scanline = widthInBlocks * BLOCK_WIDTH * 3;
        int scanlineAppend = scanline - blockScanline;

        int dR;
        int dG;
        int dB;
        int difference;
        boolean blockChanges;

        numberOfChangingBlocks = 0;

        // Examine block by block
        for (blockY = 0; blockY < heightInBlocks; blockY++) {
            for (blockX = 0; blockX < widthInBlocks; blockX++) {

                // Examine pixel by pixel in a block
                pixelIndex = blockY * BLOCK_WIDTH * scanline + blockX
                        * BLOCK_WIDTH * 3;
                blockChanges = false;

                for (y = 0; y < BLOCK_WIDTH; y++) {
                    for (x = 0; x < BLOCK_WIDTH; x++) {

                        dR = secondImage.pixels[pixelIndex]
                                - firstImage.pixels[pixelIndex];
                        pixelIndex++;

                        dG = secondImage.pixels[pixelIndex]
                                - firstImage.pixels[pixelIndex];
                        pixelIndex++;

                        dB = secondImage.pixels[pixelIndex]
                                - firstImage.pixels[pixelIndex];
                        pixelIndex++;

                        // Distance between two colors in RGB
                        // space (squared)
                        difference = dR * dR + dG * dG + dB * dB;

                        if (difference >= thresold) {
                            blockChanges = true;
                            break;
                        }
                    }
                    if (blockChanges) {
                        break;
                    }
                    pixelIndex += scanlineAppend;
                }

                if (blockChanges) {
                    changedBlocks[blockIndex] = 1;
                    numberOfChangingBlocks++;
                } else {
                    changedBlocks[blockIndex] = 0;
                }
                blockIndex++;
            }

        }
    }
    
    //
    // Private methods: compressing a masterblock
    //
    
    /**
     * Determines whether the given masterblock has changing sub-blocks.
     */
    private boolean hasChangingBlocks(int x, int y) {
        int blockX;
        int blockY;
        int startBlockX = x << 1;
        int startBlockY = y << 1;
        int i;
        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {

                i = (startBlockY + blockY) * widthInBlocks + startBlockX
                        + blockX;
                if (this.changedBlocks[i] == 1) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Writes a single masterblock into the the compressed data
     * buffer.
     * @throws IOException
     */
    private void compressMasterBlock(int startBlockX, int startBlockY) {
        int blockX;
        int blockY;
        int x;
        int y;
        int i;
        int blockIndex;

        compressMasterBlockPalette();

        // Mark all non-changing 8x8 pixel blocks to black
        // so that they will be compressed well, when the whole
        // masterblock is compressed.
        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {
                
                i = (startBlockY + blockY) * widthInBlocks +
                    startBlockX + blockX;
                if (this.changedBlocks[i] == 0) {

                    // Loop for a single block.
                    blockIndex = blockY * HALF_MASTERBLOCK_AREA + blockX
                            * BLOCK_WIDTH;

                    for (y = 0; y < BLOCK_WIDTH; y++) {
                        for (x = 0; x < BLOCK_WIDTH; x++) {
                            currentMasterBlock.pixels[blockIndex++] = 0;
                        }
                        blockIndex += BLOCK_WIDTH;
                    }                        

                } // if block was marked to be changing
                                    
            } // for BlockX
        } // for blockY
        
        compressMasterBlockPixels();
    }

    /**
     * Writes the palette of the current master block into the
     * compresed data buffer. Compresses the palette if possible.
     */
    private void compressMasterBlockPalette() {
        int i;
        for (i = 0; i < MAX_COLORS_IN_BLOCK; i++) {
            encodedFrame[encodedFrameLength++] = (byte) blockPalette[i];
        }
    }
    
    /**
     * Compresses pixel information in this.currentMasterBlock.pixels by run
     * length encoding to this.compressedBlock.
     */
    private void compressMasterBlockPixels() {
        encodedFrameLength += pixEncoder.encode(currentMasterBlock,
                encodedFrame, encodedFrameLength);
    }

    //
    // Private methods: compression by optimized palette
    //
    
    /**
     * Converts a 24-bit image to an indexed color image. Determines fittest
     * color for a pixel by shortest distance in the RGB color space.
     * 
     * @param fullcolorImage
     *            24-bit image with pixel values RGB RGB ..
     * @param indexedImage
     *            image with 256 color indexed palette.
     */
    private void applyPalette(Bitmap fullcolorImage, Bitmap indexedImage) {
        int fullcolorIndex = 0;
        int indexedIndex = 0;
        int imageSize = fullcolorImage.width * fullcolorImage.height;
        short r;
        short g;
        short b;
        short pr;
        short pg;
        short pb;
        int deltaR;
        int deltaG;
        int deltaB;
        int distance;
        int shortestDistance;
        int shortestIndex;
        int palettePointer;
        for (indexedIndex = 0; indexedIndex < imageSize;
                indexedIndex++) {
            r = fullcolorImage.pixels[fullcolorIndex++];
            g = fullcolorImage.pixels[fullcolorIndex++];
            b = fullcolorImage.pixels[fullcolorIndex++];
            // Maximum distance in the RGB space is
            // 255^2 + 255^2 + 255^2.
            // (256^2) * 3 = 196608
            shortestDistance = 196608;
            shortestIndex = 0;
            palettePointer = 0;
            // 768 = 256 * 3
            while (palettePointer < 768) {
                pr = imagePalette[palettePointer++];
                pg = imagePalette[palettePointer++];
                pb = imagePalette[palettePointer++];
                deltaR = pr - r;
                deltaG = pg - g;
                deltaB = pb - b;
                distance = deltaR * deltaR + deltaG * deltaG +
                    deltaB * deltaB;
                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    shortestIndex = (palettePointer - 1) / 3;
                }
            }
            indexedImage.pixels[indexedIndex] = (short) shortestIndex;
        }
    }

    /**
     * Generates a very limited palette for the current block when the block is
     * using the image palette. If number of the colors in the block need to be
     * reduced, uses ListMedianCut for palette creation.
     * 
     * @param startBlockX
     *            offset in blocks (not masterblocks) from the left edge of the
     *            image.
     * @param startBlockY
     *            offset in blocks (not masterblocks) from the top edge of the
     *            image.
     */
    private void createBlockPalette(int startBlockX, int startBlockY) {

        int blockX;
        int blockY;
        int x;
        int y;
        int i;
        short j;
        short color;
        boolean foundInTable;

        // Reset tables
        for (i = 0; i < MASTERBLOCK_AREA; i++) {
            blockColorsIndex[i] = 0;
            blockColorsCount[i] = 0;
        }
        blockColorsUsed = 0;

        // Count color occurrences and number of different colors.
        // A master block consists of 2 x 2 blocks.
        // Count color occurrences in only the quarters of the block
        // that are changing.

        // Loop for a master block which contains four blocks
        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {

                i = (startBlockY + blockY) * widthInBlocks + startBlockX
                        + blockX;
                if (this.changedBlocks[i] == 1) {

                    // Loop for a single block. Now i is pointer to
                    // currentMasterBlock.pixels[] which represent the
                    // current master block, and i is going through a square                    
                    // shaped area (block) inside that master block.
                    i = blockY * HALF_MASTERBLOCK_AREA +
                        blockX * BLOCK_WIDTH;
                    for (y = 0; y < BLOCK_WIDTH; y++) {
                        for (x = 0; x < BLOCK_WIDTH; x++) {

                            color = currentMasterBlock.pixels[i];
                            foundInTable = false;
                            for (j = 0; j < blockColorsUsed; j++) {
                                if (blockColorsIndex[j] == color) {
                                    foundInTable = true;
                                    blockColorsCount[j]++;
                                    break;
                                }
                            }
                            if (!foundInTable) {
                                blockColorsIndex[blockColorsUsed] = color;
                                blockColorsCount[blockColorsUsed]++;
                                blockColorsUsed++;
                            }

                            i++;
                        }
                        i += BLOCK_WIDTH;
                    }

                } // if block was marked to be changing
            } // for BlockX
        } // for blockY

        // Determine color selection method and use it
        if (blockColorsUsed > MAX_COLORS_IN_BLOCK) {

            createBlockPaletteMedianCut(startBlockX, startBlockY);

        } else {
            // Select most used colors, MAX_COLORS_IN_BLOCK at maximum.
            int selectColors = blockColorsUsed;
            if (selectColors > MAX_COLORS_IN_BLOCK) {
                selectColors = MAX_COLORS_IN_BLOCK;
            }
            short highestCount;
            short highestIndex;
            short count;
            int rgbIndex = 0;
            short mainPaletteIndex;
            for (i = 0; i < selectColors; i++) {
                highestCount = 0;
                highestIndex = -1;
                for (j = 0; j < blockColorsUsed; j++) {
                    count = blockColorsCount[j];
                    if (count > highestCount) {
                        highestCount = count;
                        highestIndex = j;
                    }
                }
                mainPaletteIndex = blockColorsIndex[highestIndex];
                blockPalette[i] = mainPaletteIndex;
                mainPaletteIndex *= 3;
                blockPaletteRGB[rgbIndex++] = imagePalette[mainPaletteIndex++];
                blockPaletteRGB[rgbIndex++] = imagePalette[mainPaletteIndex++];
                blockPaletteRGB[rgbIndex++] = imagePalette[mainPaletteIndex];
                blockColorsCount[highestIndex] = 0;
            }
            blockColorsUsed = selectColors;

        }
    }

    /**
     * Generates a very limited palette for the current master block when the
     * block is using the image palette. Uses ListMedianCut for palette
     * creation.
     * 
     * @param startBlockX offset in blocks (not masterblocks) from the left
     * edge of the image.
     * @param startBlockY offset in blocks (not masterblocks) from the top
     * edge of the image.
     */
    private void createBlockPaletteMedianCut(int startBlockX,
	    int startBlockY) {

        int blockX;
        int blockY;
        int x;
        int y;
        int i;
        short j;
        int colorIndex;
        int fullcolorIndex;

        // Count color occurrences and number of different colors.
        // A master block consists of 2 x 2 blocks.
        // Count color occurrences in only the quarters of the block
        // that are changing.

        // Loop for a master block which contains four blocks
        listQuantizer.clearCounted();

        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {

                i = (startBlockY + blockY) * widthInBlocks + startBlockX
                        + blockX;
                if (this.changedBlocks[i] == 1) {

                    // Loop for a single block. Now i is pointer to
                    // currentMasterBlock.pixels[] which represent the current
                    // master block, and i is going through a square
                    // shaped area (block) inside that master block.
                    i = blockY * HALF_MASTERBLOCK_AREA + blockX * BLOCK_WIDTH;
                    fullcolorIndex = 0;
                    for (y = 0; y < BLOCK_WIDTH; y++) {
                        for (x = 0; x < BLOCK_WIDTH; x++) {
                            colorIndex = currentMasterBlock.pixels[i] * 3;
                            for (j = 0; j < 3; j++) {
                                blockFullColor.pixels[fullcolorIndex++] =
				    imagePalette[colorIndex++];
                            }
                            i++;
                        }
                        i += BLOCK_WIDTH;
                    }

                    listQuantizer.countAndAddOccurrences(blockFullColor);

                } // if block was marked to be changing
            } // for BlockX
        } // for blockY

        // Obtain a palette for current master block where the image
        // palette already has been fitted onto. This will result a
        // temporary RGB palette.
        listQuantizer.getPaletteOfSeveralImages(blockPaletteRGB,
                MAX_COLORS_IN_BLOCK);

        // Map colors of temporary RGB palette to nearest
        // colors of the image palette.
        int rgbPaletteIndex = 0;
        short r;
        short g;
        short b;
        short pr;
        short pg;
        short pb;
        int deltaR;
        int deltaG;
        int deltaB;
        int distance;
        int shortestDistance;
        short shortestIndex;
        for (i = 0; i < MAX_COLORS_IN_BLOCK; i++) {
            r = blockPaletteRGB[rgbPaletteIndex++];
            g = blockPaletteRGB[rgbPaletteIndex++];
            b = blockPaletteRGB[rgbPaletteIndex++];
            // Maximum distance in the RGB space is 255^2 + 255^2 + 255^2.
            // (256^2) * 3 = 196608
            shortestDistance = 196608;
            shortestIndex = 0;
            // 768 = 256 * 3
            for (j = 0; j < 768;) {
                pr = imagePalette[j++];
                pg = imagePalette[j++];
                pb = imagePalette[j++];
                deltaR = pr - r;
                deltaG = pg - g;
                deltaB = pb - b;
                distance = deltaR * deltaR + deltaG * deltaG + deltaB * deltaB;
                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    shortestIndex = (short) ((j - 1) / 3);
                }
            }
            blockPalette[i] = shortestIndex;
            rgbPaletteIndex -= 3;
            j = (short) (shortestIndex * 3);
            blockPaletteRGB[rgbPaletteIndex++] = imagePalette[j++];
            blockPaletteRGB[rgbPaletteIndex++] = imagePalette[j++];
            blockPaletteRGB[rgbPaletteIndex++] = imagePalette[j];
        }
        blockColorsUsed = MAX_COLORS_IN_BLOCK;
    }

    /**
     * Applies block palette created with createBlockPalette() into current
     * master block. NOTE: After calling this method indices of
     * currentMasterBlock.pixels[] are indices of blockPalette, not
     * imagePalette.
     */
    private void applyBlockPalette() {
        int i;
        short j;
        short color;
        short r;
        short g;
        short b;
        short pr;
        short pg;
        short pb;
        int deltaR;
        int deltaG;
        int deltaB;
        int distance;
        int shortestDistance;
        short shortestIndex;
        int palettePointer;
        for (i = 0; i < MASTERBLOCK_AREA; i++) {
            color = currentMasterBlock.pixels[i];
            color *= 3;
            r = imagePalette[color++];
            g = imagePalette[color++];
            b = imagePalette[color];
            // Maximum distance in the RGB space is 255^2 + 255^2 + 255^2.
            // (256^2) * 3 = 196608
            shortestDistance = 196608;
            shortestIndex = 0;
            palettePointer = 0;
            for (j = 0; j < blockColorsUsed; j++) {
                pr = blockPaletteRGB[palettePointer++];
                pg = blockPaletteRGB[palettePointer++];
                pb = blockPaletteRGB[palettePointer++];
                deltaR = pr - r;
                deltaG = pg - g;
                deltaB = pb - b;
                distance = deltaR * deltaR + deltaG * deltaG + deltaB * deltaB;
                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    shortestIndex = j;
                }
            }
            currentMasterBlock.pixels[i] = shortestIndex;
        }
    }

    /**
     * Copies fullcolor RGB image data from a fullcolor image to
     * this.currentMasterBlockFullColor.
     *
     * @param image image in 24-bit RGB fullcolor.
     * @param xCorner the left edge of the block.
     * @param yCorner the top edge of the block.
     */
    private void copyFullColorBlockDataFromImage(Bitmap image, int xCorner,
            int yCorner) {
        int x;
        int y;
        int imageIndex;
        int blockIndex;
        int offset = (yCorner * image.width + xCorner) * 3;
        int blockScanline = MASTERBLOCK_WIDTH * 3;
        int scanlineFill = image.width * 3 - blockScanline;
        imageIndex = offset;
        blockIndex = 0;
        for (y = 0; y < MASTERBLOCK_WIDTH; y++) {
            for (x = 0; x < blockScanline; x++) {
                currentMasterBlockFullColor.pixels[blockIndex++] =
		    image.pixels[imageIndex++];
	
            }
            imageIndex += scanlineFill;
        }
    }

    //
    // Private methods: painting onto an image
    //
    
    /**
     * Paints blocks from source image to destination image according to
     * block change information provided by this.changedBlocks. Image width
     * and height must be multiples of BLOCK_WIDTH. Both images must be in
     * RGB fullcolor mode.
     * 
     * @param src image containing blocks to be painted.
     * @param dest image on where the changed blocks are painted.
     */
    private void paintChangedRegions(Bitmap src, Bitmap dest) {
        int blockX;
        int blockY;
        int blockIndex = 0;
        int x;
        int y;
        int pixelIndex;
        int blockScanline = BLOCK_WIDTH * 3;
        int scanline = widthInBlocks * BLOCK_WIDTH * 3;
        int scanlineAppend = scanline - blockScanline;

        // Go block by block
        for (blockY = 0; blockY < heightInBlocks; blockY++) {
            for (blockX = 0; blockX < widthInBlocks; blockX++) {

                // Paint a block if it is changing
                if (changedBlocks[blockIndex] == 1) {

                    pixelIndex = blockY * BLOCK_WIDTH * scanline + blockX
                            * BLOCK_WIDTH * 3;

                    for (y = 0; y < BLOCK_WIDTH; y++) {
                        for (x = 0; x < blockScanline; x++) {
                            dest.pixels[pixelIndex] = src.pixels[pixelIndex];
                            pixelIndex++;
                        }
                        pixelIndex += scanlineAppend;
                    }
                }
                blockIndex++;

            }
        }
    }

    /**
     * Encoder for pixel data.
     * 
     * @author Artturi Tilanterä.
     */

    private class PixelEncoder {
        
        private int masterBlockArea;
        private final static int MIN_LENGTH = 4;
        private final static int MAX_LENGTH = 128;
        private int maxRuns;
        
        private byte[] nibbles;
        private int[] runStarts;
        private int[] runLengths;
        private int bytesWritten;
        
        private int numberOfRuns;
           
        /**
         * Constructs a new PixelEncoder.
         * 
         * @param masterBlockArea area of a masterblock in pixels.
         */
        public PixelEncoder(int masterBlockArea) {
            this.masterBlockArea = masterBlockArea;
            this.maxRuns = masterBlockArea / MIN_LENGTH;
            this.nibbles = new byte[masterBlockArea * 2];
            this.runStarts = new int[maxRuns];
            this.runLengths = new int[maxRuns];
        }
        
        /**
         * Encodes pixel data of masterblock by run-length encoding.
         * 
         * @param masterblock masterblock to be encoded.
         * @param buffer buffer to write the encoded data.
         * @param startPos start position of writing the encoded data.
         * 
         * @return number of encoded bytes written.
         */
        public int encode(Bitmap masterblock, byte[] buffer, int startPos) {
            // The pixels of a masterblock are compressed as following
            // sequences of nibbles (sequences of four bits, a "half byte").
            // 1. Two nibbles with bits ABBB BBBB.
            // 1.a.1 If A = 0, next is (BBB BBBB) + 1 pixels of uncompressed data.
            // 1.a.2 Uncompressed data in nibbles: one nibble per pixel, which
            // are the color values for those pixels.
            // 1.b.1 If A = 1, next is (BBB BBBB) + 1 pixels of compressed data:
            // n adjacent pixels with the same color, n = 1..128.
            // 1.b.2 A nibble with bits CCCC which indicate the color of the
            // compressed pixels.
            // Pixels are stored as scanlines from left to right and scanlines
            // from top to bottom of a masterblock. This encoding compresses
            // sequences of adjacent bytes with the same color when the legnth of
            // the sequence is 4 or greater.
            
            // Compressed data is first written as nibbles (sequence of four
            // bits or half a byte representing value 0..15), which are
            // then combined to bytes. If the number of total nibbles is
            // odd, an extra padding of a nibble will be added at the end
            // to get sequence which length divisible in whole bytes.
            int nibble;               // index in this.nibbles
            int pixel;                // index in this.masterblock.pixels
            short color;                // 0 .. 15
            short previousColor;
                
            // Analyze pixel data: find compressed runs.
            // Minimum length of a run is four pixels. (Below it the data would
            // expand if it's run-length encoded at the worst case.)
            numberOfRuns = 0;        
            int runStart = 0;
            int runLength = 0;
            previousColor = masterblock.pixels[0];
            boolean runEnds = false;
            boolean breakByMaxLen = false;
            for (pixel = 0; pixel < masterBlockArea; pixel++) {
                color = masterblock.pixels[pixel];
                if (color == previousColor) {
                    runLength++;
                } else {                
                    runEnds = true;
                }
                if (runLength == MAX_LENGTH) {
                    runEnds = true;
                    breakByMaxLen = true;
                }            
                if (runEnds) {                
                    if (runLength >= MIN_LENGTH) {
                        runStarts[numberOfRuns] = runStart;
                        runLengths[numberOfRuns] = runLength;
                        numberOfRuns++;
                    }
                    if (breakByMaxLen) {
                        runStart = pixel + 1;
                        runLength = 0;
                        breakByMaxLen = false;
                    } else {
                        runStart = pixel;
                        runLength = 1;
                    }
                    runEnds = false;
                }
                previousColor = color;
            }
            // The last run that was ended by maximum pixel count, not a
            // changing color.
            if (runLength >= MIN_LENGTH) {
                runStarts[numberOfRuns] = runStart;
                runLengths[numberOfRuns] = runLength - 1;
                numberOfRuns++;
            }
            // Write compressed data.
            nibble = 0;
            pixel = 0;
            int i;
            int endI;
            int splitI;
            int sequenceLength;
            for (int run = 0; run < numberOfRuns; run++) {
                if (pixel < runStarts[run]) {
                    // Uncompressed data before the current run
                    i = pixel;
                    endI = runStarts[run];
                    sequenceLength = endI - i;
                    if (sequenceLength > MAX_LENGTH) {
                        splitI = i + MAX_LENGTH;
                        nibbles[nibble++] = 0x7;
                        nibbles[nibble++] = 0xF;
                        while (i < splitI) {
                            nibbles[nibble++] = (byte)masterblock.pixels[i++];                     
                        }
                        pixel += MAX_LENGTH;
                        sequenceLength -= MAX_LENGTH;                   
                    }
                    pixel += sequenceLength;
                    sequenceLength--; // Convert 1..128 to 0..127.
                    nibbles[nibble++] = (byte)((sequenceLength & 0x70) >> 4);
                    nibbles[nibble++] = (byte)(sequenceLength & 0x0F);
                    while (i < endI) {
                        nibbles[nibble++] = (byte)masterblock.pixels[i++];                     
                    }
                }
                // The current run
                sequenceLength = runLengths[run];
                pixel += sequenceLength;
                sequenceLength--; // Convert 1..128 to 0..127.            
                nibbles[nibble++] = (byte)(8 + ((sequenceLength & 0x70) >> 4));
                nibbles[nibble++] = (byte)(sequenceLength & 0x0F);
                nibbles[nibble++] = (byte)
                    (masterblock.pixels[runStarts[run]]);
            }
            // Uncompressed data after the last run
            if (pixel < masterBlockArea) {
                i = pixel;
                endI = masterBlockArea;            
                sequenceLength = endI - i;
                if (sequenceLength > MAX_LENGTH) {
                    splitI = i + MAX_LENGTH;
                    nibbles[nibble++] = 0x7;
                    nibbles[nibble++] = 0xF;
                    while (i < splitI) {
                        nibbles[nibble++] = (byte)masterblock.pixels[i++];                     
                    }
                    pixel += MAX_LENGTH;
                    sequenceLength -= MAX_LENGTH;                   
                }
                sequenceLength--; // Convert 1..128 to 0..127.
                nibbles[nibble++] = (byte)((sequenceLength & 0x70) >> 4);
                nibbles[nibble++] = (byte)(sequenceLength & 0x0F);
                while (i < endI) {
                    nibbles[nibble++] = (byte)masterblock.pixels[i++];                     
                }
            }
            // Combine nibbles to bytes and write to the buffer.
            // endI = compressedBlockLength = nibble / 2 rounded up.
            endI = nibble;
            if ((endI & 1) == 1) {
                endI++;
            }
            endI >>= 1;
            bytesWritten = endI;
            int pos = startPos;
            buffer[pos++] = (byte) bytesWritten;
            endI = pos + bytesWritten;
            nibble = 0;    
            for (i = pos; i < endI; i++) {
                buffer[i] = (byte)(nibbles[nibble++] << 4);
                buffer[i] += nibbles[nibble++];
            }
            return bytesWritten + 1;
        }   
    }
    
}
