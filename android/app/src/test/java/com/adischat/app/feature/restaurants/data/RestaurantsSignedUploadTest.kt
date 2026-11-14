package com.adischat.app.feature.restaurants.data

import com.adischat.app.feature.restaurants.center.RemoteRestaurantCenterDataSource
import com.adischat.app.feature.restaurants.center.RestaurantCenterCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class RestaurantsSignedUploadTest {
    private var server: LocalHttpMockServer? = null
    private var activeUid: String? = "uid-partner-1"

    private data class RecordedHttpPut(
        val method: String,
        val path: String,
        val targetWithQuery: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private data class MockHttpResponse(
        val statusCode: Int,
        val body: ByteArray = ByteArray(0),
    )

    @Before
    fun setUp() {
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
        activeUid = "uid-partner-1"
        seedSession("uid-partner-1")
    }

    @After
    fun tearDown() {
        server?.stop()
        server = null
        RestaurantsSessionMemory.finishSignOut()
        RestaurantsSessionMemory.clear()
    }

    @Test
    fun `successful signed upload completes binary PUT before doc confirm menu attach and media save`() = runBlocking {
        val puts = CopyOnWriteArrayList<RecordedHttpPut>()
        val events = CopyOnWriteArrayList<String>()
        val baseUrl = startHttpServer { req ->
            puts.add(req)
            events.add("put:${req.path}")
            MockHttpResponse(200, """{"Key":"ok"}""".toByteArray(StandardCharsets.UTF_8))
        }

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse {
                events.add("mint-doc")
                return DocumentUploadUrlResponse(
                    ok = true,
                    uploadUrl = "$baseUrl/object/upload/sign/restaurant-private-docs/applications/app-1/business_license/doc-1.pdf?token=doc-tok-1",
                    storagePath = "applications/app-1/business_license/doc-1.pdf",
                    docType = body.docType,
                )
            }

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                events.add("confirm-doc")
                return ConfirmDocumentResponse(
                    ok = true,
                    document = ApplicationDocumentDto(
                        id = "doc-row-1",
                        applicationId = body.applicationId,
                        docType = body.docType,
                        storagePath = body.storagePath,
                        fileName = body.fileName,
                        mimeType = body.mimeType,
                        byteSize = body.byteSize,
                    ),
                )
            }

            override suspend fun requestMenuPhotoUploadUrl(
                bearerAuth: String,
                action: String,
                body: PhotoUploadUrlRequest,
            ): MediaUploadUrlResponse {
                val suffix = if (body.fileName.endsWith(".png")) "logo.png" else "dish.webp"
                events.add("mint-photo:$suffix")
                return MediaUploadUrlResponse(
                    ok = true,
                    uploadUrl = "$baseUrl/object/upload/sign/restaurant-public-media/restaurants/rest-1/$suffix?token=photo-tok",
                    storagePath = "restaurants/rest-1/$suffix",
                )
            }

            override suspend fun attachMenuItemImage(
                bearerAuth: String,
                action: String,
                body: AttachMenuItemImageRequest,
            ): MenuItemImageMutationResponse {
                events.add("attach-menu-image")
                return MenuItemImageMutationResponse(
                    ok = true,
                    image = MenuItemImageDto(
                        id = "img-row-1",
                        restaurantId = body.restaurantId,
                        menuItemId = body.menuItemId,
                        storagePath = body.storagePath,
                        isPrimary = body.isPrimary ?: false,
                        sortOrder = body.sortOrder ?: 0,
                    ),
                )
            }

            override suspend fun requestApplicationMediaUploadUrl(
                bearerAuth: String,
                action: String,
                body: ApplicationMediaUploadUrlRequest,
            ): MediaUploadUrlResponse {
                events.add("mint-app-media")
                return MediaUploadUrlResponse(
                    ok = true,
                    uploadUrl = "$baseUrl/object/upload/sign/restaurant-public-media/applications/app-1/media/cover.jpg?token=media-tok",
                    storagePath = "applications/app-1/media/cover.jpg",
                )
            }

            override suspend fun updateRestaurantSettings(
                bearerAuth: String,
                action: String,
                body: UpdateRestaurantSettingsRequest,
            ): RestaurantSettingsResponse {
                events.add("update-settings")
                return RestaurantSettingsResponse(
                    ok = true,
                    restaurant = RestaurantSummaryDto(
                        id = body.restaurantId,
                        name = "Hearth",
                        description = body.description ?: "",
                        logoPath = body.logoPath,
                        coverPath = body.coverPath,
                        status = "active",
                    ),
                )
            }
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)

        val pdfBytes = "%PDF-1.4\nbinary-doc".toByteArray(StandardCharsets.UTF_8)
        val docRes = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "business_license",
            source = RestaurantsBinaryUploadSource.fromBytes("license.pdf", "application/pdf", pdfBytes),
        )
        assertTrue(docRes.isSuccess)
        assertEquals("applications/app-1/business_license/doc-1.pdf", docRes.getOrNull()?.storagePath)

        val dishBytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x10, 0x20)
        val photoRes = repo.uploadAndAttachMenuItemImage(
            restaurantId = "rest-1",
            menuItemId = "item-1",
            source = RestaurantsBinaryUploadSource.fromBytes("dish.webp", "image/webp", dishBytes),
            isPrimary = true,
            sortOrder = 0,
        )
        assertTrue(photoRes.isSuccess)
        assertEquals("restaurants/rest-1/dish.webp", photoRes.getOrNull()?.storagePath)

        val appCoverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val appMediaRes = repo.uploadApplicationMedia(
            applicationId = "app-1",
            source = RestaurantsBinaryUploadSource.fromBytes("cover.jpg", "image/jpeg", appCoverBytes),
        )
        assertTrue(appMediaRes.isSuccess)
        assertEquals("applications/app-1/media/cover.jpg", appMediaRes.getOrNull())

        val logoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val logoRes = repo.uploadAndApplyRestaurantMedia(
            restaurantId = "rest-1",
            slot = RestaurantMediaSlot.LOGO,
            source = RestaurantsBinaryUploadSource.fromBytes("logo.png", "image/png", logoBytes),
            description = "Updated Hearth",
        )
        assertTrue(logoRes.isSuccess)
        assertEquals("restaurants/rest-1/logo.png", logoRes.getOrNull()?.logoPath)

        assertEquals(
            listOf(
                "mint-doc",
                "put:/object/upload/sign/restaurant-private-docs/applications/app-1/business_license/doc-1.pdf",
                "confirm-doc",
                "mint-photo:dish.webp",
                "put:/object/upload/sign/restaurant-public-media/restaurants/rest-1/dish.webp",
                "attach-menu-image",
                "mint-app-media",
                "put:/object/upload/sign/restaurant-public-media/applications/app-1/media/cover.jpg",
                "mint-photo:logo.png",
                "put:/object/upload/sign/restaurant-public-media/restaurants/rest-1/logo.png",
                "update-settings",
            ),
            events,
        )

        assertEquals(4, puts.size)
        assertTrue(puts.all { it.method == "PUT" })
        assertTrue(puts.all { it.headers["x-upsert"] == "false" })
        assertArrayEquals(pdfBytes, puts[0].body)
        assertEquals("application/pdf", puts[0].headers["content-type"])
        assertArrayEquals(dishBytes, puts[1].body)
        assertEquals("image/webp", puts[1].headers["content-type"])
        assertArrayEquals(appCoverBytes, puts[2].body)
        assertEquals("image/jpeg", puts[2].headers["content-type"])
        assertArrayEquals(logoBytes, puts[3].body)
        assertEquals("image/png", puts[3].headers["content-type"])
    }

    @Test
    fun `upload failure before confirmation aborts cleanly without confirming doc or menu image`() = runBlocking {
        val baseUrl = startHttpServer {
            MockHttpResponse(500, """{"error":"storage_down"}""".toByteArray(StandardCharsets.UTF_8))
        }

        val confirmCalls = AtomicInteger(0)
        val attachCalls = AtomicInteger(0)
        val settingsCalls = AtomicInteger(0)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse = DocumentUploadUrlResponse(
                ok = true,
                uploadUrl = "$baseUrl/object/upload/sign/restaurant-private-docs/applications/app-1/owner_id/id.png?token=t1",
                storagePath = "applications/app-1/owner_id/id.png",
                docType = body.docType,
            )

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                return ConfirmDocumentResponse(ok = true)
            }

            override suspend fun requestMenuPhotoUploadUrl(
                bearerAuth: String,
                action: String,
                body: PhotoUploadUrlRequest,
            ): MediaUploadUrlResponse = MediaUploadUrlResponse(
                ok = true,
                uploadUrl = "$baseUrl/object/upload/sign/restaurant-public-media/restaurants/r1/item.jpg?token=t2",
                storagePath = "restaurants/r1/item.jpg",
            )

            override suspend fun attachMenuItemImage(
                bearerAuth: String,
                action: String,
                body: AttachMenuItemImageRequest,
            ): MenuItemImageMutationResponse {
                attachCalls.incrementAndGet()
                return MenuItemImageMutationResponse(ok = true)
            }

            override suspend fun updateRestaurantSettings(
                bearerAuth: String,
                action: String,
                body: UpdateRestaurantSettingsRequest,
            ): RestaurantSettingsResponse {
                settingsCalls.incrementAndGet()
                return RestaurantSettingsResponse(ok = true)
            }
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)

        val docFailure = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "owner_id",
            source = RestaurantsBinaryUploadSource.fromBytes("id.png", "image/png", byteArrayOf(1, 2, 3)),
        )
        assertTrue(docFailure.isFailure)
        assertTrue(docFailure.exceptionOrNull() is RestaurantsFailure.Server)

        val photoFailure = repo.uploadAndAttachMenuItemImage(
            restaurantId = "r1",
            menuItemId = "m1",
            source = RestaurantsBinaryUploadSource.fromBytes("item.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
        )
        assertTrue(photoFailure.isFailure)

        val mediaFailure = repo.uploadAndApplyRestaurantMedia(
            restaurantId = "r1",
            slot = RestaurantMediaSlot.COVER,
            source = RestaurantsBinaryUploadSource.fromBytes("cover.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
        )
        assertTrue(mediaFailure.isFailure)

        // Also verify unreachable TCP socket maps cleanly to Network without confirming.
        server?.stop()
        server = null
        val closedSocketRes = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "owner_id",
            source = RestaurantsBinaryUploadSource.fromBytes("id.png", "image/png", byteArrayOf(1, 2, 3)),
        )
        assertTrue(closedSocketRes.exceptionOrNull() is RestaurantsFailure.Network)

        assertEquals(0, confirmCalls.get())
        assertEquals(0, attachCalls.get())
        assertEquals(0, settingsCalls.get())
    }

    @Test
    fun `invalid content type is rejected before touching backend upload-url`() = runBlocking {
        val mintCalls = AtomicInteger(0)
        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse {
                mintCalls.incrementAndGet()
                return DocumentUploadUrlResponse(ok = true)
            }

            override suspend fun requestMenuPhotoUploadUrl(
                bearerAuth: String,
                action: String,
                body: PhotoUploadUrlRequest,
            ): MediaUploadUrlResponse {
                mintCalls.incrementAndGet()
                return MediaUploadUrlResponse(ok = true)
            }

            override suspend fun requestApplicationMediaUploadUrl(
                bearerAuth: String,
                action: String,
                body: ApplicationMediaUploadUrlRequest,
            ): MediaUploadUrlResponse {
                mintCalls.incrementAndGet()
                return MediaUploadUrlResponse(ok = true)
            }
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)

        val badDocMime = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource.fromBytes("script.svg", "image/svg+xml", byteArrayOf(1, 2)),
        )
        assertTrue(badDocMime.exceptionOrNull() is RestaurantsFailure.Validation)

        val badDocType = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "passport_fake",
            source = RestaurantsBinaryUploadSource.fromBytes("id.jpg", "image/jpeg", byteArrayOf(1, 2)),
        )
        assertTrue(badDocType.exceptionOrNull() is RestaurantsFailure.Validation)

        val pdfAsMenuPhoto = repo.uploadAndAttachMenuItemImage(
            restaurantId = "r1",
            menuItemId = "m1",
            source = RestaurantsBinaryUploadSource.fromBytes("menu.pdf", "application/pdf", byteArrayOf(1, 2)),
        )
        assertTrue(pdfAsMenuPhoto.exceptionOrNull() is RestaurantsFailure.Validation)

        val textAsAppMedia = repo.uploadApplicationMedia(
            applicationId = "app-1",
            source = RestaurantsBinaryUploadSource.fromBytes("notes.txt", "text/plain", byteArrayOf(1, 2)),
        )
        assertTrue(textAsAppMedia.exceptionOrNull() is RestaurantsFailure.Validation)

        assertEquals(0, mintCalls.get())
    }

    @Test
    fun `oversized file is rejected preflight and mid-stream overflow aborts before confirmation`() = runBlocking {
        val baseUrl = startHttpServer { MockHttpResponse(200) }
        val mintCalls = AtomicInteger(0)
        val confirmCalls = AtomicInteger(0)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse {
                mintCalls.incrementAndGet()
                return DocumentUploadUrlResponse(
                    ok = true,
                    uploadUrl = "$baseUrl/object/upload/sign/restaurant-private-docs/applications/app-1/other/overflow.pdf?token=overflow",
                    storagePath = "applications/app-1/other/overflow.pdf",
                    docType = body.docType,
                )
            }

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                return ConfirmDocumentResponse(ok = true)
            }
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)

        val oversizedDoc = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource(
                fileName = "huge.pdf",
                mimeType = "application/pdf",
                byteSize = (10L * 1024L * 1024L) + 1L,
                openStream = { ByteArrayInputStream(ByteArray(0)) },
            ),
        )
        assertTrue(oversizedDoc.exceptionOrNull() is RestaurantsFailure.Validation)
        assertEquals(0, mintCalls.get())

        val oversizedPhoto = repo.uploadAndAttachMenuItemImage(
            restaurantId = "r1",
            menuItemId = "m1",
            source = RestaurantsBinaryUploadSource(
                fileName = "huge.jpg",
                mimeType = "image/jpeg",
                byteSize = (5L * 1024L * 1024L) + 1L,
                openStream = { ByteArrayInputStream(ByteArray(0)) },
            ),
        )
        assertTrue(oversizedPhoto.exceptionOrNull() is RestaurantsFailure.Validation)
        assertEquals(0, mintCalls.get())

        val spoofedStreamRes = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource(
                fileName = "spoof.pdf",
                mimeType = "application/pdf",
                byteSize = 32L,
                openStream = { ByteArrayInputStream(ByteArray(512)) },
            ),
        )
        assertTrue(spoofedStreamRes.isFailure)
        assertTrue(spoofedStreamRes.exceptionOrNull() is RestaurantsFailure.Validation)
        assertEquals(1, mintCalls.get())
        assertEquals(0, confirmCalls.get())
    }

    @Test
    fun `expired or rejected signed url fails without session refresh and without leaking token`() = runBlocking {
        val secretSignedToken = "super-secret-signed-storage-token-xyz"
        val baseUrl = startHttpServer {
            MockHttpResponse(403, """{"error":"Signature expired"}""".toByteArray(StandardCharsets.UTF_8))
        }
        val goTrueRefreshCalls = AtomicInteger(0)
        val confirmCalls = AtomicInteger(0)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse = DocumentUploadUrlResponse(
                ok = true,
                uploadUrl = "$baseUrl/object/upload/sign/restaurant-private-docs/applications/app-1/other/expired.pdf?token=$secretSignedToken",
                storagePath = "applications/app-1/other/expired.pdf",
                docType = body.docType,
            )

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                return ConfirmDocumentResponse(ok = true)
            }
        }

        val sessionManager = RestaurantsSessionManager(
            environmentProvider = { enabledEnv() },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String? = activeUid
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token"
            },
            goTrueApiProvider = {
                object : RestaurantsGoTrueApi {
                    override suspend fun refreshSession(body: GoTrueRefreshRequest): GoTrueRefreshResponse {
                        goTrueRefreshCalls.incrementAndGet()
                        error("must not refresh GoTrue session on signed URL 403")
                    }
                }
            },
            clock = EpochClock { 1000L },
        )

        val repo = RestaurantsRepository(
            environmentProvider = { enabledEnv() },
            sessionManager = sessionManager,
            edgeApiProvider = { edgeApi },
            signedUploaderProvider = {
                OkHttpRestaurantsSignedUploader(allowInsecureLoopbackHttp = true)
            },
        )

        val result = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource.fromBytes("expired.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
        )
        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull() as RestaurantsFailure
        assertTrue(failure is RestaurantsFailure.Forbidden)
        assertFalse(failure.userMessage.contains(secretSignedToken))
        assertFalse(failure.userMessage.contains("127.0.0.1"))
        assertEquals(0, goTrueRefreshCalls.get())
        assertEquals(0, confirmCalls.get())

        val insecureRemote = runCatching {
            OkHttpRestaurantsSignedUploader(allowInsecureLoopbackHttp = false).uploadToSignedUrl(
                uploadUrl = "http://untrusted.example.com/object/upload/sign/a/b?token=secret",
                kind = RestaurantsUploadKind.PUBLIC_MEDIA,
                source = RestaurantsBinaryUploadSource.fromBytes("a.jpg", "image/jpeg", byteArrayOf(1, 2)),
            )
        }.exceptionOrNull()
        assertTrue(insecureRemote is RestaurantsFailure.Server)
        assertFalse((insecureRemote?.message ?: "").contains("secret"))
    }

    @Test
    fun `missing or invalid upload-url response aborts without HTTP upload or confirmation`() = runBlocking {
        val putCalls = AtomicInteger(0)
        val confirmCalls = AtomicInteger(0)
        var nextResponse = DocumentUploadUrlResponse(ok = false, uploadUrl = null, storagePath = null)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse = nextResponse

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                return ConfirmDocumentResponse(ok = true)
            }
        }

        val repo = RestaurantsRepository(
            environmentProvider = { enabledEnv() },
            sessionManager = createSessionManager(),
            edgeApiProvider = { edgeApi },
            signedUploaderProvider = {
                RestaurantsSignedUploader { _, _, _ -> putCalls.incrementAndGet() }
            },
        )
        val source = RestaurantsBinaryUploadSource.fromBytes("doc.pdf", "application/pdf", byteArrayOf(1, 2, 3))

        val invalidResponses = listOf(
            DocumentUploadUrlResponse(ok = false, uploadUrl = "https://storage.test/up", storagePath = "applications/app-1/other/doc.pdf"),
            DocumentUploadUrlResponse(ok = true, uploadUrl = "", storagePath = "applications/app-1/other/doc.pdf"),
            DocumentUploadUrlResponse(ok = true, uploadUrl = "https://storage.test/up", storagePath = null),
            DocumentUploadUrlResponse(ok = true, uploadUrl = "https://storage.test/up", storagePath = "../evil/doc.pdf"),
            DocumentUploadUrlResponse(ok = true, uploadUrl = "not-a-valid-url", storagePath = "applications/app-1/other/doc.pdf"),
        )

        invalidResponses.forEach { bad ->
            nextResponse = bad
            val result = repo.uploadAndConfirmDocument("app-1", "other", source)
            assertTrue(result.isFailure)
        }
        assertEquals(0, putCalls.get())
        assertEquals(0, confirmCalls.get())
    }

    @Test
    fun `no credentials leaked to external signed storage url`() = runBlocking {
        var recordedPut: RecordedHttpPut? = null
        val baseUrl = startHttpServer { req ->
            recordedPut = req
            MockHttpResponse(200)
        }

        // Verify signed upload OkHttpClient is completely decoupled from SupabaseApiKeyInterceptor.
        val edgeClientReq = copyRequestViaSupabaseClient(enabledEnv(), baseUrl)
        assertNotNull(edgeClientReq.headers["apikey"])

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse = DocumentUploadUrlResponse(
                ok = true,
                uploadUrl = "$baseUrl/object/upload/sign/restaurant-private-docs/applications/app-1/other/doc.pdf?token=ext-token",
                storagePath = "applications/app-1/other/doc.pdf",
                docType = body.docType,
            )

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse = ConfirmDocumentResponse(
                ok = true,
                document = ApplicationDocumentDto(
                    id = "doc-1",
                    applicationId = body.applicationId,
                    docType = body.docType,
                    storagePath = body.storagePath,
                    fileName = body.fileName,
                    mimeType = body.mimeType,
                    byteSize = body.byteSize,
                ),
            )
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)
        val res = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource.fromBytes("doc.pdf", "application/pdf", byteArrayOf(7, 8, 9)),
        )
        assertTrue(res.isSuccess)

        val put = recordedPut
        assertNotNull(put)
        assertNull(put?.headers?.get("authorization"))
        assertNull(put?.headers?.get("apikey"))
        assertNull(put?.headers?.get("cookie"))
        assertEquals("false", put?.headers?.get("x-upsert"))
    }

    @Test
    fun `account switching during upload aborts before confirmation and clears coordinator state`() = runBlocking {
        val confirmCalls = AtomicInteger(0)
        val attachCalls = AtomicInteger(0)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun requestDocumentUploadUrl(
                bearerAuth: String,
                action: String,
                body: DocumentUploadUrlRequest,
            ): DocumentUploadUrlResponse = DocumentUploadUrlResponse(
                ok = true,
                uploadUrl = "https://storage.example/object/upload/sign/restaurant-private-docs/applications/app-1/other/doc.pdf?token=t1",
                storagePath = "applications/app-1/other/doc.pdf",
                docType = body.docType,
            )

            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                return ConfirmDocumentResponse(ok = true)
            }

            override suspend fun requestMenuPhotoUploadUrl(
                bearerAuth: String,
                action: String,
                body: PhotoUploadUrlRequest,
            ): MediaUploadUrlResponse = MediaUploadUrlResponse(
                ok = true,
                uploadUrl = "https://storage.example/object/upload/sign/restaurant-public-media/restaurants/r1/item.jpg?token=t2",
                storagePath = "restaurants/r1/item.jpg",
            )

            override suspend fun attachMenuItemImage(
                bearerAuth: String,
                action: String,
                body: AttachMenuItemImageRequest,
            ): MenuItemImageMutationResponse {
                attachCalls.incrementAndGet()
                return MenuItemImageMutationResponse(ok = true)
            }
        }

        val switchingUploader = RestaurantsSignedUploader { _, _, _ ->
            activeUid = "uid-partner-B"
            RestaurantsSessionMemory.reconcileAdisChatUid("uid-partner-B")
        }

        val repo = RestaurantsRepository(
            environmentProvider = { enabledEnv() },
            sessionManager = createSessionManager(),
            edgeApiProvider = { edgeApi },
            signedUploaderProvider = { switchingUploader },
        )

        val switchResult = repo.uploadAndConfirmDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource.fromBytes("doc.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
        )
        assertTrue(switchResult.isFailure)
        val failure = switchResult.exceptionOrNull()
        assertTrue(failure is RestaurantsFailure.Unauthenticated)
        assertEquals("account_switched", (failure as RestaurantsFailure.Unauthenticated).code)
        assertEquals(0, confirmCalls.get())

        activeUid = "uid-partner-1"
        seedSession("uid-partner-1")
        lateinit var coordinator: RestaurantCenterCoordinator
        val delayedUploader = RestaurantsSignedUploader { _, _, _ ->
            delay(30)
            activeUid = "uid-partner-B"
            RestaurantsSessionMemory.reconcileAdisChatUid("uid-partner-B")
            coordinator.onAccountChanged("uid-partner-B")
        }
        val coordRepo = RestaurantsRepository(
            environmentProvider = { enabledEnv() },
            sessionManager = createSessionManager(),
            edgeApiProvider = { edgeApi },
            signedUploaderProvider = { delayedUploader },
        )
        coordinator = RestaurantCenterCoordinator(
            scope = this,
            dataSource = RemoteRestaurantCenterDataSource(coordRepo),
            environmentProvider = { enabledEnv() },
            accountUidProvider = { activeUid },
        )
        coordinator.uploadApplicationDocument(
            applicationId = "app-1",
            docType = "other",
            source = RestaurantsBinaryUploadSource.fromBytes("doc.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
        )
        delay(80)

        assertEquals("uid-partner-B", coordinator.state.value.activeAccountUid)
        assertNull(coordinator.state.value.errorBanner)
        assertEquals(0, confirmCalls.get())
        assertEquals(0, attachCalls.get())
    }

    @Test
    fun `duplicate confirmation for same storage path is rejected sequentially and concurrently`() = runBlocking {
        val confirmCalls = AtomicInteger(0)
        val attachCalls = AtomicInteger(0)

        val edgeApi = object : StubRestaurantsEdgeApi() {
            override suspend fun confirmDocument(
                bearerAuth: String,
                action: String,
                body: ConfirmDocumentRequest,
            ): ConfirmDocumentResponse {
                confirmCalls.incrementAndGet()
                delay(40)
                return ConfirmDocumentResponse(
                    ok = true,
                    document = ApplicationDocumentDto(
                        id = "doc-1",
                        applicationId = body.applicationId,
                        docType = body.docType,
                        storagePath = body.storagePath,
                        fileName = body.fileName,
                        mimeType = body.mimeType,
                        byteSize = body.byteSize,
                    ),
                )
            }

            override suspend fun attachMenuItemImage(
                bearerAuth: String,
                action: String,
                body: AttachMenuItemImageRequest,
            ): MenuItemImageMutationResponse {
                attachCalls.incrementAndGet()
                return MenuItemImageMutationResponse(
                    ok = true,
                    image = MenuItemImageDto(
                        id = "img-1",
                        restaurantId = body.restaurantId,
                        menuItemId = body.menuItemId,
                        storagePath = body.storagePath,
                        isPrimary = true,
                        sortOrder = 0,
                    ),
                )
            }
        }

        val repo = createRepository(edgeApi = edgeApi, allowLoopbackHttp = true)

        val docReq = ConfirmDocumentRequest(
            applicationId = "app-1",
            docType = "other",
            storagePath = "applications/app-1/other/same-doc.pdf",
            fileName = "same-doc.pdf",
            mimeType = "application/pdf",
            byteSize = 128L,
        )
        val parallelDocs = (1..3).map { async { repo.confirmDocument(docReq) } }.awaitAll()
        assertEquals(1, confirmCalls.get())
        assertEquals(1, parallelDocs.count { it.isSuccess })
        assertEquals(2, parallelDocs.count { it.exceptionOrNull() is RestaurantsFailure.Conflict })

        val sequentialDup = repo.confirmDocument(docReq)
        assertEquals(1, confirmCalls.get())
        assertTrue(sequentialDup.exceptionOrNull() is RestaurantsFailure.Conflict)

        val imgReq = AttachMenuItemImageRequest(
            restaurantId = "r1",
            menuItemId = "m1",
            storagePath = "restaurants/r1/same-photo.jpg",
            isPrimary = true,
            sortOrder = 0,
        )
        assertTrue(repo.attachMenuItemImage(imgReq).isSuccess)
        assertTrue(repo.attachMenuItemImage(imgReq).exceptionOrNull() is RestaurantsFailure.Conflict)
        assertEquals(1, attachCalls.get())
    }

    private fun copyRequestViaSupabaseClient(env: RestaurantsEnvironment, baseUrl: String): RecordedHttpPut {
        val supabaseClient = RestaurantsApiClientFactory.buildSupabaseOkHttpClient(env)
        var captured: RecordedHttpPut? = null
        val local = LocalHttpMockServer { req ->
            captured = req
            MockHttpResponse(200)
        }
        val localBase = local.start()
        try {
            val req = Request.Builder().url("$localBase/test").get().build()
            supabaseClient.newCall(req).execute().use { }
        } finally {
            local.stop()
        }
        return captured ?: error("No request captured from $baseUrl")
    }

    private fun enabledEnv(): RestaurantsEnvironment = RestaurantsEnvironment(
        supabaseUrl = "https://restaurants-test.supabase.co",
        supabasePublishableKey = "sb_publishable_secret_key",
        identityBridgeUrl = "https://bridge-test.example",
        isIdentityBridgeEnabled = true,
        isRestaurantCenterEnabled = true,
    )

    private fun seedSession(uid: String) {
        RestaurantsSessionMemory.setSessionIfCurrent(
            RestaurantsSession(
                ownerAdisChatUid = uid,
                profileId = "prof-$uid",
                accessToken = "supa-secret-access-token-$uid",
                refreshToken = "refresh-$uid",
                expiresAtEpochSeconds = 5000L,
            ),
            RestaurantsSessionMemory.generation(),
        )
    }

    private fun createSessionManager(): RestaurantsSessionManager =
        RestaurantsSessionManager(
            environmentProvider = { enabledEnv() },
            firebaseIdentityProvider = object : FirebaseIdentityProvider {
                override fun currentUid(): String? = activeUid
                override suspend fun getIdToken(forceRefresh: Boolean): String = "fb-token"
            },
            clock = EpochClock { 1000L },
        )

    private fun createRepository(
        edgeApi: RestaurantsEdgeApi,
        allowLoopbackHttp: Boolean,
    ): RestaurantsRepository = RestaurantsRepository(
        environmentProvider = { enabledEnv() },
        sessionManager = createSessionManager(),
        edgeApiProvider = { edgeApi },
        signedUploaderProvider = {
            OkHttpRestaurantsSignedUploader(allowInsecureLoopbackHttp = allowLoopbackHttp)
        },
    )

    private fun startHttpServer(
        handler: (RecordedHttpPut) -> MockHttpResponse,
    ): String {
        val created = LocalHttpMockServer(handler)
        server = created
        return created.start()
    }

    private class LocalHttpMockServer(
        private val handler: (RecordedHttpPut) -> MockHttpResponse,
    ) {
        private var socket: ServerSocket? = null
        private val running = AtomicBoolean(false)

        fun start(): String {
            val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            socket = s
            running.set(true)
            thread(isDaemon = true, name = "restaurants-upload-mock") {
                while (running.get()) {
                    val client = try {
                        s.accept()
                    } catch (_: SocketException) {
                        break
                    } catch (_: Exception) {
                        break
                    }
                    thread(isDaemon = true) {
                        client.use { conn ->
                            handleClient(conn.getInputStream(), conn.getOutputStream())
                        }
                    }
                }
            }
            return "http://127.0.0.1:${s.localPort}"
        }

        fun stop() {
            running.set(false)
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null
        }

        private fun handleClient(rawIn: InputStream, rawOut: java.io.OutputStream) {
            val input = BufferedInputStream(rawIn)
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore("?")

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase()
                    val v = line.substring(colon + 1).trim()
                    headers[k] = v
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyOut = ByteArrayOutputStream(contentLength.coerceAtLeast(0))
            val buf = ByteArray(8192)
            var remaining = contentLength
            while (remaining > 0) {
                val read = input.read(buf, 0, minOf(buf.size, remaining))
                if (read < 0) break
                bodyOut.write(buf, 0, read)
                remaining -= read
            }

            val resp = handler(
                RecordedHttpPut(
                    method = method,
                    path = path,
                    targetWithQuery = target,
                    headers = headers,
                    body = bodyOut.toByteArray(),
                ),
            )
            val reason = when (resp.statusCode) {
                200 -> "OK"
                403 -> "Forbidden"
                500 -> "Internal Server Error"
                else -> "Status"
            }
            val head = "HTTP/1.1 ${resp.statusCode} $reason\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${resp.body.size}\r\n" +
                "Connection: close\r\n\r\n"
            rawOut.write(head.toByteArray(StandardCharsets.US_ASCII))
            if (resp.body.isNotEmpty()) {
                rawOut.write(resp.body)
            }
            rawOut.flush()
        }

        private fun readAsciiLine(input: InputStream): String? {
            val baos = ByteArrayOutputStream()
            var prev = -1
            while (true) {
                val b = input.read()
                if (b == -1) {
                    return if (baos.size() == 0) null else baos.toString("US-ASCII")
                }
                if (b == '\n'.code) {
                    val raw = baos.toByteArray()
                    val len = if (prev == '\r'.code && raw.isNotEmpty()) raw.size - 1 else raw.size
                    return String(raw, 0, len, StandardCharsets.US_ASCII)
                }
                baos.write(b)
                prev = b
            }
        }
    }
}
