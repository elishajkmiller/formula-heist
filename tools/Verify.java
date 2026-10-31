import com.android.apksig.ApkVerifier;
import com.android.apksig.ApkVerifier.Result;
import java.io.File;

public class Verify {
    public static void main(String[] args) throws Exception {
        Result result = new ApkVerifier.Builder(new File(args[0])).build().verify();
        if (result.isVerified()) {
            System.out.println("signature ok: v2=" + result.isVerifiedUsingV2Scheme()
                    + " v3=" + result.isVerifiedUsingV3Scheme());
        } else {
            System.out.println("signature INVALID");
            System.exit(1);
        }
    }
}
