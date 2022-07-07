package com.stalingino.azureblob;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class FileInfoRepository {

    private Map<String, FileInfo> fileInfos = new HashMap<>();

    public void saveAndFlush(FileInfo fileInfo) {
        this.fileInfos.put(fileInfo.getFileId(), fileInfo);
    }

    public FileInfo findOneByFileId(String fileId) {
        if (fileInfos.containsKey(fileId)) return fileInfos.get(fileId);
        throw new RuntimeException("file ID not found in cache");
    }

    public void clearCache() {
        fileInfos.clear();
    }

    public void delete(FileInfo fileInfo) {
        fileInfos.remove(fileInfo.getFileId());
    }

    public Map<String, FileInfo> findAll() {
        return fileInfos;
    }

}
