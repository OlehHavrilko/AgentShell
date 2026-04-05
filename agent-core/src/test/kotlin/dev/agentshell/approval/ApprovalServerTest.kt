package dev.agentshell.approval

import dev.agentshell.domain.ApprovalRequest
import dev.agentshell.runtime.ApprovalGate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalServerTest {

    private lateinit var gate: ApprovalGate
    private lateinit var server: ApprovalServer
    private val client = HttpClient.newHttpClient()
    private val port = 19876

    @BeforeEach
    fun setup() {
        gate = ApprovalGate(ttlMs = 60_000)
        server = ApprovalServer(gate, port)
        server.start()
        Thread.sleep(100) // let the server bind
    }

    @AfterEach
    fun teardown() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() to resp.body()
    }

    private fun post(path: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .POST(HttpRequest.BodyPublishers.noBody()).build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() to resp.body()
    }

    @Test
    fun `GET health returns UP`() {
        val (status, body) = get("/health")
        assertEquals(200, status)
        assertTrue(body.contains("UP"))
    }

    @Test
    fun `GET approvals returns empty list`() {
        val (status, body) = get("/approvals")
        assertEquals(200, status)
        assertTrue(body.contains("[]") || body.contains("[ ]"))
    }

    @Test
    fun `GET approvals lists pending requests`() {
        val req = ApprovalRequest(
            approvalId = "test-id",
            runId = "run-1",
            stepId = "s1",
            riskScore = 75,
            impactPreview = "run rm -rf /",
            status = "PENDING",
        )
        gate.request(req)

        val (status, body) = get("/approvals")
        assertEquals(200, status)
        assertTrue(body.contains("test-id"), "Expected approvalId in response: $body")
        assertTrue(body.contains("run-1"))
    }

    @Test
    fun `POST approve changes status`() {
        val req = ApprovalRequest(
            approvalId = "approve-me",
            runId = "run-2",
            stepId = "s2",
            riskScore = 80,
            impactPreview = "something risky",
            status = "PENDING",
        )
        gate.request(req)

        val (status, body) = post("/approvals/approve-me/approve")
        assertEquals(200, status)
        assertTrue(body.contains("APPROVED") || body.contains("approve-me"), "Unexpected body: $body")
    }

    @Test
    fun `POST reject changes status`() {
        val req = ApprovalRequest(
            approvalId = "reject-me",
            runId = "run-3",
            stepId = "s3",
            riskScore = 90,
            impactPreview = "very risky",
            status = "PENDING",
        )
        gate.request(req)

        val (status, body) = post("/approvals/reject-me/reject")
        assertEquals(200, status)
        assertTrue(body.contains("REJECTED") || body.contains("reject-me"), "Unexpected body: $body")
    }
}
