package org.spring.boot.bind;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class PostInfo {

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	private String title;
	private String content;

}