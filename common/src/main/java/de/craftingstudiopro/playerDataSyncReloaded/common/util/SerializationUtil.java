package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SerializationUtil {

    public static String toBase64(Object object) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(object);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize object", e);
        }
    }

    public static Object fromBase64(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return dataInput.readObject();
        }
    }
}
