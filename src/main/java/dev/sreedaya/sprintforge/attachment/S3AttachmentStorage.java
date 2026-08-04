package dev.sreedaya.sprintforge.attachment;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@ConditionalOnProperty(name = "app.storage.s3.enabled", havingValue = "true")
public class S3AttachmentStorage implements AttachmentStorage {
    private final String bucket;
    private final Duration uploadTtl;
    private final S3Presigner presigner;

    public S3AttachmentStorage(
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.upload-ttl:PT10M}") Duration uploadTtl) {
        this.bucket = bucket;
        this.uploadTtl = uploadTtl;
        this.presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long sizeBytes) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(uploadTtl)
                .putObjectRequest(objectRequest)
                .build();
        var presigned = presigner.presignPutObject(request);
        return new PresignedUpload(
                java.net.URI.create(presigned.url().toString()),
                Map.of("Content-Type", contentType),
                Instant.now().plus(uploadTtl));
    }

    @PreDestroy
    void close() {
        presigner.close();
    }
}
