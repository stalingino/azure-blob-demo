package com.stalingino.azureblob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/files")
@Slf4j
public class FileController {

	public static final String CONNECTION_STRING = "";

	private static final String bucketName = "bucket";

	CloudStorageAccount storageAccount;
	CloudBlobClient blobClient = null;
	CloudBlobContainer container = null;

	@PostConstruct
	public void init() throws InvalidKeyException, URISyntaxException, StorageException {
		storageAccount = CloudStorageAccount.parse(CONNECTION_STRING);
		blobClient = storageAccount.createCloudBlobClient();
		container = blobClient.getContainerReference(bucketName);
		log.info("Creating container: " + container.getName());
		container.createIfNotExists(BlobContainerPublicAccessType.CONTAINER, new BlobRequestOptions(),
				new OperationContext());
	}

	public FileInfo storeFile(MultipartFile file, String componentPath, String category, String subCategory) {
		FileInfo fileInfo = new FileInfo();
		String filePath = UUID.randomUUID().toString();
		fileInfo.setFileId(filePath);
		fileInfo.setType(file.getContentType());
		fileInfo.setName(file.getOriginalFilename());
		fileInfo.setLength(file.getSize());
		fileInfo.setMetadata("");
		fileInfo.setCategory(category);
		fileInfo.setSubCategory(subCategory);
		fileInfo.setPath(filePath);
		fileInfo.setStorage("blob");
		try (InputStream fis = file.getInputStream()) {
			CloudBlockBlob blob = container.getBlockBlobReference(file.getName());
			HashMap<String, String> metadata = new HashMap<String, String>();
			metadata.put("name", file.getOriginalFilename());
			metadata.put("category", category);
			metadata.put("subCategory", subCategory);
			metadata.put("contentType", file.getContentType());
			blob.setMetadata(metadata);
			blob.upload(fis, file.getSize());
			fileInfo.setPath(blob.getUri().toString());
		} catch (IOException | StorageException | URISyntaxException e) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToSaveFile", e);
		}
		// fileInfoRepository.saveAndFlush(fileInfo);
		return fileInfo;
	}

	public FileInfo storeFile(byte[] fileBytes, String fileName, String contentType, String componentPath, String category, String subCategory) {
		FileInfo fileInfo = new FileInfo();
		String filePath = UUID.randomUUID().toString();
		fileInfo.setFileId(filePath);
		fileInfo.setType(contentType);
		fileInfo.setName(fileName);
		fileInfo.setLength((long) fileBytes.length);
		fileInfo.setMetadata("");
		fileInfo.setCategory(category);
		fileInfo.setSubCategory(subCategory);

		try {
			CloudBlockBlob blob = container.getBlockBlobReference(fileName);
			HashMap<String, String> metadata = new HashMap<String, String>();
			metadata.put("name", fileName);
			metadata.put("category", category);
			metadata.put("subCategory", subCategory);
			metadata.put("contentType", contentType);
			blob.setMetadata(metadata);
			blob.uploadFromByteArray(fileBytes, 0, fileBytes.length);
			fileInfo.setPath(filePath);
			fileInfo.setStorage("s3");
		} catch (IOException | StorageException | URISyntaxException e) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToSaveFile", e);
		}
		// fileInfoRepository.saveAndFlush(fileInfo);
		return fileInfo;
	}

	@PostMapping("/upload")
	public FileInfo storeFile(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "category", required = true) String category,
			@RequestParam(value = "subCategory", required = true) String subCategory) {
		return storeFile(file, null, category, subCategory);
	}

	@PostMapping("/upload/base64")
	public FileInfo storeFile(@RequestParam("file") String fileStream,
			@RequestParam(value = "type", required = true) String category,
			@RequestParam(value = "subType", required = true) String subCategory,
			@RequestParam(value = "name", required = true) String fileName,
			@RequestParam(value = "contentType", required = true) String contentType) {
		return storeFile(Base64.getMimeDecoder().decode(fileStream), fileName, contentType, null, category, subCategory);
	}
}
