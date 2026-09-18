package krill.zone.service

import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/**
 * Like [runCatching], but an exception that already carries an explicit gRPC
 * [io.grpc.Status] (e.g. the stub-provider guard in [krill.zone.Pi4jContextManager])
 * propagates as itself instead of being folded into a `success = false` response.
 */
inline fun <T> runCatchingGrpc(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: StatusException) {
    throw e
} catch (e: StatusRuntimeException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
