package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.api.dto.*
import retrofit2.http.*

/**
 * Talks to the cloudflare-worker/ feedback relay, not api.github.com directly. See
 * NetworkModule.provideGitHubRetrofit and cloudflare-worker/src/index.ts.
 */
interface GitHubApiService {

    @POST("issue")
    suspend fun createIssue(@Body request: CreateIssueRequest): GitHubIssueResponse

    @GET("issue/{number}")
    suspend fun getIssue(@Path("number") number: Int): GitHubIssueResponse

    @GET("issue/{number}/comments")
    suspend fun getComments(@Path("number") number: Int): List<GitHubCommentResponse>

    @POST("issue/{number}/comments")
    suspend fun postComment(
        @Path("number") number: Int,
        @Body request: PostCommentRequest
    ): GitHubCommentResponse

    @POST("upload-image")
    suspend fun uploadAsset(@Body request: UploadContentRequest): UploadContentResponse
}
