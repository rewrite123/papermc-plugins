package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

final class ScanState {
    private final Path file;
    private final Properties values = new Properties();

    ScanState(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) values.load(new StringReader(Files.readString(file)));
        String mode = values.getProperty("mode", "normal");
        if (!mode.equals("normal") && !mode.equals("uninstall")) throw new IOException("Invalid migration mode: " + mode);
    }

    boolean uninstall() {
        return "uninstall".equals(values.getProperty("mode"));
    }

    boolean complete() {
        return Boolean.parseBoolean(values.getProperty("complete", "false"));
    }

    int cursor(String region) {
        int result = Integer.parseInt(values.getProperty("region." + region, "0"));
        if (result < 0 || result > 1024) throw new IllegalStateException("Invalid scan cursor for " + region);
        return result;
    }

    void cursor(String region, int value) {
        values.setProperty("region." + region, Integer.toString(value));
    }

    void reset(boolean uninstall) throws IOException {
        values.clear();
        values.setProperty("mode", uninstall ? "uninstall" : "normal");
        values.setProperty("complete", "false");
        save();
    }

    void complete(boolean complete) throws IOException {
        values.setProperty("complete", Boolean.toString(complete));
        save();
    }

    void save() throws IOException {
        Files.createDirectories(file.getParent());
        StringWriter text = new StringWriter();
        values.store(text, "BetterBarrels migration progress; do not edit while server is running");
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = java.nio.charset.StandardCharsets.UTF_8.encode(text.toString());
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
