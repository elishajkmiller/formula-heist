import com.android.apksig.ApkSigner;
import java.io.FileInputStream;
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class Sign {
    public static void main(String[] args) throws Exception {
        // args: keystore alias storepass keypass in.apk out.apk
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(args[0])) {
            ks.load(fis, args[2].toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[1], args[3].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(args[1]);

        ApkSigner.SignerConfig signerConfig =
                new ApkSigner.SignerConfig.Builder("debug", key,
                        Collections.<X509Certificate>singletonList(cert))
                        .build();

        ApkSigner signer =
                new ApkSigner.Builder(Collections.singletonList(signerConfig))
                        .setInputApk(new File(args[4]))
                        .setOutputApk(new File(args[5]))
                        .setMinSdkVersion(26)
                        .setV1SigningEnabled(false)
                        .setV2SigningEnabled(true)
                        .setV3SigningEnabled(true)
                        .build();
        signer.sign();
    }
}
