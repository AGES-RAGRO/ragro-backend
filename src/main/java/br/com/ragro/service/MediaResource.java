package br.com.ragro.service;

import java.io.InputStream;

/**
 * A media object read from storage, ready to be streamed by the media proxy ({@code GET
 * /media/**}). The consumer must close {@code stream} after the transfer.
 */
public record MediaResource(InputStream stream, String contentType, long size) {}
