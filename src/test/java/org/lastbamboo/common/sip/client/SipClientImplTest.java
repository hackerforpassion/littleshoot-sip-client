package org.lastbamboo.common.sip.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;

import junit.framework.TestCase;

import org.apache.commons.lang.StringUtils;
import org.lastbamboo.common.offer.answer.IceMediaStreamDesc;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.sip.stack.SipUriFactory;
import org.lastbamboo.common.sip.stack.message.SipMessageFactory;
import org.lastbamboo.common.sip.stack.message.SipMessageFactoryImpl;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionFactory;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTrackerImpl;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayer;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayerImpl;
import org.lastbamboo.common.sip.stack.util.UriUtils;
import org.lastbamboo.common.sip.stack.util.UriUtilsImpl;
import org.littleshoot.util.SessionSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests a SIP client continually registering and sending messages.
 */
public class SipClientImplTest extends TestCase 
    implements OfferAnswerTransactionListener
    {

    private static final Logger LOG = 
        LoggerFactory.getLogger(SipClientImplTest.class);

    private static final int NUM_INVITES = 100;
    
    private final int TEST_PORT = 8472;

    private int m_invitesReceivedOnServer;
    
    /**
     * Test to make sure the client is just sending out INVITEs that are
     * received intact on the server.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testSipClientInvites() throws Exception
        {
        startServerThread();
        final SipClient client = createSipClient();
        
        final URI invitee = SipUriFactory.createSipUri(42798L);
        final byte[] body = new byte[0];
        for (int i = 0; i < NUM_INVITES; i++)
            {
            client.offer(invitee, body, this, null);
            }
        
        if (m_invitesReceivedOnServer < NUM_INVITES)
            {
            synchronized(this)
                {
                wait(20*1000);
                }
            }
        
        if (m_invitesReceivedOnServer < NUM_INVITES)
            {
            fail("Only recieved "+m_invitesReceivedOnServer+
                " invites on server...");
            }
        }

    private SipClient createSipClient() throws Exception
        {
        final UriUtils uriUtils = new UriUtilsImpl();
        final SipHeaderFactory headerFactory = new SipHeaderFactoryImpl();
        final SipMessageFactory messageFactory = new SipMessageFactoryImpl();
        final SipTransactionTracker transactionTracker = 
            new SipTransactionTrackerImpl();
        final SipTransactionFactory transactionFactory = 
            new SipTransactionFactoryImpl(transactionTracker, messageFactory, 500);
        final SipTcpTransportLayer transportLayer = 
            new SipTcpTransportLayerImpl(transactionFactory, headerFactory, 
                messageFactory);
        final SipClientTracker sipClientTracker = new SipClientTrackerImpl();
     
        final long userId = 48392L;
        final URI clientUri = SipUriFactory.createSipUri (userId);

        final URI proxyUri = 
            new URI("sip:127.0.0.1:"+TEST_PORT+";transport=tcp");
         
        final OfferAnswerFactory offerAnswerFactory = new OfferAnswerFactory() {

            public OfferAnswer createAnswerer(OfferAnswerListener listener,
                    final boolean useRelay)
                    throws OfferAnswerConnectException {
                return null;
            }

            public OfferAnswer createOfferer(OfferAnswerListener listener,
                    final IceMediaStreamDesc streamDesc)
                    throws OfferAnswerConnectException {
                return null;
            }

            @Override
            public boolean isAnswererPortMapped() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public int getMappedPort() {
                // TODO Auto-generated method stub
                return 0;
            }
        };
       
        final CrlfDelayCalculator calculator = new DefaultCrlfDelayCalculator();
        final SessionSocketListener sl = new SessionSocketListener() {
            public void onSocket(String id, Socket sock) throws IOException {}

            @Override
            public void reconnected() {
                // TODO Auto-generated method stub
                
            }
        };
        final SipClient client = 
            new SipClientImpl(clientUri, proxyUri, 
                messageFactory, transactionTracker, offerAnswerFactory, 
                new InetSocketAddress(TEST_PORT), sl, uriUtils, transportLayer, 
                sipClientTracker, calculator, null);
       
        client.connect();
        client.register();
        return client;
        }

    private void startServerThread()
        {
        final Runnable runner = new Runnable()
            {
            public void run()
                {
                try
                    {
                    startServer();
                    }
                catch (final IOException e)
                    {
                    SipClientImplTest.fail("Could not start server");
                    }
                }
            };
        final Thread serverThread = new Thread(runner, "server-thread");
        serverThread.setDaemon(true);
        serverThread.start();
        }

    private void startServer() throws IOException
        {
        final ServerSocket server = new ServerSocket(TEST_PORT);
        final Socket sock = server.accept();
        
        LOG.debug("Got server socket!!!");
        
        final InputStream is = sock.getInputStream();
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        
        String curLine = br.readLine();
        if (curLine == null)
            {
            LOG.error("GOT NULL LINE!!");
            //is.close();
            //sock.getOutputStream().close();
            sock.close();
            return;
            }
        /*
        if (StringUtils.isEmpty(curLine))
            {
            while (StringUtils.isEmpty(curLine))
                {
                LOG.debug("Got blank line");
                curLine = br.readLine();
                }
            }
            */
        final boolean giveRegisterOk;
        if (curLine.startsWith("REGISTER"))
            {
            giveRegisterOk = true;
            }
        else if (!curLine.startsWith("INVITE"))
            {
            fail("No REGISTER or INVITE: "+curLine);
            giveRegisterOk = false;
            }
        else
            {
            giveRegisterOk = false;
            }
        
        String branch = "";
        while (!StringUtils.isBlank(curLine))
            {
            LOG.debug(curLine);
            curLine = br.readLine();
            if (curLine.startsWith("Via"))
                {
                branch = StringUtils.substringAfter(curLine, "branch=");
                }
            }
        if (giveRegisterOk)
            {
            final OutputStream os = sock.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write("SIP/2.0 200 OK\r\n");
            writer.write("To: Anonymous <sip:1199792423@lastbamboo.org>;tag=2365b467-5\r\n");
            writer.write("Via: SIP/2.0/TCP 10.250.77.172:5060;branch="+branch+"\r\n");
            writer.write("Supported: outbound\r\n");
            writer.write("CSeq: 2 REGISTER\r\n");
            writer.write("Call-ID: d0d08c9-\r\n");
            writer.write("From: Anonymous <sip:1199792423@lastbamboo.org>;tag=0244d706-5\r\n\r\n");
           
            writer.flush();
            os.flush();
            }
        
        curLine = br.readLine();
        while (true)
            {
            LOG.debug(curLine);
            if (curLine.startsWith("INVITE"))
                {
                m_invitesReceivedOnServer++;
                }
            curLine = br.readLine();
            
            if (m_invitesReceivedOnServer == NUM_INVITES)
                {
                synchronized(this)
                    {
                    this.notify();
                    }
                break;
                }
            }
        }

    public void onTransactionFailed(OfferAnswerMessage arg0)
        {
        LOG.debug("Transaction failed...");
        }

    public void onTransactionSucceeded(OfferAnswerMessage arg0)
        {
        LOG.debug("Transaction succeeded...");
        }
    }
