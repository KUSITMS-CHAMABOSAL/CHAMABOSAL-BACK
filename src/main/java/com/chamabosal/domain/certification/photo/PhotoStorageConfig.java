package com.chamabosal.domain.certification.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 버킷 설정이 있으면 S3, 없으면 메모리 저장소다. prod는 버킷이 필수라 메모리 저장소로 뜨지 않는다. */
@Configuration
public class PhotoStorageConfig {

    @Bean
    @ConditionalOnExpression("!'${storage.s3.bucket:}'.isBlank()")
    public PhotoStorage s3PhotoStorage(@Value("${storage.s3.bucket}") String bucket,
                                       @Value("${storage.s3.region}") String region) {
        return new S3PhotoStorage(bucket, region);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnExpression("'${storage.s3.bucket:}'.isBlank()")
    public PhotoStorage memoryPhotoStorage() {
        return new MemoryPhotoStorage();
    }
}
