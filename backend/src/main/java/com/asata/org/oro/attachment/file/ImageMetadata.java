package com.asata.org.oro.attachment.file;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Removes the metadata of a photo, without decoding it (its pixels and its quality stay as they are): where it was
 * taken (GPS), with what, when, by whom, and its comments.
 * <ul>
 * <li>JPEG: the APP1 (Exif, XMP), APP12, APP13 (IPTC) and COM segments go; the orientation of the Exif is kept, alone, in
 * a new Exif segment (a photo of a phone is stored sideways and turned by it)</li>
 * <li>PNG: the eXIf, tEXt, zTXt, iTXt and tIME chunks go</li>
 * <li>WebP: the EXIF and XMP chunks go, and their flags in VP8X</li>
 * </ul>
 * A file that is not what its type says is refused (InvalidImage): it is never stored as it came.
 */
public final class ImageMetadata {

    /** the image is not a valid one of its type */
    public static class InvalidImage extends IOException {
        InvalidImage(String message) {
            super(message);
        }
    }

    private ImageMetadata() {
    }

    /** the types it cleans; the others are copied as they are by the caller */
    public static boolean cleans(String mimeType) {
        return Set.of("image/jpeg", "image/png", "image/webp").contains(mimeType);
    }

    public static void strip(String mimeType, InputStream in, OutputStream out) throws IOException {
        switch (mimeType) {
            case "image/jpeg" -> jpeg(new DataInputStream(in), out);
            case "image/png" -> png(new DataInputStream(in), out);
            case "image/webp" -> webp(new DataInputStream(in), out);
            default -> in.transferTo(out);
        }
    }

    // ---------- JPEG ----------

    private static void jpeg(DataInputStream in, OutputStream out) throws IOException {
        if (in.readUnsignedShort() != 0xFFD8) {
            throw new InvalidImage("not a JPEG");
        }
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        Integer orientation = null;
        boolean orientationWritten = false;
        while (true) {
            int marker = readMarker(in);
            if (marker == 0xD9) {
                out.write(new byte[]{(byte) 0xFF, (byte) 0xD9});
                return;
            }
            int length = in.readUnsignedShort();
            if (length < 2) {
                throw new InvalidImage("a JPEG segment too short");
            }
            byte[] data = in.readNBytes(length - 2);
            if (data.length != length - 2) {
                throw new InvalidImage("a JPEG cut short");
            }
            boolean exif = marker == 0xE1 && startsWith(data, "Exif\0\0");
            if (exif && orientation == null) {
                orientation = exifOrientation(data);
            }
            boolean dropped = marker == 0xE1 || marker == 0xEC || marker == 0xED || marker == 0xFE;
            if (!dropped) {
                // the orientation goes where the Exif was: after the JFIF segment (APP0), before the rest
                if (!orientationWritten && marker != 0xE0 && orientation != null) {
                    writeOrientation(out, orientation);
                    orientationWritten = true;
                }
                out.write(0xFF);
                out.write(marker);
                out.write(length >> 8);
                out.write(length);
                out.write(data);
            }
            if (marker == 0xDA) {
                // start of scan: the pixels, then the end, as they are
                in.transferTo(out);
                return;
            }
        }
    }

    private static int readMarker(DataInputStream in) throws IOException {
        int b = in.read();
        if (b != 0xFF) {
            throw new InvalidImage("not a JPEG segment");
        }
        int marker;
        do {
            marker = in.read();
        } while (marker == 0xFF);
        if (marker < 0) {
            throw new EOFException("a JPEG cut short");
        }
        return marker;
    }

    /** the orientation (1 to 8) of IFD0 of an Exif segment, or null */
    static Integer exifOrientation(byte[] app1) {
        int tiff = 6;
        if (app1.length < tiff + 8) {
            return null;
        }
        boolean little = app1[tiff] == 'I' && app1[tiff + 1] == 'I';
        if (!little && !(app1[tiff] == 'M' && app1[tiff + 1] == 'M')) {
            return null;
        }
        long ifd = u32(app1, tiff + 4, little);
        int entries = tiff + (int) ifd;
        if (ifd < 8 || entries + 2 > app1.length) {
            return null;
        }
        int count = u16(app1, entries, little);
        for (int i = 0; i < count; i++) {
            int entry = entries + 2 + 12 * i;
            if (entry + 12 > app1.length) {
                return null;
            }
            if (u16(app1, entry, little) == 0x0112) {
                int value = u16(app1, entry + 8, little);
                return value >= 1 && value <= 8 ? value : null;
            }
        }
        return null;
    }

    /** an Exif segment with the orientation only: a TIFF header (big endian), IFD0 of one entry, no next IFD */
    private static void writeOrientation(OutputStream out, int orientation) throws IOException {
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        exif.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});
        exif.writeBytes(new byte[]{0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0});
        byte[] data = exif.toByteArray();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) ((data.length + 2) >> 8), (byte) (data.length + 2)});
        out.write(data);
    }

    // ---------- PNG ----------

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> PNG_DROPPED = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private static void png(DataInputStream in, OutputStream out) throws IOException {
        byte[] signature = in.readNBytes(8);
        if (!Arrays.equals(signature, PNG)) {
            throw new InvalidImage("not a PNG");
        }
        out.write(signature);
        while (true) {
            int length = in.readInt();
            byte[] type = in.readNBytes(4);
            if (length < 0 || type.length != 4) {
                throw new InvalidImage("a PNG chunk cut short");
            }
            String name = new String(type, StandardCharsets.ISO_8859_1);
            byte[] data = in.readNBytes(length);
            byte[] crc = in.readNBytes(4);
            if (data.length != length || crc.length != 4) {
                throw new InvalidImage("a PNG cut short");
            }
            if (!PNG_DROPPED.contains(name)) {
                out.write(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
                out.write(type);
                out.write(data);
                out.write(crc);
            }
            if (name.equals("IEND")) {
                return;
            }
        }
    }

    // ---------- WebP ----------

    private static void webp(DataInputStream in, OutputStream out) throws IOException {
        byte[] header = in.readNBytes(12);
        if (header.length != 12 || !startsWith(header, "RIFF") || !new String(header, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
            throw new InvalidImage("not a WebP");
        }
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        long riffSize = u32(header, 4, true);
        long read = 4;
        while (read < riffSize) {
            byte[] chunkHeader = in.readNBytes(8);
            if (chunkHeader.length == 0) {
                break;
            }
            if (chunkHeader.length != 8) {
                throw new InvalidImage("a WebP chunk cut short");
            }
            String name = new String(chunkHeader, 0, 4, StandardCharsets.ISO_8859_1);
            long size = u32(chunkHeader, 4, true);
            if (size > Integer.MAX_VALUE - 1) {
                throw new InvalidImage("a WebP chunk too big");
            }
            byte[] data = in.readNBytes((int) size + (int) (size & 1));
            if (data.length != size + (size & 1)) {
                throw new InvalidImage("a WebP cut short");
            }
            read += 8 + data.length;
            if (name.equals("EXIF") || name.equals("XMP ")) {
                continue;
            }
            if (name.equals("VP8X") && data.length >= 1) {
                // the flags of the EXIF (bit 3) and XMP (bit 2) chunks, gone with them
                data[0] = (byte) (data[0] & ~0x0C);
            }
            chunks.write(chunkHeader);
            chunks.write(data);
        }
        long size = 4 + chunks.size();
        out.write(new byte[]{'R', 'I', 'F', 'F', (byte) size, (byte) (size >>> 8), (byte) (size >>> 16), (byte) (size >>> 24),
                'W', 'E', 'B', 'P'});
        chunks.writeTo(out);
    }

    // ---------- bytes ----------

    private static boolean startsWith(byte[] data, String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.ISO_8859_1);
        return data.length >= bytes.length && Arrays.equals(Arrays.copyOf(data, bytes.length), bytes);
    }

    private static int u16(byte[] b, int at, boolean little) {
        return little ? (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 : (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
    }

    private static long u32(byte[] b, int at, boolean little) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = little ? 8 * i : 8 * (3 - i);
            value |= (long) (b[at + i] & 0xFF) << shift;
        }
        return value;
    }
}
