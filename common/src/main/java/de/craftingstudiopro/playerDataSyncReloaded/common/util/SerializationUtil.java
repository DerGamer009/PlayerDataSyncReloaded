package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.Base64;

public class SerializationUtil {

    public static String toBase64(Object object) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(gzipStream)) {
            dataOutput.writeObject(object);
            dataOutput.flush();
            gzipStream.finish();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize and compress object", e);
        }
    }

    public static Object fromBase64(String data) throws IOException, ClassNotFoundException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            // Fallback for non-base64 or old format if needed, 
            // but here we assume modern format.
            throw new IOException("Invalid base64 data", e);
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             GZIPInputStream gzipStream = new GZIPInputStream(inputStream);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(gzipStream)) {
            return dataInput.readObject();
        } catch (IOException e) {
            // Fallback: try reading without GZIP if it was old format
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                return dataInput.readObject();
            } catch (Exception ex) {
                throw new IOException("Could not decompress or deserialize data", e);
            }
        }
    }
}
