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

        try {
            configHelper.clearConfig();
            configHelper.customizeConfig();
        } catch (NoSuchFieldException|IllegalAccessException e) {
            warning(e.getMessage());
        }

        sampleOrgs = config.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

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
        File storeFile = new File("store/userStore.properties");
        if (storeFile.exists()){
            storeFile.delete();
        }
        sampleStore = new SampleStore(storeFile);

        try {
            //初始化用户证书等
            for (SampleOrg sampleOrg : sampleOrgs) {

                final String orgName = sampleOrg.getName();
                final String mspid = sampleOrg.getMSPID();
                final String domainName = sampleOrg.getDomainName();

                //位置channel\crypto\crypto-config\peerOrganizations\org1.example.com\ users\Admin@org1.example.com
                File peerAdminPrivateKeyFile = Paths.get(config.getTestChannelPath(), "crypto/crypto-config/peerOrganizations/",
                        domainName, format("/users/Admin@%s/msp/keystore", domainName)).toFile();
                //位置G:\IDEA-workspace\fabric-sample-java-sdk\channel\crypto\crypto-config\peerOrganizations\org1.example.com\ users\Admin@org1.example.com\msp\signcerts
                File peerAdminCertificateFile = Paths.get(config.getTestChannelPath(), "crypto/crypto-config/peerOrganizations/", domainName,
                        format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", domainName, domainName)).toFile();
                SampleUser peerAdmin = sampleStore.getMember(orgName + ADMIN_NAME, orgName, mspid,
                        Utils.findFileSk(peerAdminPrivateKeyFile), peerAdminCertificateFile);
                sampleOrg.setPeerAdmin(peerAdmin); // The admin of this org --

                //位置channel\crypto\crypto-config\peerOrganizations\org1.example.com\ users\Admin@org1.example.com
                File userPrivateKeyFile = Paths.get(config.getTestChannelPath(), "crypto/crypto-config/peerOrganizations/",
                        domainName, format("/users/User1@%s/msp/keystore", domainName)).toFile();
                //位置G:\IDEA-workspace\fabric-sample-java-sdk\channel\crypto\crypto-config\peerOrganizations\org1.example.com\ users\Admin@org1.example.com\msp\signcerts
                File userCertificateFile = Paths.get(config.getTestChannelPath(), "crypto/crypto-config/peerOrganizations/", domainName,
                        format("/users/User1@%s/msp/signcerts/User1@%s-cert.pem", domainName, domainName)).toFile();
                SampleUser user1 = sampleStore.getMember(orgName + USER_1_NAME, orgName, mspid,
                        Utils.findFileSk(userPrivateKeyFile), userCertificateFile);
                sampleOrg.addUser(user1); // The admin of this org --
            }
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            warning(e.getMessage());
        }
    }

//    @FXML
//    private void newChannel(){
//        JoinPeer.newChannel(config, "mychannel", client, sampleOrg2);
//    }

    @FXML
    private void joinPeer(){
        Iterator<SampleOrg> iterator = sampleOrgs.iterator();
        SampleOrg sampleOrg1 = iterator.next();
        SampleOrg sampleOrg2 = iterator.next();
        try {
            JoinPeer.newChannel(config, "mychannel", client, sampleOrg1);
            JoinPeer.constructChannel(config, "mychannel", client, sampleOrg1);
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
