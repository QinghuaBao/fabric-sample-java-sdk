package sample;

import org.hyperledger.fabric.sdk.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static sample.Controller.USER_1_NAME;
import static sample.JoinPeer.out;

/**
 * Created by bqh on 2018/1/31.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class Contract {
    public static boolean installChaincode(Config config, HFClient client, Channel channel, ChaincodeID chaincodeID,
                                               SampleOrg sampleOrg) throws Exception{
        channel.setTransactionWaitTime(config.getTransactionWaitTime());
        channel.setDeployWaitTime(config.getDeployWaitTime());

        Collection<Peer> channelPeers = channel.getPeers();
        Collection<Orderer> orderers = channel.getOrderers();
        Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        client.setUserContext(sampleOrg.getPeerAdmin());

        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);

        installProposalRequest.setChaincodeSourceLocation(new File( "G:\\Go-workspace"));
        installProposalRequest.setChaincodeVersion(chaincodeID.getVersion());

        out("Sending install proposal");

        int numInstallProposal = 0;

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
            return false;
        }

        return true;
    }

    public static boolean instantiate(Config config, HFClient client, Channel channel, ChaincodeID chaincodeID,
                                                     String function, String[] args)throws Exception{
        ///////////////
        //// Instantiate chaincode.
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn(function);
        instantiateProposalRequest.setArgs(args);
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);

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
            return false;
        }

        Collection<Orderer> orderers = channel.getOrderers();
        BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful, orderers).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        if (!transactionEvent.isValid()){
            System.out.println("error");
            return false;
        }

        return true;
    }

    public static boolean invoke(Config config, HFClient client, Channel channel, ChaincodeID chaincodeID,
                                 String[] args)throws Exception{
        client.setUserContext(config.getIntegrationTestsSampleOrg("peerOrg1").getUser(USER_1_NAME));

        Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        ///////////////
        /// Send transaction proposal to all peers
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setFcn(args[0]);
        transactionProposalRequest.setProposalWaitTime(config.getProposalWaitTime());
        transactionProposalRequest.setArgs(Arrays.copyOfRange(args, 1, args.length));

        Map<String, byte[]> tm2 = new HashMap<>();
        tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
        tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
        tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
        transactionProposalRequest.setTransientMap(tm2);

        out("sending transactionProposal to all peers with arguments: move(a,b,100)");

        Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
        for (ProposalResponse response : transactionPropResp) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                successful.add(response);
            } else {
                failed.add(response);
            }
        }

        // Check that all the proposals are consistent with each other. We should have only one set
        // where all the proposals above are consistent.
        Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
        if (proposalConsistencySets.size() != 1) {
            System.out.println(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
        }

        out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                transactionPropResp.size(), successful.size(), failed.size());
        if (failed.size() > 0) {
            ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
            out("Not enough endorsers for invoke(move a,b,100):" + failed.size() + " endorser error: " +
                    firstTransactionProposalResponse.getMessage() +
                    ". Was verified: " + firstTransactionProposalResponse.isVerified());
        }
        out("Successfully received transaction proposal responses.");

        ProposalResponse resp = transactionPropResp.iterator().next();
        byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
        String resultAsString = null;
        if (x != null) {
            resultAsString = new String(x, "UTF-8");
            System.out.println(resultAsString);
        }

        ////////////////////////////
        // Send Transaction Transaction to orderer
        out("Sending chaincode transaction(move a,b,100) to orderer.");
        BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        if (!transactionEvent.isValid()){
            return false;
        }
        return true;
    }

    public static boolean upgrade(Config config, HFClient client, Channel channel, ChaincodeID chaincodeID,
                                      String function, String[] args)throws Exception{
        UpgradeProposalRequest request = client.newUpgradeProposalRequest();
        request.setProposalWaitTime(config.getProposalWaitTime());
        request.setChaincodeID(chaincodeID);
        request.setFcn(function);
        request.setArgs(args);

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        request.setTransientMap(tm);

        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File( "sample/chaincodeendorsementpolicy.yaml"));
        request.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;

        out("Sending UpgradeProposalRequest to all peers with arguments: a and b set to 100 and %s respectively", "" + 200);

        responses = channel.sendUpgradeProposal(request, channel.getPeers());
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
            return false;
        }

        Collection<Orderer> orderers = channel.getOrderers();
        BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful, orderers).get(config.getTransactionWaitTime(), TimeUnit.SECONDS);
        if (!transactionEvent.isValid()){
            System.out.println("error");
            return false;
        }

        return true;
    }

    public static String query(HFClient client, Channel channel, ChaincodeID chaincodeID, String[] args)throws  Exception{

        ///////////////
        /// Send instantiate transaction to orderer

        out("Sending instantiateTransaction to orderer with a and b set to 100 and %s respectively", "" + 200);

        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(Arrays.copyOfRange(args, 1, args.length));
        queryByChaincodeRequest.setFcn(args[0]);
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
}
