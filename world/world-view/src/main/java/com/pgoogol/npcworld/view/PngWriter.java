package com.pgoogol.npcworld.view;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/** Writes a JavaFX image as an 8-bit RGB PNG, without the javafx-swing dependency. */
final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private PngWriter() {
    }

    static void write(WritableImage image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            byte[] row = new byte[1 + width * 3];
            for (int y = 0; y < height; y++) {
                row[0] = 0; // filter type: none
                for (int x = 0; x < width; x++) {
                    int argb = pixels.getArgb(x, y);
                    row[1 + x * 3] = (byte) (argb >> 16);
                    row[2 + x * 3] = (byte) (argb >> 8);
                    row[3 + x * 3] = (byte) argb;
                }
                deflater.write(row);
            }
        }

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.write(new byte[] {8, 2, 0, 0, 0}); // 8 bits per channel, RGB, default compression/filter/interlace

        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(SIGNATURE);
            writeChunk(out, "IHDR", header.toByteArray());
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        stream.write(typeBytes);
        stream.write(data);
        stream.writeInt((int) crc.getValue());
        stream.flush();
    }
}
