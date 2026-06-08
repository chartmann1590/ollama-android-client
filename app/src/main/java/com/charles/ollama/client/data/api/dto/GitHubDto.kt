package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class CreateIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String> = listOf("bug", "in-app-feedback")
)

data class PostCommentRequest(
    val body: String
)

data class UploadContentRequest(
    val message: String,
    val content: String // Base64 encoded
)

data class GitHubIssueResponse(
    val number: Int,
    val title: String,
    val state: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("html_url") val htmlUrl: String,
    val body: String?
)

data class GitHubCommentResponse(
    val id: Long,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
    val user: GitHubUser
)

data class GitHubUser(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class UploadContentResponse(
    val content: GitHubContentInfo
)

data class GitHubContentInfo(
    val name: String,
    val path: String,
    @SerializedName("download_url") val downloadUrl: String
)
