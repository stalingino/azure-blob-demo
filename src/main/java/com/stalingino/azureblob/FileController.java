package com.stalingino.azureblob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	@Value("${azure.connectionString}")
	private String connectionString;

	private static final String BUCKET_NAME = "bucket";

	CloudStorageAccount storageAccount;
	CloudBlobClient blobClient = null;
	CloudBlobContainer container = null;

	@Autowired
	FileInfoRepository fileInfoRepository;

	@PostConstruct
	public void init() throws InvalidKeyException, URISyntaxException, StorageException {
		storageAccount = CloudStorageAccount.parse(connectionString);
		blobClient = storageAccount.createCloudBlobClient();
		container = blobClient.getContainerReference(BUCKET_NAME);
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
			CloudBlockBlob blob = container.getBlockBlobReference(filePath);
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
		fileInfoRepository.saveAndFlush(fileInfo);
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
		fileInfo.setStorage("blob");
		try {
			CloudBlockBlob blob = container.getBlockBlobReference(filePath);
			HashMap<String, String> metadata = new HashMap<String, String>();
			metadata.put("name", fileName);
			metadata.put("category", category);
			metadata.put("subCategory", subCategory);
			metadata.put("contentType", contentType);
			blob.setMetadata(metadata);
			blob.uploadFromByteArray(fileBytes, 0, fileBytes.length);
			fileInfo.setPath(blob.getUri().toString());
		} catch (IOException | StorageException | URISyntaxException e) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToSaveFile", e);
		}
		fileInfoRepository.saveAndFlush(fileInfo);
		return fileInfo;
	}

	@GetMapping("/stream/{fileId}")
	public void streamFile(@PathVariable String fileId,  @RequestParam(value = "view", required = false) Boolean isView, HttpServletResponse response) {
		FileInfo fileInfo = fileInfoRepository.findOneByFileId(fileId);
		if(fileInfo == null) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToFindFileWithId" + fileId);
		}

		response.setHeader("Content-Type", fileInfo.getType());
		if(fileInfo.getLength() != 0)
			response.setHeader("Content-Length", "" + fileInfo.getLength());
		if (isView != null && isView)
			response.setHeader("Content-Disposition", "filename=" + fileInfo.getName());
		else
			response.setHeader("Content-Disposition", "attachment;filename=" + fileInfo.getName());

		try {
			CloudBlockBlob blob = container.getBlockBlobReference(fileId);
			blob.download(response.getOutputStream());
		} catch (IOException | StorageException | URISyntaxException e) {
			log.warn("Unable to stream file with id:" + fileId + " message: "+ e.getMessage());
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToStreamFile" + e + fileId);
		}
	}

	private FileInfo getFileInfo(String fileId) {
		FileInfo fileInfo = new FileInfo();
		fileInfo.setFileId(fileId);
		return fileInfo;
	}

	@DeleteMapping("/delete/{fileId}")
	public void deleteFile(@PathVariable String fileId) {
		FileInfo fileInfo = fileInfoRepository.findOneByFileId(fileId);
		if(fileInfo == null) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToFindFileWithId" + fileId);
		}
		try {
			CloudBlockBlob blob = container.getBlockBlobReference(fileId);
			blob.deleteIfExists();
		} catch (StorageException | URISyntaxException e) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.unableToDelete" + e + fileId);
		}
		fileInfoRepository.delete(fileInfo);
	}

	@GetMapping("/read/{fileId}")
	public byte[] readExistingFile(@PathVariable String fileId, HttpServletResponse response) {
		FileInfo fileInfo = fileInfoRepository.findOneByFileId(fileId);
		if(fileInfo == null) {
			throw new RuntimeException("com.sensei.app.filemanagerservice.failedToFindFileWithId" + fileId);
		}
		response.setHeader("Content-Type", fileInfo.getType());
		response.setHeader("Content-Disposition", "attachment;filename=" + fileInfo.getName());
		if(fileInfo.getLength() != 0)
			response.setHeader("Content-Length", "" + fileInfo.getLength());

		try  {
			CloudBlockBlob blob = container.getBlockBlobReference(fileId);
			byte[] buffer = new byte[fileInfo.getLength().intValue()];
			blob.downloadToByteArray(buffer, 0);
			return buffer;
		} catch (Exception e) {
			log.warn("Unable to read file with id:" + fileId + " message: "+ e);
			throw new RuntimeException("Unable to read file with id:" + fileId + " message: "+ e.getMessage());
		}
	}

	@PostMapping("/upload")
	public FileInfo storeFile(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "category", required = true) String category,
			@RequestParam(value = "subCategory", required = true) String subCategory) {
		return storeFile(file, null, category, subCategory);
	}

	@PostMapping("/upload/base64")
	public FileInfo storeFile(@RequestParam("file") String fileStream,
			@RequestParam(value = "category", required = true) String category,
			@RequestParam(value = "subCategory", required = true) String subCategory,
			@RequestParam(value = "name", required = true) String fileName,
			@RequestParam(value = "contentType", required = true) String contentType) {
		return storeFile(Base64.getMimeDecoder().decode(fileStream), fileName, contentType, null, category, subCategory);
	}

	@GetMapping("/clearCache")
	public String clearCache() {
		fileInfoRepository.clearCache();
		return "cache cleared";
	}

	@GetMapping("/listCache")
	public Map<String, FileInfo> listCache() {
		return fileInfoRepository.findAll();
	}
}
