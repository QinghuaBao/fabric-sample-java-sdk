package sample;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import jdk.nashorn.internal.scripts.JO;
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
    public static final String USER_1_NAME = "user1";

    private static final String CHAIN_CODE_NAME = "pocket";
    private static final String CHAIN_CODE_PATH = "github.com/hyperledger-coin";
    private static int CHAIN_CODE_VERSION = 1;

    String testTxID = null;  // save the CC invoke TxID and use in queries

    public Controller() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException {

    }

    @FXML
    private void initialize(){
        peerComboBox.setItems(FXCollections.observableArrayList("peer0", "peer1", "peer2", "peer3"));
    }


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
        configHelper.clearConfig();
        configHelper.customizeConfig();

        sampleOrgs = config.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : sampleOrgs) {
            sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
        }

        //Create instance of client.
        client = HFClient.createNewInstance();
        //设置加密套件
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

        //不需要ca
//        for (SampleOrg sampleOrg : sampleOrgs) {
//            sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
//        }

        //初始化用户存储文件
        File storeFile = new File("userStore.properties");
        if (!storeFile.exists()){
            storeFile.createNewFile();
        }

        sampleStore = new SampleStore(storeFile);

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
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(Integer.toString(CHAIN_CODE_VERSION))
                .setPath(CHAIN_CODE_PATH).build();
        CHAIN_CODE_VERSION++;

        if (Contract.installChaincode(config, client, channel, chaincodeID, sampleOrg)){
            if (Contract.instantiate(config, client, channel, chaincodeID, "init", new String[]{""}))
                warning("success");
            else warning("failed");
        }else
            warning("failed");
    }

    @FXML
    private void query()throws Exception{
        channel = getChannel("foo");
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(Integer.toString(CHAIN_CODE_VERSION))
                .setPath(CHAIN_CODE_PATH).build();
        System.out.println(chaincodeID);
        String res = query(client, channel, chaincodeID);
        warning(res);
    }

    @FXML
    private void invoke()throws Exception{
        channel = getChannel("foo");
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(Integer.toString(CHAIN_CODE_VERSION))
                .setPath(CHAIN_CODE_PATH).build();
        System.out.println(chaincodeID);
        String[] args = {"invoke", "move", "a", "b", "100"};
        if (Contract.invoke(config, client, channel, chaincodeID, args)){
           warning("success!");
        }else warning("failed");

    }

    @FXML
    private void upgradeChaincode()throws Exception{
        channel = getChannel("foo");
        SampleOrg sampleOrg = config.getIntegrationTestsSampleOrg("peerOrg1");
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                .setVersion(Integer.toString(CHAIN_CODE_VERSION))
                .setPath(CHAIN_CODE_PATH).build();
        CHAIN_CODE_VERSION++;
        System.out.println(chaincodeID);

        if (Contract.installChaincode(config, client, channel, chaincodeID, sampleOrg)){
            if (Contract.upgrade(config, client, channel, chaincodeID, "init", new String[]{""}))
                warning("success");
            else warning("failed");
        }else
            warning("failed");
    }



    private String query(HFClient client, Channel channel, ChaincodeID chaincodeID)throws  Exception{

        ///////////////
        /// Send instantiate transaction to orderer

        out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" + 200);

        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(new String[] {"query", "b"});
        queryByChaincodeRequest.setFcn("invoke");
        queryByChaincodeRequest.setChaincodeID(chaincodeID);

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
        queryByChaincodeRequest.setTransientMap(tm2);

        StringBuilder res = new StringBuilder();
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
                res.append(payload + ":");
            }
        }
        return res.toString();
    }




    public static void warning(String contentText){
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(contentText);
        alert.showAndWait();
    }
}
