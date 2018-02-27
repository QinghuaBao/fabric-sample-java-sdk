package sample;

import com.google.protobuf.InvalidProtocolBufferException;
import org.spongycastle.util.encoders.Base64;
import protos.FoamPocket;

/**
 * Created by bqh on 2018/2/27.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class DeserializeProto {
    public static String[] deserilizePointKind(String protoByteBase64) throws InvalidProtocolBufferException{
        byte[] protoByte = Base64.decode(protoByteBase64);
        FoamPocket.PointKind pointKind = FoamPocket.PointKind.parseFrom(protoByte);
        String[] res = new String[pointKind.getKindCount()];
        for (int i = 0; i < pointKind.getKindCount(); i++) {
            res[i] = pointKind.getKind(i);
        }
        return res;
    }

    public static FoamPocket.QueryResult deserilizeQueryResult(String protoByteBase64) throws InvalidProtocolBufferException{
        byte[] protoByte = Base64.decode(protoByteBase64);
        FoamPocket.QueryResult queryResult = FoamPocket.QueryResult.parseFrom(protoByte);

        return queryResult;
    }
}
