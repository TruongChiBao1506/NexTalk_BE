package iuh.fit.se.nextalk_be.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class FileContentInspector {
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "com", "msi", "ps1", "sh", "js", "jar",
            "html", "htm", "svg", "docm", "dotm", "xlsm", "xltm", "pptm", "potm");
    private static final Set<String> BASIC_MODE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/webm",
            "text/plain"
    );

    public void validate(byte[] bytes, String declaredContentType, String fileName) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        String extension = extension(fileName);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Executable, active, and macro-enabled files are not allowed");
        }

        String contentType = declaredContentType == null
                ? ""
                : declaredContentType.toLowerCase(Locale.ROOT);
        boolean valid = switch (contentType) {
            case "image/jpeg" -> starts(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a");
            case "image/webp" -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
            case "video/mp4", "audio/mp4", "video/quicktime" -> ascii(bytes, 4, "ftyp");
            case "video/webm" -> starts(bytes, 0x1A, 0x45, 0xDF, 0xA3);
            case "audio/mpeg" -> ascii(bytes, 0, "ID3")
                    || (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0);
            case "audio/ogg" -> ascii(bytes, 0, "OggS");
            case "audio/wav" -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE");
            case "audio/webm" -> starts(bytes, 0x1A, 0x45, 0xDF, 0xA3);
            case "application/pdf" -> ascii(bytes, 0, "%PDF-");
            case "application/zip",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    starts(bytes, 0x50, 0x4B, 0x03, 0x04)
                            || starts(bytes, 0x50, 0x4B, 0x05, 0x06)
                            || starts(bytes, 0x50, 0x4B, 0x07, 0x08);
            case "text/plain" -> isPlainText(bytes);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("File content does not match its declared type");
        }
    }

    public void validateBasicMode(String declaredContentType) {
        String contentType = declaredContentType == null
                ? ""
                : declaredContentType.toLowerCase(Locale.ROOT);
        if (!BASIC_MODE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "This file type requires malware scanning and is unavailable in basic upload mode");
        }
    }

    private boolean isPlainText(byte[] bytes) {
        int sample = Math.min(bytes.length, 8192);
        for (int index = 0; index < sample; index++) {
            int value = bytes[index] & 0xFF;
            if (value == 0) return false;
            if (value < 0x09 || (value > 0x0D && value < 0x20)) return false;
        }
        return true;
    }

    private boolean starts(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[index] & 0xFF) != expected[index]) return false;
        }
        return true;
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] marker = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + marker.length) return false;
        for (int index = 0; index < marker.length; index++) {
            if (bytes[offset + index] != marker[index]) return false;
        }
        return true;
    }

    private String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
