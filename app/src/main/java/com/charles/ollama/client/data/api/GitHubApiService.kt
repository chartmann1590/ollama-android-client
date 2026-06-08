package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.api.dto.*
import retrofit2.http.*

interface GitHubApiService {

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): GitHubIssueResponse

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): GitHubIssueResponse

    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): List<GitHubCommentResponse>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun postComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: PostCommentRequest
    ): GitHubCommentResponse

    @PUT("repos/{owner}/{repo}/contents/feedback-assets/{filename}")
    suspend fun uploadAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("filename") filename: String,
        @Body request: UploadContentRequest
    ): UploadContentResponse
}
