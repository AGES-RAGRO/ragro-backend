package com.google.firebase.messaging;

import com.google.firebase.ErrorCode;

/**
 * Test-only factory for {@link SendResponse} objects, living in the FCM SDK package so it can reach
 * the package-private factories. The project pins Mockito's {@code mock-maker-subclass}, which
 * cannot mock the {@code final} {@link SendResponse}/{@link FirebaseMessagingException} classes, so
 * real instances are built here instead.
 */
public final class FcmTestResponses {

  private FcmTestResponses() {}

  public static SendResponse success() {
    return SendResponse.fromMessageId("test-message-id");
  }

  public static SendResponse failure(MessagingErrorCode code) {
    FirebaseMessagingException base =
        new FirebaseMessagingException(ErrorCode.INVALID_ARGUMENT, "test failure");
    return SendResponse.fromException(
        FirebaseMessagingException.withMessagingErrorCode(base, code));
  }
}
