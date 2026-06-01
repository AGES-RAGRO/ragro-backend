package br.com.ragro.service;

import java.io.InputStream;

/**
 * Conteúdo de um objeto de mídia lido do storage, pronto para ser transmitido pelo proxy de mídia
 * ({@code GET /media/**}). O {@code stream} deve ser fechado pelo consumidor após a transferência.
 */
public record MediaResource(InputStream stream, String contentType, long size) {}
