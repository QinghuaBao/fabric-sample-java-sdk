package sample;

import address.Sha256Hash;
import ecdsa.ECKey;
import org.spongycastle.util.encoders.Base64;
import protos.FoamPocket;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Map;

/**
 * Created by bqh on 2018/2/27.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class Sign {
    public static void main(String[] args) {
        FoamPocket.Output.Builder output = FoamPocket.Output.newBuilder();
        output.setOutputAddr("1");
        output.setOutputValue(2);

        FoamPocket.TXMap.TX.Builder txMap = FoamPocket.TXMap.TX.newBuilder();
        txMap.setTimestamp(1);
        txMap.setInputAddr("2");
        txMap.setInputBalance(3);
        txMap.setNounce(4);
        txMap.addOutput(output);
        txMap.setScript("test");
        txMap.setFee(7);

        FoamPocket.TXMap.Builder tx = FoamPocket.TXMap.newBuilder();
        tx.setTimestamp(6);
        tx.setFounder("7");
        tx.putTxMap("1", txMap.build());

        byte[] x =tx.build().toByteArray();

        //FoamPocket.TXMap.Builder temp = FoamPocket.TXMap.newBuilder(x);
        String pri = "OTI1MTg0NTU2NTEzNjE1OTY0NTkzMTYxMTkyNzc4NzA1NTYxMzA0NTY5NzIwNjY2MTkwNzg1NjU1MDQxNjQ5MzMxNjI2ODU0OTY5NzY=";
        byte[] a = Base64.decode(pri);
        BigInteger aa = new BigInteger("93141754634566361998718788907872857161037061174911484780150659485615148898066");
        ECKey key = new ECKey(aa);
        try {
            byte[] temp = signScript(key, "".getBytes());
            System.out.println("xxx");
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        System.out.println("xxx");
    }

    public static byte[] signScript(ECKey key, byte[] x)
            throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
//        byte[] x = txHash(tx);
        String temp = new String(Base64.encode(x));
//        System.out.println(txHashBytes.toString());
        //txHashBytes = java.util.Base64.getDecoder().decode("xSXBmQVjIQ6FUkcR4C1eTR+G5WzFojeN4+JKKvMHo24=");
        ECKey.Signature signature = key.sign(x);
        //String xString = java.util.Base64.getEncoder().encodeToString(signature.Serialize());
        return signature.Serialize();
    }

    public static byte[] newsignScript(ECKey key, FoamPocket.TXMap.TX tx)
            throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
        byte[] x = txHash(tx);
        String temp = new String(Base64.encode(x));
//        System.out.println(txHashBytes.toString());
        //txHashBytes = java.util.Base64.getDecoder().decode("xSXBmQVjIQ6FUkcR4C1eTR+G5WzFojeN4+JKKvMHo24=");
        ECKey.Signature signature = key.sign(x);
        //String xString = java.util.Base64.getEncoder().encodeToString(signature.Serialize());
        return signature.Serialize();
    }

    public static String sign(ECKey key, FoamPocket.TXMap txMap)
            throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
        FoamPocket.TXMap.Builder tempTxMap = FoamPocket.TXMap.newBuilder();
        tempTxMap.setTimestamp(txMap.getTimestamp());
        tempTxMap.setFounder(txMap.getFounder());

        for (Map.Entry<String, FoamPocket.TXMap.TX> entry : txMap.getTxMapMap().entrySet()){
            //取出tx签名
            FoamPocket.TXMap.TX.Builder tx = FoamPocket.TXMap.TX.newBuilder(entry.getValue());
            byte[] script = newsignScript(key, tx.build());
            tx.setScript(Base64.toBase64String(script));
            tempTxMap.putTxMap(entry.getKey(), tx.build());
        }
        return Base64.toBase64String(tempTxMap.build().toByteArray());
    }


    private static byte[] txHash(FoamPocket.TXMap.TX tx) {
        byte[] txBytes = tx.toByteArray();
        byte[] fhash = Sha256Hash.hash(txBytes);
        byte[] lhash = Sha256Hash.hash(fhash);
        return lhash;
    }
}
