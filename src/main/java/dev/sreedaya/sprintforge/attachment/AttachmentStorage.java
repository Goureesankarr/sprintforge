package dev.sreedaya.sprintforge.attachment;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public interface AttachmentStorage {
    PresignedUpload presignUpload(String objectKey, String contentType, long sizeBytes);

    record PresignedUpload(URI url, Map<String, String> headers, Instant expiresAt) {
        public PresignedUpload {
            headers = Map.copyOf(headers);
        }

        @Override
        public Map<String, String> headers() {
            return Map.copyOf(headers);
        }
    }
}
