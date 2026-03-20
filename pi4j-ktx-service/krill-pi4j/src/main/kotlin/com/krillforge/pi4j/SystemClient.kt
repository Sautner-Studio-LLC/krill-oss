package com.krillforge.pi4j

import com.google.protobuf.Empty
import io.grpc.Channel
import com.krillforge.pi4j.proto.PingResponse
import com.krillforge.pi4j.proto.SystemInfoResponse
import com.krillforge.pi4j.proto.SystemServiceGrpcKt

class SystemClient internal constructor(channel: Channel) {

    private val stub = SystemServiceGrpcKt.SystemServiceCoroutineStub(channel)

    /** Liveness check. Returns the service version and a server-side timestamp. */
    suspend fun ping(): PingResponse = stub.ping(Empty.getDefaultInstance())

    /** Returns platform and provider inventory from the pi4j context on the daemon. */
    suspend fun getInfo(): SystemInfoResponse = stub.getInfo(Empty.getDefaultInstance())

    /** Request a graceful shutdown of the remote daemon. */
    suspend fun shutdown() { stub.shutdown(Empty.getDefaultInstance()) }
}
