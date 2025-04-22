package anip;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Decoder for AP-format video files.
 * 
 * @author Artturi Tilanterä
 */

public class APDecoder {
    
    //
    // Contents of this class:
    //
    // Fields: constants (static fields)
    // Fields: General information on the file being decoded
    // Fields: Data applying to the whole current image being decoded
    // Fields: Data used in decoding single masterblock
    //
    // Public methods
    // Private methods: decoding a frame
    // Private methods: decoding a masterblock
    // Private methods: general
    //
 
    //
    // Constants
    //

    /**
     * <p>Width of a block. A block is a constant sized, square shaped
     * sub-image the encoded image consists of.</p>
     * 
     * <p>SUMMARY OF BLOCKS: The frame, a single image that will be encoded,
     * is split into constant-sized squares called master blocks. Master
     * blocks consist of smaller squares called blocks. The changes between
     * frames are handled in blocks while subsections of frame containing
     * local palettes are handled with size of master blocks.</p>
     */
    private final static int BLOCK_WIDTH = 8;

    /** Width of a master block. */
    private final static int MASTERBLOCK_WIDTH = BLOCK_WIDTH * 2;

    /** Area of a master block. */
    private final static int MASTERBLOCK_AREA = MASTERBLOCK_WIDTH *
    MASTERBLOCK_WIDTH;
    
    /** Half of the area area of a master block. */
    private final static int HALF_MASTERBLOCK_AREA = MASTERBLOCK_AREA / 2;

    /** Maximum colors in the local palette of one palette block. */
    private final static int MAX_COLORS_IN_BLOCK = 16;

    /** Maximum colors in a frame when it is in indexed color mode (has a
     * palette). 256 * color depth = 256 * 3 = 768. */
    private final static int FRAME_PALETTE_LENGTH = 768;
   
    /** Determines whether to use optimized versions of some methods. */
    private final static boolean USE_OPTIMIZATIONS = true;
    //
    // Fields
    //    

    // General information on the file being decoded.
    
    /**
     * A handle for reading the video file.
     */
    private RandomAccessFile file;
    
    /**
     * Determines whether the decoder has opened a file for decoding.
     */
    private boolean isDecoding;
    
    /** Width of the video image in pixels. */
    private short frameWidth;

    /** Height of the video image in pixels. */
    private short frameHeight;

    /** Video playing speed in frames per second. */
    private float fps;

    /** Number of frames in the video. */
    private int frameCount;

    /** Number of keyframes in the video. */
    private int keyframeCount;

    /** Number of the next frame that would be decoded. */
    private int nextFrame;

    //
    // Data applying to the whole current image being decoded
    //

    /** Data of one encoded frame as read from the file. */
    private byte[] encodedFrame;
    
    /** The size of current encoded frame in bytes. */
    private int encodedFrameLength;

    /** Current reading position in this.encodedFrame. */
    private int encodedFramePosition;
    
    /** The decoded frame as a 24-bit RGB image, type of
     * anip.Bitmap. */
    private Bitmap frameBuffer;

    /** Value is widthInMasterBlocks * MASTERBLOCK_WIDTH. */
    private int fixedWidthOfFrameBuffer;
   
    /** The decoded frame as a 24-bit RGB image, type of
     * an int array where data is coded in order
     * java.awt.image.BufferedImage.TYPE_INT_RGB. */
    private int[] rgbBuffer;

    /** Determines whether to use this.rgbBuffer instead of
     * this.frameBuffer. Value true means yes. */
    private boolean useRgbBuffer;

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
     * this.widthInBlocks * this.heightInBlocks. Values are: 0 = block
     * contains exactly the same pixel values as the block in the same
     * position in the previous frame; 1 = block is different from the
     * corresponding block in the previous frame.
     */
    private byte[] changedBlocks;
    
    /**
     * Same as this.changedBlocks, but for previous frame.
     * Used by compressRegionChanges().
     */
    private byte[] previousChangedBlocks;

    /** 256 color RGB palette for the current frame. */
    private short[] imagePalette;
    
    //
    // Data used in decoding single masterblock
    //
	
    /**
     * 16 color palette for the current masterblock. Values are indices of
     * this.imagePalette.
     */
    private short[] blockPalette;

    /**
     * 16 color palette for the current masterblock in RGB fullcolor,
     * all three components packed into one int.
     */
    private int[] blockPaletteRGB;

    /** Indicates whether the current masterblock has any sub-blocks that
     * are marked to be changing.
     */
    private boolean hasChangingBlocks;
    
    /** Indicates which blocks of the masterblock are changing. */
    private boolean[] changingQuarters;
    
    /**
     * Small bitmap in indexed color, for encoding the current master block.
     */
    private Bitmap currentMasterBlock;

    /**
     * Small bitmap that contains image data of this.currentMasterBlock
     * in RGB fullcolor.
     */
    private Bitmap currentMasterBlockFullColor;
    
    /**
     * Small bitmap in fullcolor, for encoding the current master block.
     * NOTE: size of this bitmap is BLOCK_WIDTH * BLOCK_WIDTH.
     * It represents a single quarter of the current master block.
     */
    private Bitmap blockFullColor;

    /**
     * Array of 4-bit integers used in compressing the pixels of a masterblock.
     */
    private byte[] nibbles;

    /** Number of bytes actually used in this.compressedBlock. */
    private int compressedBlockLength;
    
    //
    // Public methods
    //
    
    /**
     * Creates a new APDecoder.
     */
    public APDecoder() {
        isDecoding = false;
	imagePalette = new short[FRAME_PALETTE_LENGTH];

        encodedFrame = null;
        
	// Data used in encoding single masterblock
        blockPalette = new short[MAX_COLORS_IN_BLOCK];
	blockPaletteRGB = new int[MAX_COLORS_IN_BLOCK];

        changingQuarters = new boolean[4];
        
        currentMasterBlock = new Bitmap();
        currentMasterBlock.resize(MASTERBLOCK_WIDTH, MASTERBLOCK_WIDTH, 1);
        currentMasterBlockFullColor = new Bitmap();
        currentMasterBlockFullColor.resize(MASTERBLOCK_WIDTH,
                MASTERBLOCK_WIDTH, 3);
        blockFullColor = new Bitmap();
        blockFullColor.resize(BLOCK_WIDTH, BLOCK_WIDTH, 3);
        nibbles = new byte[1024];
    }
    
    /**
     * Opens a new video file for decoding.
     * 
     * @param fileName name of the video file.
     * 
     * @throws IOException if the file cannot be opened or is of wrong type.
     */
    public void openFile(String fileName) throws IOException {
        if (isDecoding) {
            throw new IOException("Already decoding a file!");
        }
        
        file = new RandomAccessFile(fileName, "r");
        readFileHeader();
        encodedFrame = new byte[frameWidth * frameHeight];
	changedBlocks = new byte[widthInBlocks * heightInBlocks];
        previousChangedBlocks = new byte[widthInBlocks * heightInBlocks];
	nextFrame = 0;
    }

    /**
     * Returns the speed of the video in frames per second.
     *
     * @return the speed of the video.
     */
    public float getSpeed() {
        return fps;
    }

    /**
     * Returns the length of the video in frames.
     *
     * @return the length of the video.
     */
    public int getLength() {
        return frameCount;
    }
    
    /**
     * Returns the actual width of the video image in pixels.
     *
     * @return the width of the video image.
     */
    public int getFrameWidth() {
        return frameWidth;
    }

    /**
     * Returns the actual height of the video image in pixels.
     *
     * @return the height of the video image.
     */
    public int getFrameHeight() {
        return frameHeight;
    }

    /**
     * Returns width of the frame buffer when using
     * getImage(int[] rgbBuffer).
     * 
     * @return width in pixels.
     */
    public int getBufferWidth() {
        return widthInMasterBlocks * MASTERBLOCK_WIDTH;
    }
    
    /**
     * Returns width of the frame buffer when using
     * getImage(int[] rgbBuffer).
     * 
     * @return width in pixels.
     */
    public int getBufferHeight() {
        return heightInMasterBlocks * MASTERBLOCK_WIDTH;
    }
    
    /**
     * Returns new Bitmap that is ready to be used with
     * getImage(Bitmap image).
     *
     * @return new Bitmap that has proper size.
     */
    public Bitmap createFrameBufferBitmap() {
	Bitmap buffer = new Bitmap();
	buffer.resize(widthInMasterBlocks * MASTERBLOCK_WIDTH,
	    heightInMasterBlocks * MASTERBLOCK_WIDTH, 3);
        return buffer;
    }

    /**
     * Returns size of an int[] array that can be used with
     * getImage(int[] rgbBuffer).
     *
     * @return size of the array in indices.
     */
    public int getFrameBufferArraySize() {
	return widthInMasterBlocks * heightInMasterBlocks *
	    MASTERBLOCK_AREA;
    }

    /**
     * Decodes next frame and stores its contents to the bitmap
     * given as a parameter. Image width and height must be according to
     * values returned by methods <code>getFrameWidth()</code> and
     * <code>getFrameHeight()</code>, and image depth must be 3.
     *
     * @param image the bitmap where the frame will be written to.
     *
     * @throws IOException
     */
    public void getImage(Bitmap image) throws IOException {
        if (image.width != this.getBufferWidth() ||
	    image.height != this.getBufferHeight() ||
                image.depth != 3) {
            throw new IOException("APDecoder.getImage: image is of wrong " +
                    "type. Size = " + image.width + " x " + image.height +
                    ", depth = " + image.depth + ". Should be " +
                    "size = " + this.getBufferWidth() + " x " +
		    this.getBufferHeight() + ", depth = 5.");
        }
	if (!isDecoding) {
	    throw new IOException("APDecoder.getImage: no file is opened.");
	}
	if (nextFrame < frameCount) {
	    useRgbBuffer = false;
	    frameBuffer = image;
	    decodeFrame();
	}
    }

    public void getImage(int[] rgbBuffer) throws IOException {
	if (!isDecoding) {
	    throw new IOException("APDecoder.getImage: no file is opened.");
	}
	if (nextFrame < frameCount) {
	    useRgbBuffer = true;
	    this.rgbBuffer = rgbBuffer;

	    if (USE_OPTIMIZATIONS) {
		decodeFrameOptimized();
	    } else {	    
		decodeFrame();
	    }
	}
    }
    
    /**
     * Moves to the position in the video file where the number of
     * the next frame that will be decoded is the one given as a
     * parameter.
     *
     * @param frame number of the next frame.
     *
     * @throws IOException
     */
    public void seek(int frame) throws IOException {
        if (frame == 0) {
            file.seek(21);
            nextFrame = 0;
        } else {
            throw new IOException("APDecoder.seek(): not yet " +
                    "implemented with values other than zero!");
        }
    }
    
    /**
     * Closes the file that was being decoded.
     *
     * @throws IOException
     */
    public void closeFile() throws IOException {
        if (isDecoding) {
            file.close();
        }
        isDecoding = false;
    }
    
    //
    // Private methods: decoding a frame
    //
    
    /**
     * Reads the header of the video file.
     * 
     * @throws IOException
     */
    private void readFileHeader() throws IOException {
        byte[] magicNumber = new byte[4];
        file.read(magicNumber);
        byte version = (byte) file.read();	
        if (!(magicNumber[0] == 0x41 && magicNumber[1] == 0x4E &&
                magicNumber[2] == 0x49 && magicNumber[3] == 0x50 &&
                version == 1) ) {
            throw new IOException("File is not an anip video file or " +
            "the version is not 1.");
        }
        frameCount = file.readInt();
        fps = file.readFloat();
        keyframeCount = file.readInt();
        setFrameSize(file.readShort(), file.readShort());
        isDecoding = true;
    }

    /**
     * Sets the width and height of the video image.
     * 
     * @param width width of the video image in pixels.
     * @param height height of the video image in pixels.
     */
    private void setFrameSize(short width, short height) {

        frameWidth = width;
        frameHeight = height;

        widthInBlocks = width / BLOCK_WIDTH;
        if (width % BLOCK_WIDTH != 0) {
            widthInBlocks++;
        }
        heightInBlocks = height / BLOCK_WIDTH;
        if (height % BLOCK_WIDTH != 0) {
            heightInBlocks++;
        }

        widthInMasterBlocks = width / MASTERBLOCK_WIDTH;
        if (width % MASTERBLOCK_WIDTH != 0) {
            widthInMasterBlocks++;
        }
        heightInMasterBlocks = height / MASTERBLOCK_WIDTH;
        if (height % MASTERBLOCK_WIDTH != 0) {
            heightInMasterBlocks++;
        }

        fixedWidthOfFrameBuffer = widthInMasterBlocks * MASTERBLOCK_WIDTH;
        
        changedBlocks = new byte[widthInBlocks * heightInBlocks];
    }    
   
    /**
     * Reads next frame from the video file.
     *
     * @return boolean value describing whether frame is a keyframe.
     */
    private boolean readFrame() throws IOException {
	int frameType = file.readByte();
        encodedFrameLength = file.readInt();
	if (frameType == 1) {
	    // Is keyframe
	    int keyframeNumber = file.readInt();
	    int prefKeyframeOffset = file.readInt();
	    int nextKeyframeOffset = file.readInt();
	}
        file.read(encodedFrame, 0, encodedFrameLength);
	encodedFramePosition = 0;
	nextFrame++;
	return (frameType == 1);
    }

    /**
     * Decodes the next frame in the video file.
     *
     * @throws IOException
     */
    private void decodeFrame() throws IOException {
	boolean isKeyFrame = readFrame();        
        decompressFramePalette();
        decompressRegionChanges(isKeyFrame);
	
	// Masterblocks that are changing
	int x;
	int y;
        for (y = 0; y < heightInMasterBlocks; y++) {            
            for (x = 0; x < widthInMasterBlocks; x++) {
                readMasterBlock((x << 1), (y << 1));
                if (hasChangingBlocks) {
                    convertBlockToImagePalette();                   
                    convertToFullcolor(currentMasterBlock,
                            currentMasterBlockFullColor);
                    
                    if (isKeyFrame) {
                        this.copyFullColorBlockDataToImage(frameBuffer,
                                x * MASTERBLOCK_WIDTH, y * MASTERBLOCK_WIDTH);
                    } else {
                        this.paintChangedRegionsOfMasterBlock(frameBuffer,
                                (x << 1), (y << 1));
                    }
                }                
	    }
	}
    }

    /**
     * Decodes the next frame in the video file. Optimized for
     * performance. This the rgbBuffer-only version.
     *
     * @throws IOException
     */
    private void decodeFrameOptimized() throws IOException {
	boolean isKeyFrame = readFrame();        
        decompressFramePalette();
        decompressRegionChanges(isKeyFrame);
	
	// Masterblocks that are changing.
	// This is quite an ugly contraption. :)
	int mbX;        // Iterates through masterblocks
	int mbY;        // Iterates through masterblocks
	int mbXend;
	int mbYend;
	int bX;         // Iterates through blocks inside a masterblock
	int bY;         // Iterates through blocks inside a masterblock
	int quarter;    // Iterates through blocks inside a masterblock
	int x;          // Iterates through pixels inside a block
	int y;          // Iterates through pixels inside a block
	int blockIndex; // Index of pixel in currentMasterBlock.pixels
	int imageIndex; // Index of pixel in rgbBuffer
	int scanlineFill = fixedWidthOfFrameBuffer - BLOCK_WIDTH;
	int colorIndex; // Index of color in imagePalette
	int r;
	int g;
	int b;
	int compressedLength; // For decoding run-length encoding of pixels
	int nibble;           // For decoding run-length encoding of pixels
	int pixel;            // For decoding run-length encoding of pixels 
	int sequenceLength;   // For decoding run-length encoding of pixels
	mbYend = heightInMasterBlocks * 2;
	mbXend = widthInMasterBlocks * 2;
        for (mbY = 0; mbY < mbYend; mbY += 2) {            
            for (mbX = 0; mbX < mbXend; mbX += 2) {

		// Determine if masterblock has changing parts
		// meaning the four blocks inside it.
		quarter = mbY * widthInBlocks + mbX;
		hasChangingBlocks = false;
		if (changedBlocks[quarter++] == 1) { // left-top
		    changingQuarters[0] = true;
		    hasChangingBlocks = true;
		} else {
		    changingQuarters[0] = false;
		}
		if (changedBlocks[quarter] == 1) {   // right-top
		    changingQuarters[1] = true;
		    hasChangingBlocks = true;
		} else {
		    changingQuarters[1] = false;
		}
		quarter += widthInBlocks;
		if (changedBlocks[quarter--] == 1) { // right-bottom
		    changingQuarters[3] = true;
		    hasChangingBlocks = true;
		} else {
		    changingQuarters[3] = false;
		}
		if (changedBlocks[quarter] == 1) {   // left-bottom
		    changingQuarters[2] = true;
		    hasChangingBlocks = true;
		} else {
		    changingQuarters[2] = false;
		}
 
		if (hasChangingBlocks) {
		    
		    // 1. Decompress palette
		    
	            for (x = 0; x < MAX_COLORS_IN_BLOCK; x++) {
		     colorIndex =
		      (encodedFrame[encodedFramePosition++] & 0xFF);
		     colorIndex *= 3;
		     r = imagePalette[colorIndex++];
		     g = imagePalette[colorIndex++];
		     b = imagePalette[colorIndex];
		     blockPaletteRGB[x] = (r << 16) | (g << 8) | b;
 		    }

		    // 2. Decompress pixels
		    compressedLength =
		     encodedFrame[encodedFramePosition++] & 0xFF;
		    // 2.1 Divide bytes to nibbles.
	            nibble = 0;
		    for (x = 0; x < compressedLength; x++) {
		     nibbles[nibble++] = (byte)
			 ((encodedFrame[encodedFramePosition] & 0xF0) >> 4);
		     nibbles[nibble++] = (byte)
			 (encodedFrame[encodedFramePosition] & 0x0F);
		     encodedFramePosition++;
		    }
		    // 2.2 Decode nibbles
		    nibble = 0;
		    for (pixel = 0; pixel < MASTERBLOCK_AREA;) {            
		     colorIndex = (nibbles[nibble++] << 4) & 0xF0;
		     colorIndex += nibbles[nibble++];
		     sequenceLength = (colorIndex & 0x7F) + 1;
		     if ((colorIndex & 0x80) == 0) {
                      // 2.2.1 Uncompressed sequence
		      for (x = 0; x < sequenceLength; x++) {
                       currentMasterBlock.pixels[pixel++] = nibbles[nibble++];
		      }
		     } else {                
		      // 2.2.2 Compressed sequence
		      colorIndex = nibbles[nibble++];
		      for (x = 0; x < sequenceLength; x++) {
                       currentMasterBlock.pixels[pixel++] = (short) colorIndex;
		      }                
		     }
		    }

		    // 3. Paint the masterblock
		    quarter = 0;
	            for (bY = 0; bY < 2; bY++) {
		     for (bX = 0; bX < 2; bX++) {
		      if (changingQuarters[quarter++] == true) {
		       // 3.1 Paint a block
                       blockIndex = bY * HALF_MASTERBLOCK_AREA +
                         bX * BLOCK_WIDTH;                    
		       imageIndex = (mbY + bY) * BLOCK_WIDTH *
		         fixedWidthOfFrameBuffer;
		       imageIndex += (mbX + bX) * BLOCK_WIDTH;
                        for (y = 0; y < BLOCK_WIDTH; y++) {
    	                    for (x = 0; x < BLOCK_WIDTH; x++) {
				colorIndex =
				    currentMasterBlock.pixels[blockIndex++];
				rgbBuffer[imageIndex++] =
				 blockPaletteRGB[colorIndex];
    			    }
                            blockIndex += BLOCK_WIDTH;
    			    imageIndex += scanlineFill;
                        }
		       } // Current block loops end
		      }
		     } // Current masterblock loops end
                }                
	    }
	} // Masterblocks of current frame loops end
    }


    /**
     * Reads frame palette from the video file.
     */
    private void decompressFramePalette() throws IOException {
        int i;
        for (i = 0; i < FRAME_PALETTE_LENGTH; i++) {
            imagePalette[i] = (short)
                (encodedFrame[encodedFramePosition++] & 0xFF);
        }
    }
    
    /**
     * Uncompresses block change information of the current frame
     * in this.encodedFrame to this.changedBlocks.
     * 
     * @param isKeyFrame determines whether the frame is a keyframe.
     */      
    private void decompressRegionChanges(boolean isKeyFrame) {
        int totalBlocks = widthInBlocks * heightInBlocks;
        int block = 0;
        
        if (isKeyFrame) {
            for (block = 0; block < totalBlocks; block++) {
                changedBlocks[block] = 1;
                previousChangedBlocks[block] = 1;
            }
            return;
        }

        // Writes contents of changedBlocks[] with one bit per index,
	// and round the data length up to the next whole byte.
        int value = encodedFrame[encodedFramePosition++];
        int mask = 128;
        int shift = 7;
        for (block = 0; block < totalBlocks; block++) {
            changedBlocks[block] = (byte)((value & mask) >> shift);
            mask >>= 1;
            shift--;            
            if (shift == -1) {
                value = encodedFrame[encodedFramePosition++];
                mask = 128;
                shift = 7;
            }
        }
        
    }
    
    //
    // Private methods: decoding a masterblock
    //

    /**
     * Reads a single masterblock from the video file.
     * 
     * @throws IOException
     */
    private void readMasterBlock(int startBlockX, int startBlockY)
    throws IOException {
        int blockX;
        int blockY;
        int i;
        int quarterIndex;    
        
        hasChangingBlocks = false;
        quarterIndex = 0;
        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {                
                i = (startBlockY + blockY) * widthInBlocks +
                    startBlockX + blockX;
                if (this.changedBlocks[i] == 1) {
                    changingQuarters[quarterIndex] = true;
                    hasChangingBlocks = true;
                } else {
                    changingQuarters[quarterIndex] = false;
                }
                quarterIndex++;
            }
        }
        
        if (hasChangingBlocks) {
            decompressMasterBlockPalette();
            decompressMasterBlockPixels();
        }
    }
  
    /**
     * Reads the palette of the current master block from the video file.
     * Decompresses palette if needed.
     */
    private void decompressMasterBlockPalette() {        
        int i;
        for (i = 0; i < MAX_COLORS_IN_BLOCK; i++) {
            blockPalette[i] = (short)
                (encodedFrame[encodedFramePosition++] & 0xFF);
        }
    }

    /**
     * Decompresses pixel information in this.compressedBlock to
     * this.currentMasterBlock.pixels.
     */
    private void decompressMasterBlockPixels() {
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
        int i;
        int nibble; // index in this.nibbles
        int pixel; // index in this.currentMasterBlock.pixels
        int color; // 0 .. 15
        int sequenceLength = 0; // in pixels

        compressedBlockLength = encodedFrame[encodedFramePosition++] & 0xFF;
        
        // Divide bytes to nibbles.
        nibble = 0;
        for (i = 0; i < compressedBlockLength; i++) {
            nibbles[nibble++] = (byte)
                ((encodedFrame[encodedFramePosition] & 0xF0) >> 4);
            nibbles[nibble++] = (byte)
                (encodedFrame[encodedFramePosition] & 0x0F);
            encodedFramePosition++;
        }

        nibble = 0;
        for (pixel = 0; pixel < MASTERBLOCK_AREA;) {            
            color = (nibbles[nibble++] << 4) & 0xF0;
            color += nibbles[nibble++];
            sequenceLength = (color & 0x7F) + 1;
            
            if ((color & 0x80) == 0) {
                // Uncompressed sequence
                for (i = 0; i < sequenceLength; i++) {
                    currentMasterBlock.pixels[pixel++] = nibbles[nibble++];
                }
            } else {                
                // Compressed sequence
                color = nibbles[nibble++];
                for (i = 0; i < sequenceLength; i++) {
                    currentMasterBlock.pixels[pixel++] = (short) color;
                }                
            }
        }
    }
    
    /**
     * Converts color indices of currentMasterBlock.pixels from indices of
     * blockPalette to indices of imagePalette.
     */
    private void convertBlockToImagePalette() {
        int i;
        for (i = 0; i < MASTERBLOCK_AREA; i++) {
            currentMasterBlock.pixels[i] =
                blockPalette[currentMasterBlock.pixels[i]];
        }
    }

    /**
     * Copies fullcolor RGB image data from this.currentMasterBlockFullcolor
     * to a fullcolor image. 
     * 
     * @param image image in 24-bit RGB fullcolor.
     * @param xCorner the left edge of the block.
     * @param yCorner the top edge of the block.
     */
    private void copyFullColorBlockDataToImage(Bitmap image,
            int xCorner, int yCorner) {
        int x;
        int y;
        int imageIndex;
        int blockIndex;
        int offset;
        int blockScanline;
        int scanlineFill;
        if (useRgbBuffer) {
            offset = yCorner * fixedWidthOfFrameBuffer + xCorner;
            blockScanline = MASTERBLOCK_WIDTH;
            scanlineFill = fixedWidthOfFrameBuffer - blockScanline;
        } else {
            offset = (yCorner * image.width + xCorner) * 3;
            blockScanline = MASTERBLOCK_WIDTH * 3;
            scanlineFill = image.width * 3 - blockScanline;
        }
        imageIndex = offset;
        blockIndex = 0;
        if (useRgbBuffer) {
            int value;
            for (y = 0; y < MASTERBLOCK_WIDTH; y++) {
                for (x = 0; x < blockScanline; x++) {
                    value = currentMasterBlockFullColor.pixels
                        [blockIndex++] << 16;
                    value = value | (currentMasterBlockFullColor.pixels
                        [blockIndex++] << 8);
                    value = value | (currentMasterBlockFullColor.pixels
                        [blockIndex++]);
                    rgbBuffer[imageIndex++] = value;
                }            
                imageIndex += scanlineFill;
            }            
        } else {
            for (y = 0; y < MASTERBLOCK_WIDTH; y++) {
                for (x = 0; x < blockScanline; x++) {
                    image.pixels[imageIndex++] =
                        currentMasterBlockFullColor.pixels[blockIndex++];
                }            
                imageIndex += scanlineFill;
            }
        }
    }
    
    /**
     * Copies fullcolor RGB image data from this.currentMasterBlockFullcolor
     * to a fullcolor image, and only parts according to this.changedBlocks.
     * 
     * @param image image in 24-bit RGB fullcolor.
     * @param startBlockX offset in blocks (not masterblocks) from the left
     * edge of the image.
     * @param startBlockY offset in blocks (not masterblocks) from the top
     * edge of the image.
      */
    private void paintChangedRegionsOfMasterBlock(Bitmap image,
            int startBlockX, int startBlockY) {
        
        int blockX;
        int blockY;
        int x;
        int y;
        int i;
        int blockIndex;
        int imageIndex;
	int blockScanline;
	int scanlineFill;
	int value;
	if (useRgbBuffer) {
	    blockScanline = BLOCK_WIDTH * 3;
	    scanlineFill = fixedWidthOfFrameBuffer - BLOCK_WIDTH;
	} else {
	    blockScanline = BLOCK_WIDTH * 3;
	    scanlineFill = image.width * 3 - blockScanline;
	}
       
        for (blockY = 0; blockY < 2; blockY++) {
            for (blockX = 0; blockX < 2; blockX++) {

                i = (startBlockY + blockY) * widthInBlocks + startBlockX +
                blockX;
                if (changedBlocks[i] == 1) {

                    // Loop for a single block. Now i is pointer to
                    // currentMasterBlock.pixels[] which represent the current
                    // master block, and i is going through a square
                    // shaped area (block) inside that master block.
                    blockIndex = blockY * HALF_MASTERBLOCK_AREA * 3 +
                        blockX * BLOCK_WIDTH * 3;
                    
		    if (useRgbBuffer) {
			imageIndex = (startBlockY + blockY) *
			    fixedWidthOfFrameBuffer * BLOCK_WIDTH;
			imageIndex += (startBlockX + blockX) * BLOCK_WIDTH;
                        for (y = 0; y < BLOCK_WIDTH; y++) {
    	                    for (x = 0; x < BLOCK_WIDTH; x++) {
	                        rgbBuffer[imageIndex++] =
				    (currentMasterBlockFullColor.pixels
				     [blockIndex] << 16) |
				    (currentMasterBlockFullColor.pixels
				     [blockIndex + 1] << 8) |
				    (currentMasterBlockFullColor.pixels
				    [blockIndex + 2]);
				blockIndex += 3;
    			    }
    			    imageIndex += scanlineFill;
                            blockIndex += blockScanline;
                        }
		    } else {
			imageIndex = (startBlockY + blockY) * BLOCK_WIDTH;
			imageIndex *= image.width;
			imageIndex += (startBlockX + blockX) * BLOCK_WIDTH;
			imageIndex *= 3;
                        for (y = 0; y < BLOCK_WIDTH; y++) {
	                    for (x = 0; x < blockScanline; x++) {
	                        image.pixels[imageIndex++] = 
		                    currentMasterBlockFullColor.pixels
		                        [blockIndex++];
                            }
	                    imageIndex += scanlineFill;
	                    blockIndex += blockScanline;
		        }
		    }

                } // if block was marked to be changing
            } // for BlockX
        } // for blockY
    }

    //
    // Private methods: general
    //

    /**
     * Converts an indexed color image to 24-bit image.
     * 
     * @param indexedImage image with 256 color indexed palette.
     * @param fullcolorImage 24-bit image with pixel values RGB RGB ..
     */
    private void convertToFullcolor(Bitmap indexedImage,
            Bitmap fullcolorImage) {

        int indexedIndex;
        int rgbPixelIndex;
        int end = indexedImage.width * indexedImage.height;
        short colorIndex;
        short r;
        short g;
        short b;
        rgbPixelIndex = 0;
        for (indexedIndex = 0; indexedIndex < end; indexedIndex++) {
            colorIndex = indexedImage.pixels[indexedIndex];
            colorIndex *= 3;
            r = imagePalette[colorIndex++];
            g = imagePalette[colorIndex++];
            b = imagePalette[colorIndex];
            fullcolorImage.pixels[rgbPixelIndex++] = r;
            fullcolorImage.pixels[rgbPixelIndex++] = g;
            fullcolorImage.pixels[rgbPixelIndex++] = b;
        }
    }
}
