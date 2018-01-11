package sample;

import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * Created by bqh on 2018/1/10.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class JoinPeer {
    public static final String CHANNEL_CONFIG_PATH = "channel/channel-artifacts/";

    public JoinPeer() throws CryptoException, InvalidArgumentException {
//        HFClient client = HFClient.createNewInstance();
//        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
//
//        File storeFile = new File("store/userStore.properties");
//        if (storeFile.exists()){
//            storeFile.delete();
//        }
//
//        SampleStore sampleStore = new SampleStore(storeFile);

    }

    public static void constructChannel(Config config, String name, HFClient client, SampleOrg sampleOrg) throws Exception{
        String orderName = sampleOrg.getOrdererNames().iterator().next();
        Properties ordererProperties = config.getOrdererProperties(orderName);

        //example of setting keepAlive to avoid timeouts on inactive http2 connections.
        // Under 5 minutes would require changes to server side to accept faster ping rates.
        ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
        ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});

        Orderer anOrderer = client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName), ordererProperties);

        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File( CHANNEL_CONFIG_PATH + "channel" + ".tx"));
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));
    }

    public static void join(Config config, String peerName, HFClient client, SampleOrg sampleOrg, String name) throws Exception{
        Channel channel = client.newChannel(name);
        String peerLocation = sampleOrg.getPeerLocation(peerName);

        Properties peerProperties = config.getPeerProperties(peerName); //test properties for peer.. if any.
        if (peerProperties == null) {
            peerProperties = new Properties();
        }
        //Example of setting specific options on grpc's NettyChannelBuilder
        peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

        Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
        channel.joinPeer(peer);
        out("Peer %s joined channel %s", peerName, channel.getName());
        sampleOrg.addPeer(peer);
    }

    public static void constructChannel(Config config, String name, HFClient client, SampleOrg sampleOrg) throws Exception {

        for (String peerName : sampleOrg.getPeerNames()) {
            String eventHubName = sampleOrg.getEventHubNamesByPeerNames(peerName);
            constructChannel(config, name, client, sampleOrg, peerName, eventHubName);
        }
    }

    public static void constructChannel(Config config, String name, HFClient client, SampleOrg sampleOrg) throws Exception {

        out("Constructing channel %s", name);

        //Only peer Admin org
        client.setUserContext(sampleOrg.getPeerAdmin());

        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : sampleOrg.getOrdererNames()) {

            Properties ordererProperties = config.getOrdererProperties(orderName);

            //example of setting keepAlive to avoid timeouts on inactive http2 connections.
            // Under 5 minutes would require changes to server side to accept faster ping rates.
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});

            orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                    ordererProperties));
        }

        //Just pick the first orderer in the list to create the channel.

        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);

        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File( CHANNEL_CONFIG_PATH + "channel" + ".tx"));

        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));

        out("Created channel %s", name);


        for (String peerName : sampleOrg.getPeerNames()) {

        }


        for (Orderer orderer : orderers) { //add remaining orderers if any.
            newChannel.addOrderer(orderer);
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {

            final Properties eventHubProperties = config.getEventHubProperties(eventHubName);

            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});

            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    eventHubProperties);
            newChannel.addEventHub(eventHub);
        }

        newChannel.initialize();

        out("Finished initialization channel %s", name);
    }

    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }
}
