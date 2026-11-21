package io.github.agustinaguitmand.drive.models.entities;

public enum TipoArchivo {
    IMAGEN,
    VIDEO,
    AUDIO,
    DOCUMENTO,
    OTRO;

    public static TipoArchivo desdeContentType(String contentType) {
        if (contentType == null) return OTRO;
        if (contentType.startsWith("image/")) return IMAGEN;
        if (contentType.startsWith("video/")) return VIDEO;
        if (contentType.startsWith("audio/")) return AUDIO;
        if (contentType.startsWith("text/") || contentType.equals("application/pdf")) return DOCUMENTO;
        return OTRO;
    }
}
