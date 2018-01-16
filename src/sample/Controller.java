package sample;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
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
import java.util.Collection;
import java.util.Iterator;

import static java.lang.String.format;

public class Controller {
    @FXML
    private ComboBox<String> peerComboBox;

    private final Config config = Config.getConfig();
    private final ConfigHelper configHelper = new ConfigHelper();
    private Collection<SampleOrg> sampleOrgs;
    private SampleStore sampleStore;
    private HFClient client;

    private static final String ADMIN_NAME = "admin";
    private static final String USER_1_NAME = "user1";

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
    private void joinPeer(){
        connect();

        Iterator<SampleOrg> iterator = sampleOrgs.iterator();
        SampleOrg sampleOrg1 = iterator.next();
        SampleOrg sampleOrg2 = iterator.next();
        try {
            JoinPeer.constructChannel(config, "foo", client, sampleOrg1);
        } catch (Exception e) {
            warning(e.getMessage());
        }
    }

    @FXML
    private void connect(){
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
    }

    public static void warning(String contentText){
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(contentText);
        alert.showAndWait();
    }
}
