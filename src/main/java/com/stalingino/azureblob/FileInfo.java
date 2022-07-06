package com.stalingino.azureblob;

import java.io.Serializable;

import lombok.Data;

@Data
public class FileInfo implements Serializable{
	private Long id;
	private String path;
	private String name;
	private Long length;
	private String type;
	private String metadata;
	private String fileId;
	private String category;
	private String subCategory;
	private String storage;
	private String partnerCode;
}
