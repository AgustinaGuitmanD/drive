package io.github.agustinaguitmand.drive.storage;

public record BlobGuardado(String storageKey, String sha256, long tamanio) { }