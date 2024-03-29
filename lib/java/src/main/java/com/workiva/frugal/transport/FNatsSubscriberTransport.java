package com.workiva.frugal.transport;

import com.workiva.frugal.exception.TTransportExceptionType;
import com.workiva.frugal.protocol.FAsyncCallback;
import io.nats.client.Connection;
import io.nats.client.Constants;
import io.nats.client.Subscription;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

import static com.workiva.frugal.transport.FNatsTransport.FRUGAL_PREFIX;

/**
 * FNatsSubscriberTransport implements FSubscriberTransport by using NATS as the pub/sub message broker.
 * Messages are limited to 1MB in size.
 */
public class FNatsSubscriberTransport implements FSubscriberTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(FNatsSubscriberTransport.class);

    private final Connection conn;
    protected String subject;
    protected final String queue;
    protected Subscription sub;

    /**
     * Creates a new FNatsScopeTransport which is used for subscribing. Subscribers using this transport will subscribe
     * to the provided queue, forming a queue group. When a queue group is formed, only one member receives the message.
     * If the queue is null, then the subscriber does not join a queue group.
     *
     * @param conn  NATS connection
     * @param queue subscription queue
     */
    protected FNatsSubscriberTransport(Connection conn, String queue) {
        this.conn = conn;
        this.queue = queue;
    }

    /**
     * An FSubscriberTransportFactory implementation which creates FSubscriberTransports backed by NATS.
     */
    public static class Factory implements FSubscriberTransportFactory {

        private final Connection conn;
        private final String queue;

        /**
         * Creates a NATS FSubscriberTransportFactory using the provided NATS connection. Subscribers using this
         * transport will not use a queue.
         *
         * @param conn NATS connection
         */
        public Factory(Connection conn) {
            this(conn, null);
        }

        /**
         * Creates a NATS FSubscriberTransportFactory using the provided NATS connection. Subscribers using this
         * transport will subscribe to the provided queue, forming a queue group. When a queue group is formed,
         * only one member receives the message.
         *
         * @param conn  NATS connection
         * @param queue subscription queue
         */
        public Factory(Connection conn, String queue) {
            this.conn = conn;
            this.queue = queue;
        }

        /**
         * Get a new FSubscriberTransport instance.
         *
         * @return A new FSubscriberTransport instance.
         */
        public FNatsSubscriberTransport getTransport() {
            return new FNatsSubscriberTransport(this.conn, this.queue);
        }
    }

    @Override
    public boolean isSubscribed() {
        return conn.getState() == Constants.ConnState.CONNECTED && sub != null;
    }

    @Override
    public void subscribe(String topic, FAsyncCallback callback) throws TException {
        if (conn.getState() != Constants.ConnState.CONNECTED) {
            throw new TTransportException(TTransportExceptionType.NOT_OPEN,
                    "NATS not connected, has status " + conn.getState());
        }

        subject = topic;
        if ("".equals(subject)) {
            throw new TTransportException("Subject cannot be empty.");
        }

        sub = conn.subscribe(getFormattedSubject(), queue, msg -> {
            if (msg.getData().length < 4) {
                LOGGER.warn("discarding invalid scope message frame");
                return;
            }
            try {
                callback.onMessage(
                        new TMemoryInputTransport(Arrays.copyOfRange(msg.getData(), 4, msg.getData().length))
                );
            } catch (TException ignored) {
            }
        });
    }

    @Override
    public synchronized void unsubscribe() {
        try {
            sub.unsubscribe();
        } catch (IOException e) {
            LOGGER.warn("could not unsubscribe from subscription. " + e.getMessage());
        }
        sub = null;
    }

    private String getFormattedSubject() {
        return FRUGAL_PREFIX + this.subject;
    }

}

