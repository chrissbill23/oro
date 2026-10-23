package com.asata.org.oro.attachment.file;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** a photo loses where, when and with what it was taken, never its pixels nor its orientation */
class ImageMetadataTest {

    static final String SECRET = "GPS 48.8566N 2.3522E, Pixel 9, Amani's phone";

    private static BufferedImage picture() {
        BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, (x * 16) << 16 | (y * 32) << 8 | 0x40);
            }
        }
        return image;
    }

    static byte[] encode(String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture(), format, out);
        return out.toByteArray();
    }

    private static byte[] strip(String type, byte[] file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageMetadata.strip(type, new ByteArrayInputStream(file), out);
        return out.toByteArray();
    }

    private static int[] pixels(byte[] file) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    static byte[] segment(int marker, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) marker, (byte) ((data.length + 2) >> 8), (byte) (data.length + 2)});
        out.writeBytes(data);
        return out.toByteArray();
    }

    /** an Exif segment: IFD0 with the orientation (6: turned) and a description holding the secret */
    static byte[] exif() {
        byte[] text = (SECRET + "\0").getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
        tiff.writeBytes(new byte[]{0, 2});
        // ImageDescription (0x010E), ASCII, its text after the IFD (offset 8 + 2 + 24 + 4 = 38)
        tiff.writeBytes(new byte[]{0x01, 0x0E, 0, 2, 0, 0, 0, (byte) text.length, 0, 0, 0, 38});
        // Orientation (0x0112), SHORT, 6
        tiff.writeBytes(new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0});
        tiff.writeBytes(new byte[]{0, 0, 0, 0});
        tiff.writeBytes(text);
        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        app1.writeBytes(tiff.toByteArray());
        return app1.toByteArray();
    }

    @Test
    void aJpegKeepsItsPixelsAndOrientationOnly() throws Exception {
        byte[] plain = encode("jpg");
        // JFIF (APP0) first, then the Exif, an XMP, a comment: what a phone and an editor leave
        int afterApp0 = 2 + 2 + ((plain[4] & 0xFF) << 8 | (plain[5] & 0xFF));
        ByteArrayOutputStream photo = new ByteArrayOutputStream();
        photo.write(plain, 0, afterApp0);
        photo.writeBytes(segment(0xE1, exif()));
        photo.writeBytes(segment(0xE1, ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>" + SECRET + "</x:xmpmeta>").getBytes(StandardCharsets.UTF_8)));
        photo.writeBytes(segment(0xFE, SECRET.getBytes(StandardCharsets.UTF_8)));
        photo.write(plain, afterApp0, plain.length - afterApp0);
        byte[] taken = photo.toByteArray();
        assertThat(new String(taken, StandardCharsets.ISO_8859_1)).contains(SECRET);

        byte[] clean = strip("image/jpeg", taken);

        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET).doesNotContain("xmpmeta");
        assertThat(pixels(clean)).isEqualTo(pixels(taken));
        // the orientation, alone, in its own Exif segment after the JFIF one
        int app1 = 2 + 2 + ((clean[4] & 0xFF) << 8 | (clean[5] & 0xFF));
        assertThat(clean[app1] & 0xFF).isEqualTo(0xFF);
        assertThat(clean[app1 + 1] & 0xFF).isEqualTo(0xE1);
        int length = (clean[app1 + 2] & 0xFF) << 8 | (clean[app1 + 3] & 0xFF);
        byte[] kept = java.util.Arrays.copyOfRange(clean, app1 + 4, app1 + 2 + length);
        assertThat(ImageMetadata.exifOrientation(kept)).isEqualTo(6);
        assertThat(ImageMetadata.exifOrientation(exif())).isEqualTo(6);
    }

    private static byte[] chunk(String type, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) (data.length >>> 24), (byte) (data.length >>> 16), (byte) (data.length >>> 8), (byte) data.length});
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(name);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        long value = crc.getValue();
        out.writeBytes(new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
        return out.toByteArray();
    }

    @Test
    void aPngKeepsItsPixelsWithoutItsTexts() throws Exception {
        byte[] plain = encode("png");
        // after the signature and IHDR (8 + 25 bytes): a text chunk, as an editor or a phone writes it
        ByteArrayOutputStream photo = new ByteArrayOutputStream();
        photo.write(plain, 0, 33);
        photo.writeBytes(chunk("tEXt", ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)));
        photo.writeBytes(chunk("eXIf", exif()));
        photo.write(plain, 33, plain.length - 33);
        byte[] taken = photo.toByteArray();

        byte[] clean = strip("image/png", taken);

        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
        assertThat(clean).isEqualTo(plain);
        assertThat(pixels(clean)).isEqualTo(pixels(taken));
    }

    @Test
    void aWebpLosesItsExifAndXmpChunksAndTheirFlags() throws Exception {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        // VP8X: flags (EXIF 0x08, XMP 0x04, alpha 0x10), then 3 reserved bytes and the size of the canvas
        chunks.writeBytes(new byte[]{'V', 'P', '8', 'X', 10, 0, 0, 0, 0x1C, 0, 0, 0, 15, 0, 0, 7, 0, 0});
        chunks.writeBytes(new byte[]{'V', 'P', '8', 'L', 5, 0, 0, 0, 0x2F, 1, 2, 3, 4, 0});
        byte[] exif = exif();
        chunks.writeBytes(new byte[]{'E', 'X', 'I', 'F', (byte) exif.length, 0, 0, 0});
        chunks.writeBytes(exif);
        if (exif.length % 2 == 1) {
            chunks.write(0);
        }
        byte[] xmp = SECRET.getBytes(StandardCharsets.UTF_8);
        chunks.writeBytes(new byte[]{'X', 'M', 'P', ' ', (byte) xmp.length, 0, 0, 0});
        chunks.writeBytes(xmp);
        if (xmp.length % 2 == 1) {
            chunks.write(0);
        }
        int size = 4 + chunks.size();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[]{'R', 'I', 'F', 'F', (byte) size, (byte) (size >>> 8), 0, 0, 'W', 'E', 'B', 'P'});
        chunks.writeTo(file);

        byte[] clean = strip("image/webp", file.toByteArray());

        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET).doesNotContain("EXIF").doesNotContain("XMP ");
        // the RIFF size is the file less its first 8 bytes; only the alpha flag is left
        assertThat((clean[4] & 0xFF) | (clean[5] & 0xFF) << 8).isEqualTo(clean.length - 8);
        assertThat(clean[20]).isEqualTo((byte) 0x10);
        assertThat(new String(clean, 30, 4, StandardCharsets.ISO_8859_1)).isEqualTo("VP8L");
    }

    @Test
    void whatIsNotTheImageItSaysIsRefused() {
        for (String type : java.util.List.of("image/jpeg", "image/png", "image/webp")) {
            assertThatThrownBy(() -> strip(type, "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                    .as(type).isInstanceOf(ImageMetadata.InvalidImage.class);
        }
        assertThat(ImageMetadata.cleans("image/gif")).isFalse();
    }
}
