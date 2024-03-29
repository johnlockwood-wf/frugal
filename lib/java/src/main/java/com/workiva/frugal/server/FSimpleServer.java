package com.workiva.frugal.server;

import com.workiva.frugal.processor.FProcessor;
import com.workiva.frugal.protocol.FProtocol;
import com.workiva.frugal.protocol.FProtocolFactory;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple single-threaded server for testing that may be used used to serve clients using TTransports
 * wrapped with the FAdapterTransport.
 */
public class FSimpleServer implements FServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FSimpleServer.class);

    private final FProcessor fProcessor;
    private final TServerTransport tServerTransport;
    private final FProtocolFactory fProtocolFactory;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    private FSimpleServer(FProcessor fProcessor, TServerTransport tServerTransport,
                          FProtocolFactory fProtocolFactory) {
        this.fProcessor = fProcessor;
        this.tServerTransport = tServerTransport;
        this.fProtocolFactory = fProtocolFactory;
    }

    /**
     * Create a new FSimpleServer.
     *
     * @param fProcessor Processor for incoming requests.
     * @param tServerTransport Server transport listening to requests.
     * @param fProtocolFactory Protocol factory
     *
     * @return FSimpleServer
     */
    public static FSimpleServer of(FProcessor fProcessor, TServerTransport tServerTransport,
                                   FProtocolFactory fProtocolFactory) {
        return new FSimpleServer(fProcessor, tServerTransport, fProtocolFactory);
    }

    /**
     * Starts the server by listening on the server transport and starting
     * an accept loop.
     *
     * @throws TException
     */
    @Override
    public void serve() throws TException {
        tServerTransport.listen();
        acceptLoop();
    }

    /**
     * Stops the server by interrupting the server transport.
     *
     * @throws TException
     */
    @Override
    public void stop() throws TException {
        isStopped.set(true);
        tServerTransport.interrupt();
    }

    /**
     * Loop while accepting incoming data on the configured transport.
     */
    void acceptLoop() throws TException {
        while (!isStopped.get()) {
            TTransport client;
            try {
                client = tServerTransport.accept();
            } catch (TException e) {
                if (isStopped.get()) {
                    return;
                }
                throw e;
            }
            if (client != null) {
                // TODO: Could make this multi-threaded by processing client messages in another thread.
                try {
                    accept(client);
                } catch (TException e) {
                    LOGGER.warn("frugal: error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Processes messages from the given client. Will block until the client disconnects.
     */
    void accept(TTransport client) throws TException {
        TTransport transport = new TFramedTransport(client);
        FProtocol inputProtocol = fProtocolFactory.getProtocol(transport);
        FProtocol outputProtocol = fProtocolFactory.getProtocol(transport);
        try {
            while (!isStopped.get()) {
                fProcessor.process(inputProtocol, outputProtocol);
            }
        } catch (TTransportException ttx) {
            // Client died, just move on
        }
    }
}
