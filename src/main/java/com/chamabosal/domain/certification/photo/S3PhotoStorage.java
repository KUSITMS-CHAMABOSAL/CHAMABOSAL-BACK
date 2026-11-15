package com.chamabosal.domain.certification.photo;

import java.time.Duration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** 접근 키는 SDK 기본 방식으로 환경 변수(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)에서 읽는다. */
public class S3PhotoStorage implements PhotoStorage, AutoCloseable {

    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3PhotoStorage(String bucket, String region) {
        this.bucket = bucket;
        this.client = S3Client.builder().region(Region.of(region)).build();
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
    }

    @Override
    public String presignUpload(String key, String contentType, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl).putObjectRequest(put).build()).url().toString();
    }

    @Override
    public String presignView(String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl).getObjectRequest(get).build()).url().toString();
    }

    /** 버킷 목록 권한이 없어 없는 원본도 403으로 온다. 둘 다 "없는 사진"이다 (CERT-04 9번). */
    @Override
    public boolean copy(String fromKey, String toKey) {
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket).sourceKey(fromKey)
                    .destinationBucket(bucket).destinationKey(toKey).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND || e.statusCode() == FORBIDDEN) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
