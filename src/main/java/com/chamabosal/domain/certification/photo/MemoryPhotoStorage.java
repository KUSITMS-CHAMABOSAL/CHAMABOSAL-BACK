package com.chamabosal.domain.certification.photo;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 로컬·테스트용 저장소. 업로드 URL로는 실제로 올릴 수 없어 테스트는 upload로 올린 것처럼 만든다. */
public class MemoryPhotoStorage implements PhotoStorage {

    private final Set<String> keys = ConcurrentHashMap.newKeySet();

    @Override
    public String presignUpload(String key, String contentType, Duration ttl) {
        return "memory://upload/" + key;
    }

    @Override
    public String presignView(String key, Duration ttl) {
        return "memory://view/" + key;
    }

    @Override
    public boolean copy(String fromKey, String toKey) {
        if (!keys.contains(fromKey)) {
            return false;
        }
        keys.add(toKey);
        return true;
    }

    @Override
    public void delete(String key) {
        keys.remove(key);
    }

    public void upload(String key) {
        keys.add(key);
    }

    public boolean contains(String key) {
        return keys.contains(key);
    }

    public void clear() {
        keys.clear();
    }
}
