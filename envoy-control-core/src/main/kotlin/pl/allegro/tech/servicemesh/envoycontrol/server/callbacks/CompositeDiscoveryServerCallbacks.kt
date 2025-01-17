package pl.allegro.tech.servicemesh.envoycontrol.server.callbacks

import io.envoyproxy.controlplane.server.DiscoveryServerCallbacks
import io.envoyproxy.controlplane.server.exception.RequestException
import io.micrometer.core.instrument.MeterRegistry
import pl.allegro.tech.servicemesh.envoycontrol.logger
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest as v3DiscoveryRequest
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse as v3DiscoveryResponse

class CompositeException(exceptions: List<java.lang.Exception>) :
    RuntimeException("Composite exception: " + exceptions.map { it.message }.joinToString(",", "[", "]"))

class CompositeDiscoveryServerCallbacks(
    val meterRegistry: MeterRegistry,
    vararg val delegate: DiscoveryServerCallbacks
) : DiscoveryServerCallbacks {
    private val logger by logger()

    override fun onStreamCloseWithError(streamId: Long, typeUrl: String?, error: Throwable?) {
        runCallbacks {
            it.onStreamCloseWithError(streamId, typeUrl, error)
        }
    }

    override fun onStreamClose(streamId: Long, typeUrl: String?) {
        runCallbacks {
            it.onStreamClose(streamId, typeUrl)
        }
    }

    override fun onStreamOpen(streamId: Long, typeUrl: String?) {
        runCallbacks {
            it.onStreamOpen(streamId, typeUrl)
        }
    }

    override fun onV3StreamRequest(streamId: Long, request: v3DiscoveryRequest?) {
        runCallbacks {
            it.onV3StreamRequest(streamId, request)
        }
    }

    override fun onV3StreamResponse(
        streamId: Long,
        request: v3DiscoveryRequest?,
        response: v3DiscoveryResponse?
    ) {
        runCallbacks {
            it.onV3StreamResponse(streamId, request, response)
        }
    }

    private fun runCallbacks(fn: (DiscoveryServerCallbacks) -> Unit) {
        val exceptions = mutableListOf<Exception>()
        for (callback in delegate) {
            try {
                fn(callback)
            } catch (e: Exception) {
                meterRegistry.counter("callbacks.errors").increment()
                logger.warn(e.message, e)
                when (e) {
                    // stop callback processing and throw RequestException without wrapping,
                    // to notify client with proper message
                    is RequestException -> throw e
                    else -> exceptions.add(e)
                }
            }
        }
        if (exceptions.isNotEmpty()) {
            throw CompositeException(exceptions)
        }
    }
}
