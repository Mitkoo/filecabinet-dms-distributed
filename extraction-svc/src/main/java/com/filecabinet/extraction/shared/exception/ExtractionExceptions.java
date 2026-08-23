package com.filecabinet.extraction.shared.exception;

public final class ExtractionExceptions {

    private ExtractionExceptions() {
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String message) {
            super(message);
        }
    }

    public static class ExtractionFailedException extends RuntimeException {
        public ExtractionFailedException(String message) {
            super(message);
        }

        public ExtractionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
