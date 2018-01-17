package sample;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static sample.JoinPeer.out;

public class Controller {
    @FXML
    private ComboBox<String> peerComboBox;

    private final Config config = Config.getConfig();
    private final ConfigHelper configHelper = new ConfigHelper();
    private Collection<SampleOrg> sampleOrgs;
    private SampleStore sampleStore;
    private HFClient client;
    private Channel channel;

    private static final String ADMIN_NAME = "admin";
    private static final String USER_1_NAME = "user1";

    private static final String CHAIN_CODE_NAME = "example_cc_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "2";

    String testTxID = null;  // save the CC invoke TxID and use in queries

    public Controller() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException {

    }

    @FXML
    private void initialize(){
        peerComboBox.setItems(FXCollections.observableArrayList("peer0", "peer1", "peer2", "peer3"));
    }

//    @FXML
//    private void newChannel(){
//        JoinPeer.newChannel(config, "mychannel", client, sampleOrg2);
//    }

    @FXML
    private void joinPeer()throws Exception{
        connect();

        SampleOrg sampleOrg1 = config.getIntegrationTestsSampleOrg("peerOrg1");
        try {
            channel = JoinPeer.constructChannel(config, "foo", client, sampleOrg1);
            //initChannel(channel);
        } catch (Exception e) {
            warning(e.getMessage());
        }
    }

    @FXML
    private void connect()throws Exception{
        try {
            configHelper.clearConfig();
            configHelper.customizeConfig();
        } catch (NoSuchFieldException|IllegalAccessException e) {
            warning(e.getMessage());
        }

        sampleOrgs = config.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : sampleOrgs) {
            try {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            } catch (MalformedURLException e) {
                warning(e.getMessage());
            }
        }

        //Create instance of client.
        client = HFClient.createNewInstance();
        //设置加密套件
        try {
            client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        } catch (CryptoException|InvalidArgumentException e) {
            warning(e.getMessage());
        }

        //不需要ca
//        for (SampleOrg sampleOrg : sampleOrgs) {
//            sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
//        }

        //初始化用户存储文件
        File storeFile = new File("userStore.properties");
        if (!storeFile.exists()){
            try {
                storeFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        sampleStore = new SampleStore(storeFile);

        try {
            //初始化用户证书等

            for (SampleOrg sampleOrg : sampleOrgs) {

                HFCAClient ca = sampleOrg.getCAClient();
                final String orgName = sampleOrg.getName();
                final String mspid = sampleOrg.getMSPID();
                ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
                SampleUser admin = sampleStore.getMember(ADMIN_NAME, orgName);
                if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                    admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                    admin.setMspId(mspid);
                }

                sampleOrg.setAdmin(admin); // The admin of this org --

                SampleUser user = sampleStore.getMember(USER_1_NAME, sampleOrg.getName());
                if (!user.isRegistered()) {  // users need to be registered AND enrolled
                    RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                    user.setEnrollmentSecret(ca.register(rr, admin));
                }
                if (!user.isEnrolled()) {
                    user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                    user.setMspId(mspid);
                }
                sampleOrg.addUser(user); //Remember user belongs to this Org

                final String sampleOrgName = sampleOrg.getName();
                final String sampleOrgDomainName = sampleOrg.getDomainName();

                // src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/

                SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                        Utils.findFileSk(Paths.get(config.getTestChannelPath(), "crypto-config/peerOrganizations/",
                                sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                        Paths.get(config.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                                format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());

                sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode

            }
        } catch (Exception e) {
            warning(e.getMessage());
        }
        client.setUserContext(config.getIntegrationTestsSampleOrg("peerOrg1").getPeerAdmin());
    }

    private Channel initChannel(Channel channel)throws Exception{
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
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

        channel.addOrderer(anOrderer);

        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);

            Properties peerProperties = config.getPeerProperties(peerName); //test properties for peer.. if any.
            if (peerProperties == null) {
                peerProperties = new Properties();
            }
            //Example of setting specific options on grpc's NettyChannelBuilder
            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
            channel.addPeer(peer);
            sampleOrg.addPeer(peer);
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {

            final Properties eventHubProperties = config.getEventHubProperties(eventHubName);

            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});

            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    eventHubProperties);
            channel.addEventHub(eventHub);
        }

        channel.initialize();

        return channel;
    }

    private Channel getChannel(String name) throws Exception{
        Channel channel = client.newChannel(name);
//        Channel channel = client.getChannel(name);
        return initChannel(channel);
    }

    @FXML
    private void installChaincode()throws Exception{
        channel = getChannel("foo");
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
        installChaincode(channel, true, sampleOrg);
    }

    private void installChaincode(Channel channel, boolean installChaincode, SampleOrg sampleOrg) throws Exception{
        final String channelName = channel.getName();
        channel.setTransactionWaitTime(config.getTransactionWaitTime());
        channel.setDeployWaitTime(config.getDeployWaitTime());

        Collection<Peer> channelPeers = channel.getPeers();
        Collection<Orderer> orderers = channel.getOrderers();
        final ChaincodeID chaincodeID;
        Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(CHAIN_CODE_VERSION)
                .setPath(CHAIN_CODE_PATH).build();

        client.setUserContext(sampleOrg.getPeerAdmin());

        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);

        // on foo chain install from directory.

        ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
        installProposalRequest.setChaincodeSourceLocation(new File( "sample"));
        installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

        out("Sending install proposal");

        ////////////////////////////
        // only a client from the same org as the peer can issue an install request
        int numInstallProposal = 0;
        //    Set<String> orgs = orgPeers.keySet();
        //   for (SampleOrg org : testSampleOrgs) {

        Set<Peer> peersFromOrg = sampleOrg.getPeers();
        numInstallProposal = numInstallProposal + peersFromOrg.size();
        responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

        for (ProposalResponse response : responses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                successful.add(response);
            } else {
                failed.add(response);
            }
        }

        SDKUtils.getProposalConsistencySets(responses);
        //   }
        out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            System.out.println("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
        }

        successful.clear();
        failed.clear();

        successful = instantiate(channel, chaincodeID);

        CompletableFuture<BlockEvent.TransactionEvent> future = channel.sendTransaction(successful, orderers);
        future.thenApply(transactionEvent -> {
            if (!transactionEvent.isValid()){
                System.out.println("error");
                return null;
            }
            try {
                out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
                testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

                ////////////////////////////
                // Send Query Proposal to all peers
                //
                String expect = "" + 300;
                out("Now query chaincode for the value of b.");
                QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
                queryByChaincodeRequest.setArgs(new String[] {"query", "b"});
                queryByChaincodeRequest.setFcn("invoke");
                queryByChaincodeRequest.setChaincodeID(chaincodeID);

                Map<String, byte[]> tm2 = new HashMap<>();
                tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
                tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
                queryByChaincodeRequest.setTransientMap(tm2);

                Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
                for (ProposalResponse proposalResponse : queryProposals) {
                    if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                        System.out.println("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                                ". Messages: " + proposalResponse.getMessage()
                                + ". Was verified : " + proposalResponse.isVerified());
                    } else {
                        String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                        out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                        //assertEquals(payload, expect);
                    }
                }

                return null;
            } catch (Exception e) {
                out("Caught exception while running query");
                e.printStackTrace();
                System.out.println("Failed during chaincode query with error : " + e.getMessage());
            }

            return null;

            });

    }

    private Collection<ProposalResponse> instantiate(Channel channel, ChaincodeID chaincodeID)throws Exception{
        ///////////////
        //// Instantiate chaincode.
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(new String[] {"a", "500", "b", "" + 200});
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File( "sample/chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;

        out("Sending instantiateProposalRequest to all peers with arguments: a and b set to 100 and %s respectively", "" + 200);

        responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());
        for (ProposalResponse response : responses) {
            if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
                out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
            } else {
                failed.add(response);
            }
        }
        out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            out("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
        }
        return successful;
    }

    @FXML
    private void query()throws Exception{
        channel = getChannel("foo");
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(CHAIN_CODE_VERSION)
                .setPath(CHAIN_CODE_PATH).build();
        System.out.println(chaincodeID);
        query(channel, sampleOrg, chaincodeID);
    }

    private void query(Channel channel, SampleOrg sampleOrg, ChaincodeID chaincodeID)throws  Exception{

        ///////////////
        /// Send instantiate transaction to orderer
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;
        Collection<Orderer> orderers = channel.getOrderers();

        out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" + 200);

        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(new String[] {"query", "b"});
        queryByChaincodeRequest.setFcn("invoke");
        queryByChaincodeRequest.setChaincodeID(chaincodeID);

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
        queryByChaincodeRequest.setTransientMap(tm2);

        Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
        for (ProposalResponse proposalResponse : queryProposals) {
            if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                System.out.println("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                        ". Messages: " + proposalResponse.getMessage()
                        + ". Was verified : " + proposalResponse.isVerified());
            } else {
                String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                //assertEquals(payload, expect);
            }
        }
    }


    public static void warning(String contentText){
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(contentText);
        alert.showAndWait();
    }
}
